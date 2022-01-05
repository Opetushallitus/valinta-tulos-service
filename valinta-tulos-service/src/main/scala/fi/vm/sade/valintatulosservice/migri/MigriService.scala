package fi.vm.sade.valintatulosservice.migri

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.oppijanumerorekisteri.{Henkilo, OppijanumerorekisteriService}
import fi.vm.sade.valintatulosservice.tarjonta.{HakuService, HakukohdeMigri}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValinnantulosRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakijaOid, HakukohdeOid, ValinnantulosWithTilahistoria}
import fi.vm.sade.valintatulosservice.{AuditInfo, ValinnantulosService}

import scala.collection.immutable.Set
import scala.collection.mutable

class MigriService(hakuService: HakuService, valinnantulosService: ValinnantulosService, oppijanumerorekisteriService: OppijanumerorekisteriService, valintarekisteriService: ValinnantulosRepository) extends Logging {

  private def fetchHenkilotFromONR(hakijaOids: Set[HakijaOid]): Set[Henkilo] = {
    oppijanumerorekisteriService.henkilot(hakijaOids) match {
      case Right(h) => h.values.toSet
      case Left(_) => throw new IllegalArgumentException(s"No hakijas found for oid: $hakijaOids found.")
    }
  }

  def fetchHakemuksetByHakijaOid(hakijaOids: Set[HakijaOid], auditInfo: AuditInfo): Set[Hakija] = {
    val foreignHakijat: Set[Hakija] = fetchHenkilotFromONR(hakijaOids).map(henkilo =>
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

    logger.info("FILTERED HAKIJAT: " + foreignHakijat.toString)

    foreignHakijat.foreach(hakija => {
      val hakemusOids = valintarekisteriService.getHakijanHyvaksytHakemusOidit(HakijaOid(hakija.henkiloOid))

      logger.info("FOUND HAKEMUSOIDS: " + hakemusOids.toString())

      valinnantulosService.getValinnantuloksetForHakemukset(hakemusOids, auditInfo) match {
        case tulokset: Set[ValinnantulosWithTilahistoria] =>
          logger.info("VALINNANTULOKSET: " + tulokset)
          tulokset.map { tulos =>
            getHakukohdeMigri(tulos.valinnantulos.hakukohdeOid) match {
              case Some(hakukohde: HakukohdeMigri) =>
                val hakemus = Hakemus(
                  hakuOid = hakukohde.hakuOid.toString,
                  hakuNimi = hakukohde.hakuNimi,
                  hakemusOid = tulos.valinnantulos.hakemusOid.toString,
                  organisaatioOid = hakukohde.organisaatioOid,
                  organisaatioNimi = hakukohde.organisaatioNimi,
                  hakukohdeOid = tulos.valinnantulos.hakukohdeOid.toString,
                  hakukohdeNimi = hakukohde.hakukohteenNimi,
                  toteutusOid = hakukohde.toteutusOid,
                  toteutusNimi = hakukohde.toteutusNimi,
                  valintaTila = tulos.valinnantulos.valinnantila.toString,
                  vastaanottoTila = tulos.valinnantulos.vastaanottotila.toString,
                  ilmoittautuminenTila = tulos.valinnantulos.ilmoittautumistila.toString,
                  maksuvelvollisuus = "",
                  lukuvuosimaksu = "",
                  koulutuksenAlkamiskausi = hakukohde.koulutuksenAlkamiskausi.getOrElse("").toString,
                  koulutuksenAlkamisvuosi = hakukohde.koulutuksenAlkamisvuosi.getOrElse("").toString
                )
                hakija.hakemukset += hakemus
              case _ =>
            }
          }
      }
    })
    foreignHakijat.filter(h => h.hakemukset.nonEmpty)
  }

  private def getHakukohdeMigri(hakukohdeOid: HakukohdeOid): Option[HakukohdeMigri] = {
    try {
      Some(hakuService.getHakukohdeMigri(hakukohdeOid).right.get)
    } catch {
      case e: Throwable =>
        logger.warn(e.toString)
        None
    }
  }
}
