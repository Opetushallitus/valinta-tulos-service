package fi.vm.sade.valintatulosservice.migri

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.migraatio.vastaanotot.HakijaResolver
import fi.vm.sade.valintatulosservice.oppijanumerorekisteri.{Henkilo, OppijanumerorekisteriService}
import fi.vm.sade.valintatulosservice.tarjonta.{HakuService, HakukohdeMigri, MigriTarjontaHakukohdeNotImplementedException}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValinnantulosRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakijaOid, HakukohdeOid, HyvaksyttyValinnanTila, ValinnantulosWithTilahistoria}
import fi.vm.sade.valintatulosservice.{AuditInfo, LukuvuosimaksuService, ValinnantulosService}

class MigriService(hakemusRepository: HakemusRepository, hakuService: HakuService, valinnantulosService: ValinnantulosService,
                   oppijanumerorekisteriService: OppijanumerorekisteriService, valintarekisteriService: ValinnantulosRepository,
                   lukuvuosimaksuService: LukuvuosimaksuService, hakijaResolver: HakijaResolver) extends Logging {

  def isFinnishNational(hakija: MigriHakija): Boolean = hakija.kansalaisuudet match {
    case Some(kansalaisuudet) => kansalaisuudet.contains("246")
  }

  def parseForeignHakijat(henkilot: Set[Henkilo]): Set[MigriHakija] = {
    henkilot.map(parseForeignHakija)
      .filterNot(isFinnishNational)
  }

  def parseForeignHakijat(henkiloMap: Map[HakijaOid, Henkilo]): Set[MigriHakija] = {
    henkiloMap.map{ case (henkiloOid, henkilo) =>
      parseForeignHakija(henkilo).copy(henkiloOid = henkiloOid.toString)
    }.toSet.filterNot(isFinnishNational)
  }

  def parseForeignHakija(henkilo: Henkilo): MigriHakija = {
    val hetu = henkilo.hetu.map(_.toString)
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

  def getMigriHenkilotForOids(hakijaOids: Set[HakijaOid]): Set[MigriHakija] = {
    oppijanumerorekisteriService.henkilot(hakijaOids).fold(e => {
      val errorString: String = s"Error fetching hakijas for oid(s): $hakijaOids found. Cause: $e"
      logger.warn(errorString, e)
      throw new RuntimeException(errorString)
    }, henkilot => {
      logger.info(s"Before filtering for foreign people: ${henkilot.size} results for ${hakijaOids.size} oids.")
      parseForeignHakijat(henkilot)
    })
  }

  def getMigriHenkilotForHetus(hetus: Set[String]): Set[MigriHakija] = {
    oppijanumerorekisteriService.henkilotForHetus(hetus).fold(e => {
      val errorString: String = s"Error fetching hakijas for hetu(s): $hetus found. Cause: $e"
      logger.warn(errorString, e)
      throw new RuntimeException(errorString)
    }, henkilot => {
      logger.info(s"Before filtering for foreign people: ${henkilot.size} results for ${hetus.size} hetua.")
      parseForeignHakijat(henkilot)
    })
  }

  private def tuloksetToMigriHakemukset(tulokset: Set[ValinnantulosWithTilahistoria], auditInfo: AuditInfo): Set[MigriHakemus] = {
    tulokset.map(tulos => {
      getHakukohdeMigri(tulos.valinnantulos.hakukohdeOid) match {
        case Some(hakukohde: HakukohdeMigri) =>
          hakemusRepository.findHakemus(tulos.valinnantulos.hakemusOid).fold(e => {
            val errorString: String = s"No hakemus found for migri hakijaOid: ${tulos.valinnantulos.hakemusOid}, cause: ${e.toString}"
            logger.error(errorString)
            throw new RuntimeException(errorString)
          }, hakemus => {
            val maksuvelvollisuus: Option[String] = hakemus.maksuvelvollisuudet.find(m => m._1.equals(tulos.valinnantulos.hakukohdeOid.toString)).map(_._2)
            val lukuvuosimaksu = maksuvelvollisuus match {
              case Some(mv) if mv.equals("REQUIRED") =>
                lukuvuosimaksuService.getLukuvuosimaksuByHakijaAndHakukohde(HakijaOid(hakemus.henkiloOid), tulos.valinnantulos.hakukohdeOid, auditInfo) match {
                  case Some(maksu) => Some(maksu.maksuntila.toString)
                  case None => Some("MAKSAMATTA")
              }
              case _ => None
            }
            Some(MigriHakemus(
              hakuOid = hakemus.hakuOid.toString,
              hakuNimi = hakukohde.hakuNimi,
              hakemusOid = hakemus.oid.toString,
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
              koulutuksenAlkamisvuosi = hakukohde.koulutuksenAlkamisvuosi))
          })
        case None => None
      }
    }).filter(_.isDefined).flatten
  }

  def getMigriHakijatByHetus(hetus: Set[String], auditInfo: AuditInfo) = {
    val hakijat = getMigriHenkilotForHetus(hetus)
    logger.info(s"migriHakijat: Löydettiin ${hetus.size} henkilötunnukselle ${hakijat.size} henkilöä. Haetaan hakemukset.")
    enrichHakijatWithHakemukses(hakijat, auditInfo)
  }

  def getMigriHakijatByOids(henkilot: Set[HakijaOid], auditInfo: AuditInfo) = {
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
      case e: MigriTarjontaHakukohdeNotImplementedException =>
        logger.warn(s"Oltiin hakemassa migrihakukohdetta vanhan tarjonnan hakukohteelle: ${e.toString}. Skipataan hakukohde.")
        None
      case e: Throwable =>
        logger.error(s"Jokin meni pieleen migrihakukohteen haussa: ${e.toString}")
        throw e
    }
  }
}
