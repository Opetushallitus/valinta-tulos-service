package fi.vm.sade.valintatulosservice.migri

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.migraatio.vastaanotot.HakijaResolver
import fi.vm.sade.valintatulosservice.oppijanumerorekisteri.{Henkilo, OppijanumerorekisteriService}
import fi.vm.sade.valintatulosservice.tarjonta.{HakuService, HakukohdeMigri}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValinnantulosRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakijaOid, HakukohdeOid, HyvaksyttyValinnanTila, ValinnantulosWithTilahistoria}
import fi.vm.sade.valintatulosservice.{AuditInfo, LukuvuosimaksuService, ValinnantulosService}

class MigriService(hakemusRepository: HakemusRepository, hakuService: HakuService, valinnantulosService: ValinnantulosService,
                   oppijanumerorekisteriService: OppijanumerorekisteriService, valintarekisteriService: ValinnantulosRepository,
                   lukuvuosimaksuService: LukuvuosimaksuService, hakijaResolver: HakijaResolver) extends Logging {
  def parseForeignHakijat(henkilot: Set[Henkilo]): Set[MigriHakija] = {
    henkilot.map(henkilo => {
      val hetu = henkilo.hetu match {
        case Some(hetu) => Some(hetu.toString)
        case None => None
      }
      MigriHakija(
        henkilotunnus = hetu,
        henkiloOid = henkilo.oid.toString,
        sukunimi = henkilo.sukunimi,
        etunimet = henkilo.etunimet,
        kansalaisuudet = henkilo.kansalaisuudet,
        syntymaaika = henkilo.syntymaaika,
        Set()
      )
    }
    ).filterNot(hakija => hakija.kansalaisuudet match {
      case Some(kansalaisuudet) => kansalaisuudet.contains("246")
    })
  }

  def getMigriHenkilotForOids(hakijaOids: Set[HakijaOid]): Set[MigriHakija] = {
    oppijanumerorekisteriService.henkilot(hakijaOids).fold(e => {
      val errorString: String = s"Error fetching hakijas for oid(s): $hakijaOids found. Cause: $e"
      logger.warn(errorString)
      throw new RuntimeException(errorString)
    }, henkilot => parseForeignHakijat(henkilot.values.toSet))
  }

  def getMigriHenkilotForHetus(hetus: Set[String]): Set[MigriHakija] = {
    oppijanumerorekisteriService.henkilotForHetus(hetus).fold(e => {
      val errorString: String = s"Error fetching hakijas for hetu(s): $hetus found. Cause: $e"
      logger.warn(errorString)
      throw new RuntimeException(errorString)
    }, henkilot => parseForeignHakijat(henkilot))
  }

  private def tuloksetToMigriHakemukset(tulokset: Set[ValinnantulosWithTilahistoria], auditInfo: AuditInfo): Set[MigriHakemus] = {
    logger.info(s"Muodostetaan MigriHakemukset ${tulokset.size} valinnantulokselle")
    tulokset.map(tulos => {
      getHakukohdeMigri(tulos.valinnantulos.hakukohdeOid) match {
        case Some(hakukohde: HakukohdeMigri) =>
          hakemusRepository.findHakemus(tulos.valinnantulos.hakemusOid).fold(e => {
            val errorString: String = s"No hakemus found for migri hakijaOid: ${tulos.valinnantulos.hakemusOid}, cause: ${e.toString}"
            logger.error(errorString)
            throw new RuntimeException(errorString)
          }, h => {
            val lukuvuosimaksu = lukuvuosimaksuService.getLukuvuosimaksuByHakijaAndHakukohde(HakijaOid(h.henkiloOid), tulos.valinnantulos.hakukohdeOid, auditInfo) match {
              case Some(maksu) => Some(maksu.maksuntila.toString)
              case None => None
            }
            val maksuvelvollisuus: Option[String] =
              if (h.maksuvelvollisuudet.exists(m => m._1 == tulos.valinnantulos.hakukohdeOid.toString))
                Some(h.maksuvelvollisuudet.filter(m => m._1 == tulos.valinnantulos.hakukohdeOid.toString).head._2)
              else None
            MigriHakemus(
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
    })
  }

  def getMigriHakijatByHetus(hetus: Set[String], auditInfo: AuditInfo) = {
    logger.info(s"migriHakijat: Haetaan henkilöt ${hetus.size} henkilötunnukselle.")
    val hakijat = getMigriHenkilotForHetus(hetus)
    logger.info(s"migriHakijat: Löydettiin ${hetus.size} henkilötunnukselle ${hakijat.size} henkilöä. Haetaan hakemukset.")
    enrichHakijatWithHakemukses(hakijat, auditInfo)
  }

  def getMigriHakijatByOids(henkilot: Set[HakijaOid], auditInfo: AuditInfo) = {
    logger.info(s"migriHakijat: Haetaan henkilöt ${henkilot.size} henkilöOidille.")
    val hakijat = getMigriHenkilotForOids(henkilot)
    logger.info(s"migriHakijat: Löydettiin ${henkilot.size} henkilöOidille ${hakijat.size} henkilöä. Haetaan hakemukset.")
    enrichHakijatWithHakemukses(hakijat, auditInfo)
  }


  def enrichHakijatWithHakemukses(henkilot: Set[MigriHakija], auditInfo: AuditInfo): Set[MigriHakija] = {
    henkilot.map(hakija => {
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
        logger.error(s"Jokin meni pieleen migrihakukohteen haussa: ${e.toString}")
        throw e
    }
  }
}
