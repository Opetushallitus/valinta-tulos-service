package fi.vm.sade.valintatulosservice.migri

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.oppijanumerorekisteri.OppijanumerorekisteriService
import fi.vm.sade.valintatulosservice.tarjonta.{HakuService, HakukohdeMigri}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValinnantulosRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakijaOid, HakukohdeOid, HyvaksyttyValinnanTila, ValinnantulosWithTilahistoria}
import fi.vm.sade.valintatulosservice.{AuditInfo, LukuvuosimaksuService, ValinnantulosService}

import scala.collection.immutable.Set

class MigriService(hakemusRepository: HakemusRepository, hakuService: HakuService, valinnantulosService: ValinnantulosService, oppijanumerorekisteriService: OppijanumerorekisteriService, valintarekisteriService: ValinnantulosRepository, lukuvuosimaksuService: LukuvuosimaksuService) extends Logging {

  private def getForeignHakijat(hakijaOids: Set[HakijaOid]): Set[MigriHakija] = {
    oppijanumerorekisteriService.henkilot(hakijaOids).fold(e => {
      val errorString: String = s"No migri hakijas found for oid(s): $hakijaOids found. Cause: $e"
      logger.warn(errorString)
      throw new RuntimeException(errorString)
    }, henkilot => {
      henkilot.map(henkilo => {
        val hetu = henkilo._2.hetu match {
          case Some(hetu) => Some(hetu.toString)
          case None => None
        }
        MigriHakija(
          henkilotunnus = hetu,
          henkiloOid = henkilo._2.oid.toString,
          sukunimi = henkilo._2.sukunimi,
          etunimet = henkilo._2.etunimet,
          kansalaisuudet = henkilo._2.kansalaisuudet,
          syntymaaika = henkilo._2.syntymaaika,
          Set()
        )
      }
      ).filterNot(hakija => hakija.kansalaisuudet match {
        case Some(kansalaisuudet) => kansalaisuudet.contains("246")
      }
      ).toSet})
  }

  private def tuloksetToMigriHakemukset(tulokset: Set[ValinnantulosWithTilahistoria], auditInfo: AuditInfo): Set[MigriHakemus] = {
    var hakemukset: Set[MigriHakemus] = Set()
    tulokset match {
      case tulokset: Set[ValinnantulosWithTilahistoria] =>
        tulokset
          .foreach { tulos =>
            getHakukohdeMigri(tulos.valinnantulos.hakukohdeOid) match {
              case Some(hakukohde: HakukohdeMigri) =>
                hakemusRepository.findHakemus(tulos.valinnantulos.hakemusOid).fold(e => {
                  val errorString: String = s"No hakemus found for migri hakijaOid: ${tulos.valinnantulos.hakemusOid}, cause: ${e.toString}"
                  logger.error(errorString)
                  throw new RuntimeException(errorString)
                }, h => {
                  val lukuvuosimaksu: Option[String] = lukuvuosimaksuService.getLukuvuosimaksuByHakijaAndHakukohde(HakijaOid(h.henkiloOid), tulos.valinnantulos.hakukohdeOid, auditInfo) match {
                    case Some(maksu) => Some(maksu.maksuntila.toString)
                    case None => None
                  }
                  val maksuvelvollisuus: Option[String] = if (h.maksuvelvollisuudet.exists(m => m._1 == tulos.valinnantulos.hakukohdeOid.toString)) Some(h.maksuvelvollisuudet.filter(m => m._1 == tulos.valinnantulos.hakukohdeOid.toString).head._2) else None
                  hakemukset += MigriHakemus(
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
                    koulutuksenAlkamiskausi = hakukohde.koulutuksenAlkamiskausi,
                    koulutuksenAlkamisvuosi = hakukohde.koulutuksenAlkamisvuosi)
                })
            }
          }
    }
    hakemukset
  }

  def getHakemuksetByHakijaOids(hakijaOids: Set[HakijaOid], auditInfo: AuditInfo): Set[MigriHakija] = {
    val foreignHakijat: Set[MigriHakija] = getForeignHakijat(hakijaOids)
    foreignHakijat.map(hakija => {
      val hyvaksytyt: Set[HyvaksyttyValinnanTila] = valintarekisteriService.getHakijanHyvaksytValinnantilat(HakijaOid(hakija.henkiloOid))
      val hyvaksytytHakemusOidit = hyvaksytyt.map(h => h.hakemusOid)
      val hyvaksytytHakukohdeOidit = hyvaksytyt.map(h => h.hakukohdeOid)
      val tulokset = valinnantulosService.getValinnantuloksetForHakemukset(hyvaksytytHakemusOidit, auditInfo)
        .filter(tulos => hyvaksytytHakukohdeOidit.contains(tulos.valinnantulos.hakukohdeOid))
        .filter(tulos => tulos.valinnantulos.isHyvaksytty)
      val migriHakemukset = tuloksetToMigriHakemukset(tulokset, auditInfo)
      hakija.copy(hakemukset = migriHakemukset)
    }).filter(h => h.hakemukset.nonEmpty)
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
