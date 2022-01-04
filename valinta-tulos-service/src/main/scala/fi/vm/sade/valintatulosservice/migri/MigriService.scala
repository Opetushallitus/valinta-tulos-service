package fi.vm.sade.valintatulosservice.migri

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.oppijanumerorekisteri.{Henkilo, OppijanumerorekisteriService}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValinnantulosRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakijaOid, ValinnantulosWithTilahistoria}
import fi.vm.sade.valintatulosservice.{AuditInfo, ValinnantulosService}

import scala.collection.immutable.Set
import scala.collection.mutable

class MigriService(valinnantulosService: ValinnantulosService, oppijanumerorekisteriService: OppijanumerorekisteriService, valintarekisteriService: ValinnantulosRepository) extends Logging {

  private def fetchHenkilotFromONR(hakijaOids: Set[HakijaOid]): Set[Henkilo] = {
    oppijanumerorekisteriService.henkilot(hakijaOids) match {
      case Right(h) => h.values.toSet
      case Left(_) => throw new IllegalArgumentException(s"No hakijas found for oid: $hakijaOids found.")
    }
  }

  def fetchHakemuksetByHakijaOid(hakijaOids: Set[HakijaOid], auditInfo: AuditInfo): Set[Hakija] = {
    val hakijat: Set[Hakija] = fetchHenkilotFromONR(hakijaOids).map(henkilo =>
      Hakija(
        henkilotunnus = henkilo.hetu.getOrElse("").toString,
        henkiloOid = henkilo.oid.toString,
        sukunimi = henkilo.sukunimi.getOrElse(""),
        etunimet = henkilo.etunimet.getOrElse(""),
        kansalaisuudet = henkilo.kansalaisuudet.getOrElse(List()),
        syntymaaika = henkilo.syntymaaika.getOrElse("")
        , mutable.Set()
      )
    ).filterNot(hakija => hakija.kansalaisuudet.contains("246"))

    logger.info("FILTERED HAKIJAT: " + hakijat.toString)
    hakijat.foreach(hakija => {
      val hakemusOids = valintarekisteriService.getHakijanHyvaksytHakemusOidit(HakijaOid(hakija.henkiloOid))
      logger.info("FOUND HAKEMUSOIDS: " + hakemusOids.toString())
      valinnantulosService.getValinnantuloksetForHakemukset(hakemusOids, auditInfo) match {
        case tulokset: Set[ValinnantulosWithTilahistoria] =>
          logger.info("VALINNANTULOKSET: " + tulokset)
          val hakemukset: Set[Hakemus] = tulokset.map(tulos =>
            Hakemus(
              hakuOid = "",
              hakuNimi = Map(),
              hakemusOid = tulos.valinnantulos.hakemusOid.toString,
              organisaatioOid = "",
              organisaatioNimi = Map(),
              hakukohdeOid = tulos.valinnantulos.hakukohdeOid.toString,
              hakukohdeNimi = Map(),
              toteutusOid = "",
              toteutusNimi = Map(),
              valintaTila = tulos.valinnantulos.valinnantila.toString,
              vastaanottoTila = tulos.valinnantulos.vastaanottotila.toString,
              ilmoittautuminenTila = tulos.valinnantulos.ilmoittautumistila.toString,
              maksuvelvollisuus = "",
              lukuvuosimaksu = "",
              koulutuksenAlkamiskausi = "",
              koulutuksenAlkamisvuosi = ""
            )
          )
          hakija.hakemukset ++= hakemukset
      }
    }
    )



    //TODO: 3. hae hakemuksen tiedot ja tulosten tiedot
    //Suomi: 246 -> kaksoiskansalaisuudet karsittu ATM
    //          [
    //            "1.2.246.562.24.81233532746",
    //            "1.2.246.562.24.21082937581",
    //            "1.2.246.562.24.38232063764",
    //            "1.2.246.562.24.69379769754"
    //          ]
    hakijat
  }
}
