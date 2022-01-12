package fi.vm.sade.valintatulosservice.migri

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.oppijanumerorekisteri.OppijanumerorekisteriService
import fi.vm.sade.valintatulosservice.tarjonta.{HakuService, HakukohdeMigri}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValinnantulosRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakijaOid, HakukohdeOid, HyvaksyttyValinnanTila, ValinnantulosWithTilahistoria}
import fi.vm.sade.valintatulosservice.{AuditInfo, LukuvuosimaksuService, ValinnantulosService}

import scala.collection.immutable.Set
import scala.collection.mutable

class MigriService(hakemusRepository: HakemusRepository, hakuService: HakuService, valinnantulosService: ValinnantulosService, oppijanumerorekisteriService: OppijanumerorekisteriService, valintarekisteriService: ValinnantulosRepository, lukuvuosimaksuService: LukuvuosimaksuService) extends Logging {

  private def getHakijat(hakijaOids: Set[HakijaOid]): Set[MigriHakija] = {
    oppijanumerorekisteriService.henkilot(hakijaOids).fold(_ => throw new IllegalArgumentException(s"No migri hakijas found for oid: $hakijaOids found."), henkilot => {
      henkilot.map(henkilo =>
        MigriHakija(
          henkilotunnus = henkilo._2.hetu.getOrElse("").toString,
          henkiloOid = henkilo._2.oid.toString,
          sukunimi = henkilo._2.sukunimi.getOrElse(""),
          etunimet = henkilo._2.etunimet.getOrElse(""),
          kansalaisuudet = henkilo._2.kansalaisuudet.getOrElse(List()),
          syntymaaika = henkilo._2.syntymaaika.getOrElse(""),
          mutable.Set()
        )
      ).filterNot(hakija => hakija.kansalaisuudet.contains("246")).toSet
    })
  }

  def getHakemuksetByHakijaOids(hakijaOids: Set[HakijaOid], auditInfo: AuditInfo): Set[MigriHakija] = {
    val foreignHakijat: Set[MigriHakija] = getHakijat(hakijaOids)
    foreignHakijat.foreach(hakija => {
      val hyvaksytyt: Set[HyvaksyttyValinnanTila] = valintarekisteriService.getHakijanHyvaksytValinnantilat(HakijaOid(hakija.henkiloOid))
      val hyvaksytytHakemusOidit = hyvaksytyt.map(h => h.hakemusOid)
      val hyvaksytytHakukohdeOidit = hyvaksytyt.map(h => h.hakukohdeOid)

      valinnantulosService.getValinnantuloksetForHakemukset(hyvaksytytHakemusOidit, auditInfo) match {
        case tulokset: Set[ValinnantulosWithTilahistoria] =>
          tulokset
            .filter(t => t.valinnantulos.isHyvaksytty)
            .map { tulos =>
              getHakukohdeMigri(tulos.valinnantulos.hakukohdeOid) match {
                case Some(hakukohde: HakukohdeMigri) =>
                  if (hyvaksytytHakukohdeOidit.contains(hakukohde.oid)) {
                    hakemusRepository.findHakemus(tulos.valinnantulos.hakemusOid).fold(e =>
                      logger.warn(s"No hakemus found for migri hakijaOid: ${tulos.valinnantulos.hakemusOid}, cause: ${e.toString}"), h => {
                      val maksuntila: String = lukuvuosimaksuService.getLukuvuosimaksuByHakijaAndHakukohde(HakijaOid(h.henkiloOid), tulos.valinnantulos.hakukohdeOid, auditInfo) match {
                        case Some(maksu) => maksu.maksuntila.toString
                        case None => ""
                      }

                      val maksuvelvollisuus: String = if (h.maksuvelvollisuudet.exists(m => m._1 == tulos.valinnantulos.hakukohdeOid.toString)) h.maksuvelvollisuudet.filter(m => m._1 == tulos.valinnantulos.hakukohdeOid.toString).head._2 else ""

                      hakija.hakemukset += MigriHakemus(
                        hakuOid = h.hakuOid.toString,
                        hakuNimi = hakukohde.hakuNimi,
                        hakemusOid = h.oid.toString,
                        organisaatioOid = hakukohde.organisaatioOid,
                        organisaatioNimi = hakukohde.organisaatioNimi,
                        hakukohdeOid = tulos.valinnantulos.hakukohdeOid.toString,
                        hakukohdeNimi = hakukohde.hakukohteenNimi,
                        toteutusOid = hakukohde.toteutusOid,
                        toteutusNimi = hakukohde.toteutusNimi,
                        valintaTila = tulos.valinnantulos.valinnantila.toString,
                        vastaanottoTila = tulos.valinnantulos.vastaanottotila.toString,
                        ilmoittautuminenTila = tulos.valinnantulos.ilmoittautumistila.toString,
                        maksuvelvollisuus = maksuvelvollisuus,
                        lukuvuosimaksu = maksuntila,
                        koulutuksenAlkamiskausi = hakukohde.koulutuksenAlkamiskausi.getOrElse("").toString,
                        koulutuksenAlkamisvuosi = hakukohde.koulutuksenAlkamisvuosi.getOrElse("").toString
                      )
                    })
                  }
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
