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
          henkilotunnus = if (henkilo._2.hetu.nonEmpty) henkilo._2.hetu.toString else null,
          henkiloOid = henkilo._2.oid.toString,
          sukunimi = if (henkilo._2.sukunimi.nonEmpty) henkilo._2.sukunimi.toString else null,
          etunimet = if (henkilo._2.etunimet.nonEmpty) henkilo._2.etunimet.toString else null,
          kansalaisuudet = if (henkilo._2.kansalaisuudet.nonEmpty) henkilo._2.kansalaisuudet.get else null,
          syntymaaika = if (henkilo._2.syntymaaika.nonEmpty) henkilo._2.syntymaaika.toString else null,
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
                      val lukuvuosimaksu: String = lukuvuosimaksuService.getLukuvuosimaksuByHakijaAndHakukohde(HakijaOid(h.henkiloOid), tulos.valinnantulos.hakukohdeOid, auditInfo) match {
                        case Some(maksu) => maksu.maksuntila.toString
                        case None => null
                      }
                      val maksuvelvollisuus: String = if (h.maksuvelvollisuudet.exists(m => m._1 == tulos.valinnantulos.hakukohdeOid.toString)) h.maksuvelvollisuudet.filter(m => m._1 == tulos.valinnantulos.hakukohdeOid.toString).head._2 else null
                      val koulutuksenAlkamiskausi: String = hakukohde.koulutuksenAlkamiskausi match {
                        case Some(kausi) => kausi
                        case None => null
                      }
                      val koulutuksenAlkamisvuosi: Integer = hakukohde.koulutuksenAlkamisvuosi match {
                        case Some(vuosi) => vuosi
                        case None => null
                      }

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
                        valintaTila = tulos.valinnantulos.valinnantila.valinnantila.toString,
                        vastaanottoTila = tulos.valinnantulos.vastaanottotila.toString,
                        ilmoittautuminenTila = tulos.valinnantulos.ilmoittautumistila.ilmoittautumistila.toString,
                        maksuvelvollisuus = maksuvelvollisuus,
                        lukuvuosimaksu = lukuvuosimaksu,
                        koulutuksenAlkamiskausi = koulutuksenAlkamiskausi,
                        koulutuksenAlkamisvuosi = koulutuksenAlkamisvuosi
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
