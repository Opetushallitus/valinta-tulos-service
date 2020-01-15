package fi.vm.sade.valintatulosservice

import java.time.Instant
import java.util.Date

import fi.vm.sade.sijoittelu.domain.{ValintatuloksenTila, Valintatulos}
import fi.vm.sade.sijoittelu.tulos.dto
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.{HakijaDTO, HakijaPaginationObject, HakutoiveDTO}
import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.config.VtsDynamicAppConfig
import fi.vm.sade.valintatulosservice.domain.Valintatila.isHyväksytty
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, OhjausparametritService}
import fi.vm.sade.valintatulosservice.sijoittelu.{SijoittelutulosService, ValintarekisteriHakijaDTOClient, ValintarekisteriValintatulosDao}
import fi.vm.sade.valintatulosservice.tarjonta.{Haku, HakuService}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, ValinnantulosRepository, VastaanottoRecord, VirkailijaVastaanottoRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila.vastaanottanut
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import fi.vm.sade.valintatulosservice.vastaanotto.VastaanottoUtils.ehdollinenVastaanottoMahdollista
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime

import scala.collection.JavaConverters._

class ValintatulosService(valinnantulosRepository: ValinnantulosRepository,
                           vastaanotettavuusService: VastaanotettavuusService,
                          sijoittelutulosService: SijoittelutulosService,
                          ohjausparametritService: OhjausparametritService,
                          hakemusRepository: HakemusRepository,
                          virkailijaVastaanottoRepository: VirkailijaVastaanottoRepository,
                          hakuService: HakuService,
                          hakijaVastaanottoRepository: HakijaVastaanottoRepository,
                          hakukohdeRecordService: HakukohdeRecordService,
                          valintatulosDao: ValintarekisteriValintatulosDao,
                          hakijaDTOClient: ValintarekisteriHakijaDTOClient)(implicit appConfig: VtsAppConfig, dynamicAppConfig: VtsDynamicAppConfig) extends Logging {
  def this(valinnantulosRepository: ValinnantulosRepository,
            vastaanotettavuusService: VastaanotettavuusService,
           sijoittelutulosService: SijoittelutulosService,
           hakemusRepository: HakemusRepository,
           virkailijaVastaanottoRepository: VirkailijaVastaanottoRepository,
           hakuService: HakuService,
           hakijaVastaanottoRepository: HakijaVastaanottoRepository,
           hakukohdeRecordService: HakukohdeRecordService,
           valintatulosDao: ValintarekisteriValintatulosDao,
           hakijaDTOClient: ValintarekisteriHakijaDTOClient )(implicit appConfig: VtsAppConfig, dynamicAppConfig: VtsDynamicAppConfig) =
    this(valinnantulosRepository, vastaanotettavuusService, sijoittelutulosService, appConfig.ohjausparametritService, hakemusRepository, virkailijaVastaanottoRepository, hakuService, hakijaVastaanottoRepository, hakukohdeRecordService, valintatulosDao, hakijaDTOClient)

  def haunKoulutuksenAlkamiskaudenVastaanototYhdenPaikanSaadoksenPiirissa(hakuOid: HakuOid) : Set[VastaanottoRecord] = {
    (for {
      hakukohdeOids <- hakuService.getHakukohdeOids(hakuOid).right
      hakukohteet <- hakukohdeRecordService.getHakukohdeRecords(hakuOid, hakukohdeOids.toSet).right
    } yield hakukohteet.collect({ case YPSHakukohde(oid, _, koulutuksenAlkamiskausi) => (oid, koulutuksenAlkamiskausi) }).groupBy(_._2).toList match {
      case Nil => Set.empty[VastaanottoRecord]
      case (kausi, _) :: Nil => virkailijaVastaanottoRepository.findkoulutuksenAlkamiskaudenVastaanottaneetYhdenPaikanSaadoksenPiirissa(kausi)
      case kaudet => throw new IllegalStateException(s"Haussa $hakuOid on YPS piirissä olevia hakukohteita joilla on eri koulutuksen alkamiskausi ${kaudet.map(_._2.map(_._1).mkString(", ")).mkString("; ")}")
    }) match {
      case Right(vs) => vs
      case Left(t) => throw t
    }
  }

  def hakemuksentulos(hakemusOid: HakemusOid): Option[Hakemuksentulos] =
    hakemusRepository.findHakemus(hakemusOid).right.toOption.flatMap(hakemuksentulos)

  def hakemuksentulos(h: Hakemus): Option[Hakemuksentulos] = {
    for {
      haku <- hakuService.getHaku(h.hakuOid).right.toOption
      latestSijoitteluajoId = sijoittelutulosService.findLatestSijoitteluajoId(h.hakuOid)
      ilmoittautumisenAikaleimat = timed(s"Ilmoittautumisten aikaleimojen haku hakijalle ${h.henkiloOid}", 1000) {
        Map(h.henkiloOid -> valinnantulosRepository.runBlocking(valinnantulosRepository.getIlmoittautumisenAikaleimat(h.henkiloOid)))
      }
      hakukohdeRecords <- hakukohdeRecordService.getHakukohdeRecords(h.toiveet.map(_.oid)).right.toOption
      vastaanototKausilla <- Some({
        val vastaanototByKausi = hakukohdeRecords
          .collect({ case YPSHakukohde(_, _, koulutuksenAlkamiskausi) => koulutuksenAlkamiskausi })
          .distinct
          .map(kausi => kausi -> hakijaVastaanottoRepository.runBlocking(hakijaVastaanottoRepository.findYhdenPaikanSaannonPiirissaOlevatVastaanotot(h.henkiloOid, kausi)))
          .toMap
        hakukohdeRecords.collect({
          case YPSHakukohde(oid, _, koulutuksenAlkamiskausi) => oid -> (koulutuksenAlkamiskausi, vastaanototByKausi(koulutuksenAlkamiskausi).map(_.henkiloOid).toSet)
        }).toMap
      })
      hakemus <- fetchTulokset(
        haku,
        () => List(h).iterator,
        hakijaOidsByHakemusOids => sijoittelutulosService.hakemuksenTulos(haku,
          h.oid,
          hakijaOidsByHakemusOids.findBy(h.oid),
          sijoittelutulosService.findOhjausparametritFromOhjausparametritService(h.hakuOid),
          latestSijoitteluajoId).toSeq,
        vastaanottoKaudella = vastaanototKausilla.get,
        ilmoittautumisenAikaleimat = ilmoittautumisenAikaleimat
      ).toSeq.headOption
    } yield hakemus
  }

  def hakemustenTulosByHaku(hakuOid: HakuOid, checkJulkaisuAikaParametri: Boolean): Option[Iterator[Hakemuksentulos]] = {
    val haunVastaanotot = timed("Fetch haun vastaanotot for haku: " + hakuOid, 1000) {
      virkailijaVastaanottoRepository.findHaunVastaanotot(hakuOid).groupBy(_.henkiloOid)
    }
    hakemustenTulosByHaku(hakuOid, Some(haunVastaanotot), checkJulkaisuAikaParametri)
  }

  def haeTilatHakijoille(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid, valintatapajonoOid: ValintatapajonoOid, hakemusOids: Set[HakemusOid]): Set[TilaHakijalle] = {
    val hakemukset: Seq[Hakemus] = hakemusRepository.findHakemuksetByOids(hakemusOids).toSeq
    val uniqueHakukohdeOids: Seq[HakukohdeOid] = hakemukset.flatMap(_.toiveet.map(_.oid)).distinct
    (hakuService.getHaku(hakuOid), hakukohdeRecordService.getHakukohdeRecords(uniqueHakukohdeOids))  match {
      case (Right(haku), Right(hakukohdes)) =>
        val vastaanototByKausi = timed("kaudenVastaanotot", 1000)({
          virkailijaVastaanottoRepository.findkoulutuksenAlkamiskaudenVastaanottaneetYhdenPaikanSaadoksenPiirissa(
            hakukohdes.collect({ case YPSHakukohde(_, _, koulutuksenAlkamiskausi) => koulutuksenAlkamiskausi }).toSet)
        })
        val vastaanototKausilla = hakukohdes.collect({
          case YPSHakukohde(oid, _, koulutuksenAlkamiskausi) => oid -> (koulutuksenAlkamiskausi, vastaanototByKausi(koulutuksenAlkamiskausi).map(_.henkiloOid))
        }).toMap
        val ilmoittautumisenAikaleimat = timed(s"Ilmoittautumisten aikaleimojen haku haulle $hakuOid", 1000) {
          valinnantulosRepository.runBlocking(valinnantulosRepository.getIlmoittautumisenAikaleimat(hakuOid))
            .groupBy(_._1).mapValues(i => i.map(t => (t._2, t._3)))
        }
        val vastaanottoaikataulu = sijoittelutulosService.findOhjausparametritFromOhjausparametritService(hakuOid)
        val latestSijoitteluajoId = sijoittelutulosService.findLatestSijoitteluajoId(hakuOid)
        val hakemustenTulokset = fetchTulokset(
          haku,
          () => hakemukset.toIterator,
          personOidFromHakemusResolver => hakemusOids.flatMap(hakemusOid => sijoittelutulosService.hakemuksenTulos(haku, hakemusOid, personOidFromHakemusResolver.findBy(hakemusOid), vastaanottoaikataulu, latestSijoitteluajoId)).toSeq,
          vastaanottoKaudella = vastaanototKausilla.get,
          ilmoittautumisenAikaleimat = ilmoittautumisenAikaleimat
        )
        hakemustenTulokset.flatMap { hakemuksenTulos =>
          hakemuksenTulos.hakutoiveet.find(_.valintatapajonoOid == valintatapajonoOid).map { hakutoiveenTulos =>
            val tilaHakijalle = ValintatuloksenTila.valueOf(hakutoiveenTulos.vastaanottotila.toString)
            TilaHakijalle(hakemusOid = hakemuksenTulos.hakemusOid,
              hakukohdeOid = hakutoiveenTulos.hakukohdeOid,
              valintatapajonoOid = hakutoiveenTulos.valintatapajonoOid,
              tilaHakijalle = tilaHakijalle.toString)
          }
        }.toSet
      case (Left(e), Left(ee)) =>
        logger.warn(s"Could not find haku $hakuOid", e)
        logger.warn(s"Could not find hakukohdes $uniqueHakukohdeOids", ee)
        Set()
      case (Left(e), _) =>
        logger.warn(s"Could not find haku $hakuOid", e)
        Set()
      case (_, Left(ee)) =>
        logger.warn(s"Could not find hakukohdes $uniqueHakukohdeOids", ee)
        Set()
    }
  }

  def hakemustenTulosByHaku(hakuOid: HakuOid, haunVastaanotot: Option[Map[String,Set[VastaanottoRecord]]], checkJulkaisuAikaParametri: Boolean = true): Option[Iterator[Hakemuksentulos]] = {
    timed("Fetch hakemusten tulos for haku: " + hakuOid, 1000) (
      for {
        haku <- hakuService.getHaku(hakuOid).right.toOption
        hakukohdeOids <- hakuService.getHakukohdeOids(hakuOid).right.toOption
        hakukohteet <- hakukohdeRecordService.getHakukohdeRecords(hakuOid, hakukohdeOids.toSet).right.toOption
        vastaanototByKausi = timed(s"kausien vastaanotot", 1000)({
          virkailijaVastaanottoRepository.findkoulutuksenAlkamiskaudenVastaanottaneetYhdenPaikanSaadoksenPiirissa(
            hakukohteet.collect({ case YPSHakukohde(_, _, koulutuksenAlkamiskausi) => koulutuksenAlkamiskausi }).toSet)
        })
        vastaanototKausilla = hakukohteet.collect({
          case YPSHakukohde(oid, _, koulutuksenAlkamiskausi) => oid -> (koulutuksenAlkamiskausi, vastaanototByKausi(koulutuksenAlkamiskausi).map(_.henkiloOid))
        }).toMap
        ilmoittautumisenAikaleimat = timed(s"Ilmoittautumisten aikaleimojen haku haulle $hakuOid", 1000) {
          valinnantulosRepository.runBlocking(valinnantulosRepository.getIlmoittautumisenAikaleimat(hakuOid))
            .groupBy(_._1).mapValues(i => i.map(t => (t._2, t._3)))
        }
      } yield {
        fetchTulokset(
          haku,
          () => hakemusRepository.findHakemukset(hakuOid),
          personOidFromHakemusResolver => sijoittelutulosService.hakemustenTulos(hakuOid, None, personOidFromHakemusResolver, haunVastaanotot = haunVastaanotot),
          Some(new PersonOidFromHakemusResolver {
            private lazy val hakijaOidByHakemusOid = timed("personOids from hakemus", 1000)(hakemusRepository.findPersonOids(hakuOid))
            override def findBy(hakemusOid: HakemusOid): Option[String] = hakijaOidByHakemusOid.get(hakemusOid)
          }),
          checkJulkaisuAikaParametri,
          vastaanottoKaudella = vastaanototKausilla.get,
          ilmoittautumisenAikaleimat
        )
      }
    )
  }

  def hakemustenTulosByHakukohde(hakuOid: HakuOid,
                                 hakukohdeOid: HakukohdeOid,
                                 hakukohteenVastaanotot: Option[Map[String,Set[VastaanottoRecord]]] = None,
                                 checkJulkaisuAikaParametri: Boolean = true,
                                 vainHakukohteenTiedot: Boolean = false): Either[Throwable, Iterator[Hakemuksentulos]] = {
    val hakemukset: Seq[Hakemus] = hakemusRepository.findHakemuksetByHakukohde(hakuOid, hakukohdeOid).toVector //hakemus-mongo
    val uniqueHakukohdeOids: Seq[HakukohdeOid] = hakemukset.flatMap(_.toiveet.map(_.oid)).distinct
    timed("Fetch hakemusten tulos for haku: "+ hakuOid + " and hakukohde: " + hakukohdeOid, 1000) (
      for {
        haku <- hakuService.getHaku(hakuOid).right //tarjonta
        hakukohteet <- hakukohdeRecordService.getHakukohdeRecords(uniqueHakukohdeOids).right //valintarekisteri/hakukohde
      } yield {
        val vastaanototByKausi = timed(s"kausien vastaanotot", 1000)({
          virkailijaVastaanottoRepository.findkoulutuksenAlkamiskaudenVastaanottaneetYhdenPaikanSaadoksenPiirissa(
            hakukohteet.collect({ case YPSHakukohde(_, _, koulutuksenAlkamiskausi) => koulutuksenAlkamiskausi }).toSet)
        })
        val vastaanototKausilla = hakukohteet.collect({
          case YPSHakukohde(oid, _, koulutuksenAlkamiskausi) => oid -> (koulutuksenAlkamiskausi, vastaanototByKausi(koulutuksenAlkamiskausi).map(_.henkiloOid))
        }).toMap
        val ilmoittautumisenAikaleimat = timed(s"Ilmoittautumisten aikaleimojen haku haulle $hakuOid", 1000) {
          valinnantulosRepository.runBlocking(valinnantulosRepository.getIlmoittautumisenAikaleimat(hakuOid))
            .groupBy(_._1).mapValues(i => i.map(t => (t._2, t._3)))
        }
        fetchTulokset(
          haku,
          () => hakemukset.toIterator,
          personOidFromHakemusResolver => sijoittelutulosService.hakemustenTulos(hakuOid, Some(hakukohdeOid), personOidFromHakemusResolver, hakukohteenVastaanotot, vainHakukohde = vainHakukohteenTiedot),
          Some(new PersonOidFromHakemusResolver {
            private lazy val hakijaOidByHakemusOid = timed("personOids from hakemus", 1000)(hakemusRepository.findPersonOids(hakuOid, hakukohdeOid))
            override def findBy(hakemusOid: HakemusOid): Option[String] = hakijaOidByHakemusOid.get(hakemusOid)
          }),
          checkJulkaisuAikaParametri,
          vastaanottoKaudella = vastaanototKausilla.get,
          ilmoittautumisenAikaleimat
        )
      }
    )
  }

  def findValintaTuloksetForVirkailija(hakuOid: HakuOid): List[Valintatulos] = {
    val haunVastaanotot = virkailijaVastaanottoRepository.findHaunVastaanotot(hakuOid).groupBy(_.henkiloOid)
    val hakemustenTulokset = hakemustenTulosByHaku(hakuOid, Some(haunVastaanotot)).getOrElse(throw new IllegalArgumentException(s"Unknown hakuOid $hakuOid"))
    val valintatulokset = valintatulosDao.loadValintatulokset(hakuOid)

    setValintatuloksetTilat(hakuOid, valintatulokset, mapHakemustenTuloksetByHakemusOid(hakemustenTulokset), haunVastaanotot)
    valintatulokset
  }

  def findValintaTuloksetForVirkailija(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): List[Valintatulos] = {
    val haunVastaanotot: Map[String, Set[VastaanottoRecord]] = virkailijaVastaanottoRepository.findHaunVastaanotot(hakuOid).groupBy(_.henkiloOid)
    val hakemustenTulokset: Iterator[Hakemuksentulos] = hakemustenTulosByHakukohde(hakuOid, hakukohdeOid, Some(haunVastaanotot)) match {
      case Right(x) => x
      case Left(e) => throw e
    }
    val valintatulokset:List[Valintatulos] = valintatulosDao.loadValintatuloksetForHakukohde(hakukohdeOid)

    setValintatuloksetTilat(hakuOid, valintatulokset, mapHakemustenTuloksetByHakemusOid(hakemustenTulokset), haunVastaanotot)
    valintatulokset
  }

  def findValintaTuloksetForVirkailijaWithoutTilaHakijalle(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): List[Valintatulos] = {
    val haunVastaanotot: Map[String, Set[VastaanottoRecord]] = timed(s"Fetch vastaanotto records for haku $hakuOid", 1000) {
      virkailijaVastaanottoRepository.findHaunVastaanotot(hakuOid).groupBy(_.henkiloOid)
    }
    lazy val hakemusOidsByHakijaOids = timed(s"Fetch hakija oids by hakemus oids for haku $hakuOid and hakukohde $hakukohdeOid", 1000) {
      hakemusRepository.findPersonOids(hakuOid, hakukohdeOid)
    }
    lazy val hakemusOidsByHakijaOidsForWholeHaku = timed(s"Fetch hakija oids by hakemus oids for haku $hakuOid $hakukohdeOid", 1000) {
      hakemusRepository.findPersonOids(hakuOid)
    }

    val valintatulokset: List[Valintatulos] = timed(s"Fetch plain valintatulokset for haku $hakuOid", 1000) {
      valintatulosDao.loadValintatuloksetForHakukohde(hakukohdeOid)
    }

    val hakukohde = hakukohdeRecordService.getHakukohdeRecord(hakukohdeOid) match {
      case Right(h) => h
      case Left(t) => throw t
    }
    val kaudenVastaanotot = timed(s"Fetch YPS related kauden vastaanotot for hakukohde $hakukohdeOid", 1000) {
      hakukohde match {
        case YPSHakukohde(_, _, koulutuksenAlkamiskausi) =>
          virkailijaVastaanottoRepository.findkoulutuksenAlkamiskaudenVastaanottaneetYhdenPaikanSaadoksenPiirissa(koulutuksenAlkamiskausi)
        case _ =>
          Set.empty[VastaanottoRecord]
      }
    }

    val valintatulosIterator = valintatulokset.iterator
    while (valintatulosIterator.hasNext) {
      val v = valintatulosIterator.next()
      val hakijaOid: String = if (StringUtils.isNotBlank(v.getHakijaOid)) v.getHakijaOid else hakemusOidsByHakijaOids.getOrElse(HakemusOid(v.getHakemusOid), {
        logger.warn(s"Could not find hakija oid for ${v.getHakemusOid} when finding for hakukohde $hakukohdeOid , " +
          s"resorting to searching from whole haku $hakuOid")
        hakemusOidsByHakijaOidsForWholeHaku(HakemusOid(v.getHakemusOid))
      })
      val henkilonVastaanotot = haunVastaanotot.get(hakijaOid)
      val hakijanVastaanototHakukohteeseen: List[VastaanottoRecord] = henkilonVastaanotot.map(_.filter(_.hakukohdeOid == hakukohdeOid)).toList.flatten
      val tilaVirkailijalle: ValintatuloksenTila = paatteleVastaanottotilaVirkailijaaVarten(hakijaOid, hakuOid, hakukohde, hakijanVastaanototHakukohteeseen, kaudenVastaanotot)
      v.setTila(tilaVirkailijalle, tilaVirkailijalle, "", "") // pass same old and new tila to avoid log entries
      v.setHakijaOid(hakijaOid, "")
    }
    valintatulokset
  }

  def haeVastaanotonAikarajaTiedot(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid, hakemusOids: Set[HakemusOid]): Set[VastaanottoAikarajaMennyt] = {
    sijoittelutulosService.haeVastaanotonAikarajaTiedot(hakuOid, hakukohdeOid, hakemusOids)
  }


  def findValintaTuloksetForVirkailijaByHakemus(hakemusOid: HakemusOid): List[Valintatulos] = {
    val hakemuksenTulos = hakemuksentulos(hakemusOid)
      .getOrElse(throw new IllegalArgumentException(s"Not hakemuksen tulos for hakemus $hakemusOid"))
    val hakuOid = hakemuksenTulos.hakuOid
    val henkiloOid = hakemuksenTulos.hakijaOid
    val vastaanotot = virkailijaVastaanottoRepository.runBlocking(virkailijaVastaanottoRepository.findHenkilonVastaanototHaussa(henkiloOid, hakuOid))
    val valintatulokset: List[Valintatulos] = valintatulosDao.loadValintatuloksetForHakemus(hakemusOid)

    setValintatuloksetTilat(hakuOid, valintatulokset, Map(hakemusOid -> hakemuksenTulos), Map(henkiloOid -> vastaanotot))
    valintatulokset
  }

  def sijoittelunTulokset(hakuOid: HakuOid, sijoitteluajoId: String, hyvaksytyt: Option[Boolean], ilmanHyvaksyntaa: Option[Boolean], vastaanottaneet: Option[Boolean],
                          hakukohdeOid: Option[List[HakukohdeOid]], count: Option[Int], index: Option[Int]): HakijaPaginationObject = timed(s"Getting sijoittelun tulokset for haku $hakuOid") {
    val haunVastaanototByHakijaOid = timed("Fetch haun vastaanotot for haku: " + hakuOid, 1000) {
      virkailijaVastaanottoRepository.findHaunVastaanotot(hakuOid).groupBy(_.henkiloOid)
    }
    val hakemustenTulokset = hakukohdeOid match {
      case Some(oid :: Nil) => hakemustenTulosByHakukohde(hakuOid, oid, Some(haunVastaanototByHakijaOid)).right.get
      case _ => hakemustenTulosByHaku(hakuOid, Some(haunVastaanototByHakijaOid)).getOrElse(Iterator.empty)
    }
    val hakemustenTuloksetByHakemusOid: Map[HakemusOid, Hakemuksentulos] =
      timed("Realizing hakemukset mongo calls from hakemuksenTulokset") {
        hakemustenTulokset.map(h => h.hakemusOid -> h).toMap
      }

    try {
      val hakijaPaginationObject = sijoittelutulosService.sijoittelunTuloksetWithoutVastaanottoTieto(hakuOid, sijoitteluajoId, hyvaksytyt, ilmanHyvaksyntaa, vastaanottaneet,
        hakukohdeOid, count, index)
      assertNoMissingHakemusOidsInKeys(
        hakijaPaginationObject.getResults.asScala.map(h => HakemusOid(h.getHakemusOid)).toSet,
        hakemustenTuloksetByHakemusOid.keySet
      )
      hakijaPaginationObject.getResults.asScala.foreach { hakijaDto =>
        val hakemuksenTulos = hakemustenTuloksetByHakemusOid(HakemusOid(hakijaDto.getHakemusOid))
        hakijaDto.setHakijaOid(hakemuksenTulos.hakijaOid)
        hakijaDto.getHakutoiveet.asScala.foreach(hakutoiveDto => {
          val tulos = hakemuksenTulos.findHakutoive(HakukohdeOid(hakutoiveDto.getHakukohdeOid)).
            getOrElse(throw new IllegalStateException(s"Ei löydy hakutoiveelle ${hakutoiveDto.getHakukohdeOid} tulosta hakemukselta ${hakijaDto.getHakemusOid}. " +
              s"Hakutoive saattaa olla poistettu hakemukselta sijoittelun jälkeen."))._1
          hakutoiveDto.setVastaanottotieto(fi.vm.sade.sijoittelu.tulos.dto.ValintatuloksenTila.valueOf(tulos.vastaanottotila.toString))
          if (tulos.julkaistavissa) {
            hakutoiveDto.getHakutoiveenValintatapajonot.asScala.foreach(_.setTilanKuvaukset(tulos.tilanKuvaukset.asJava))
          }
        })
      }
      hakijaPaginationObject
    } catch {
      case e: Exception =>
        logger.error(s"Sijoittelun hakemuksia ei saatu haulle $hakuOid", e)
        new HakijaPaginationObject
    }
  }

  private def assertNoMissingHakemusOidsInKeys(hakemusOids: Set[HakemusOid], keys: Set[HakemusOid]): Unit = {
    val missingHakemusOids: Set[HakemusOid] = hakemusOids.diff(keys)
    if (missingHakemusOids.nonEmpty) {
      val missingOidsException = s"HakijaDTOs contained more hakemusOids than in hakukohteeseen hyväksytyt: $missingHakemusOids"
      throw new RuntimeException(missingOidsException)
    }
  }

  def sijoittelunTulosHakemukselle(hakuOid: HakuOid, sijoitteluajoId: String, hakemusOid: HakemusOid): Option[HakijaDTO] = {
    val hakemuksenTulosOption = hakemuksentulos(hakemusOid)
    val hakijaOidFromHakemusOption = hakemusRepository.findHakemus(hakemusOid).right.map(_.henkiloOid)
    val id = sijoittelutulosService.findSijoitteluAjo(hakuOid, sijoitteluajoId)
    sijoittelutulosService.sijoittelunTulosForAjoWithoutVastaanottoTieto(id, hakuOid, hakemusOid).map(hakijaDto => {
      hakijaOidFromHakemusOption.right.foreach(hakijaOidFromHakemus => hakijaDto.setHakijaOid(hakijaOidFromHakemus))
      hakemuksenTulosOption.foreach(hakemuksenTulos => populateVastaanottotieto(hakijaDto, hakemuksenTulos.hakutoiveet))
      hakijaDto
    })
  }

  def copyVastaanottotieto(hakutoiveDto: HakutoiveDTO, hakutoiveenTulos: Hakutoiveentulos): Unit = {
    hakutoiveDto.setVastaanottotieto(fi.vm.sade.sijoittelu.tulos.dto.ValintatuloksenTila.valueOf(hakutoiveenTulos.vastaanottotila.toString))
    hakutoiveDto.getHakutoiveenValintatapajonot.asScala.foreach(_.setTilanKuvaukset(hakutoiveenTulos.tilanKuvaukset.asJava))
  }

  def populateVastaanottotieto(hakijaDto: HakijaDTO, hakemuksenHakutoiveidenTuloksetVastaanottotiedonKanssa: List[Hakutoiveentulos]): Unit = {
    hakijaDto.getHakutoiveet.asScala.foreach(palautettavaHakutoiveDto =>
      hakemuksenHakutoiveidenTuloksetVastaanottotiedonKanssa.find(_.hakukohdeOid.toString == palautettavaHakutoiveDto.getHakukohdeOid) match {
        case Some(hakutoiveenOikeaTulos) => copyVastaanottotieto(palautettavaHakutoiveDto, hakutoiveenOikeaTulos)
        case None => palautettavaHakutoiveDto.setVastaanottotieto(dto.ValintatuloksenTila.KESKEN)
      }
    )
  }

  private def mapHakemustenTuloksetByHakemusOid(hakemustenTulokset:Iterator[Hakemuksentulos]):Map[HakemusOid, Hakemuksentulos] = {
    hakemustenTulokset.toList.groupBy(_.hakemusOid).mapValues(_.head)
  }

  private def setValintatuloksetTilat(hakuOid:HakuOid,
                                      valintatulokset: Seq[Valintatulos],
                                      hakemustenTulokset: Map[HakemusOid, Hakemuksentulos],
                                      haunVastaanotot: Map[String, Set[VastaanottoRecord]] ): Unit = {
    valintatulokset.foreach(valintaTulos => {
      val hakemuksenTulosOption: Option[Hakemuksentulos] = hakemustenTulokset.get(HakemusOid(valintaTulos.getHakemusOid)).orElse(
        crashOrLog(s"No hakemuksen tulos found for hakemus ${valintaTulos.getHakemusOid}"))
      val hakutoiveenTulosOption: Option[Hakutoiveentulos] = hakemuksenTulosOption.flatMap { _.findHakutoive(HakukohdeOid(valintaTulos.getHakukohdeOid)).map(_._1) }.orElse(
        crashOrLog(s"No hakutoive found for hakukohde ${valintaTulos.getHakukohdeOid} in hakemus ${valintaTulos.getHakemusOid}"))

      val tulosPari: Option[(Hakemuksentulos, Hakutoiveentulos)] = hakemuksenTulosOption.flatMap { hakemuksenTulos => hakutoiveenTulosOption.map((hakemuksenTulos, _)) }

      tulosPari match {
        case Some((hakemuksenTulos, hakutoiveenTulos)) =>
          assertThatHakijaOidsDoNotConflict(valintaTulos, hakemuksenTulos)
          val tilaHakijalle = ValintatuloksenTila.valueOf(hakutoiveenTulos.vastaanottotila.toString)

          val hakijaOid = hakemuksenTulos.hakijaOid
          val tilaVirkailijalle = ValintatulosService.toVirkailijaTila(tilaHakijalle, haunVastaanotot.get(hakijaOid), hakutoiveenTulos.hakukohdeOid)
          valintaTulos.setTila(tilaVirkailijalle, tilaVirkailijalle, "", "") // pass same old and new tila to avoid log entries
          if (valintaTulos.getHakijaOid == null) {
            valintaTulos.setHakijaOid(hakemuksenTulos.hakijaOid, "")
          }
          valintaTulos.setTilaHakijalle(tilaHakijalle)
        case None =>
          crashOrLog(s"Problem when processing valintatulos for hakemus ${valintaTulos.getHakemusOid}")
          valintaTulos.setTila(ValintatuloksenTila.KESKEN, ValintatuloksenTila.KESKEN, "Tilaa ei saatu luettua sijoittelun tuloksista", "")
          valintaTulos.setTilaHakijalle(ValintatuloksenTila.KESKEN)
      }
    })
  }

  private def assertThatHakijaOidsDoNotConflict(valintaTulos: Valintatulos, hakemuksenTulos: Hakemuksentulos): Unit = {
    if (valintaTulos.getHakijaOid != null && !valintaTulos.getHakijaOid.equals(hakemuksenTulos.hakijaOid)) {
      if (virkailijaVastaanottoRepository.runBlocking(virkailijaVastaanottoRepository.aliases(valintaTulos.getHakijaOid)).contains(hakemuksenTulos.hakijaOid)) {
        logger.warn(s"Valintatulos $valintaTulos with hakijaoid that is alias of hakijaoid in $hakemuksenTulos")
      } else {
        crashOrLog(s"Conflicting hakija oids: valintaTulos: ${valintaTulos.getHakijaOid} vs hakemuksenTulos: ${hakemuksenTulos.hakijaOid} in $valintaTulos , $hakemuksenTulos")
      }
    }
  }

  def fetchTulokset(haku: Haku,
                    getHakemukset: () => Iterator[Hakemus],
                    getSijoittelunTulos: PersonOidFromHakemusResolver => Seq[HakemuksenSijoitteluntulos],
                    personOidFromHakemusResolver: Option[PersonOidFromHakemusResolver] = None,
                    checkJulkaisuAikaParametri: Boolean = true,
                    vastaanottoKaudella: HakukohdeOid => Option[(Kausi, Set[String])],
                    ilmoittautumisenAikaleimat: Map[String, Iterable[(HakukohdeOid, Instant)]]): Iterator[Hakemuksentulos] = {
    val ohjausparametrit = ohjausparametritService.ohjausparametrit(haku.oid) match {
      case Right(o) => o
      case Left(e) => throw e
    }
    val hakemukset = getHakemukset()
    val sijoitteluTulokset = timed("Fetch sijoittelun tulos", 1000) {
      getSijoittelunTulos(
        personOidFromHakemusResolver.getOrElse(new PersonOidFromHakemusResolver {
          private lazy val hakijaOidByHakemusOid = getHakemukset().map(h => (h.oid, h.henkiloOid)).toMap
          override def findBy(hakemusOid: HakemusOid): Option[String] = hakijaOidByHakemusOid.get(hakemusOid)
        })
      ).map(t => (t.hakemusOid, t)).toMap
    }
    hakemukset.map(hakemus => {
      val sijoitteluTulos = sijoitteluTulokset.getOrElse(hakemus.oid, tyhjäHakemuksenTulos(hakemus.oid, ohjausparametrit.map(_.vastaanottoaikataulu)))
      val henkiloOid = sijoitteluTulos.hakijaOid.getOrElse(hakemus.henkiloOid)
      val hakemuksenVastaanototKaudella: HakukohdeOid => Option[(Kausi, Boolean)] = hakukohdeOid =>
        vastaanottoKaudella(hakukohdeOid).map(a => (a._1, a._2.contains(henkiloOid)))
      logger.debug("sijoittelunTulos " + sijoitteluTulos.toString)
      julkaistavaTulos(
        sijoitteluTulos,
        haku,
        ohjausparametrit,
        checkJulkaisuAikaParametri,
        hakemuksenVastaanototKaudella,
        ilmoittautumisenAikaleimat.getOrElse(henkiloOid, Iterable.empty),
        hakemus.henkilotiedot.hasHetu
      )(hakemus)
    })
  }

  def julkaistavaTulos(sijoitteluTulos: HakemuksenSijoitteluntulos,
                       haku: Haku,
                       ohjausparametrit: Option[Ohjausparametrit],
                       checkJulkaisuAikaParametri: Boolean,
                       vastaanottoKaudella: HakukohdeOid => Option[(Kausi, Boolean)],
                       ilmoittautumisenAikaleimat: Iterable[(HakukohdeOid, Instant)],
                       hasHetu: Boolean
                      )(h:Hakemus): Hakemuksentulos = {
    val tulokset = h.toiveet.map { toive =>
      val hakutoiveenSijoittelunTulos: HakutoiveenSijoitteluntulos = sijoitteluTulos.hakutoiveet.find { t =>
        t.hakukohdeOid == toive.oid
      }.getOrElse(HakutoiveenSijoitteluntulos.kesken(toive.oid, toive.tarjoajaOid))
      val ilmoittautumisenAikaleima = ilmoittautumisenAikaleimat.find(_._1 == toive.oid).map(t => Date.from(t._2))
      Hakutoiveentulos.julkaistavaVersioSijoittelunTuloksesta(
        ilmoittautumisenAikaleima,
        hakutoiveenSijoittelunTulos,
        toive,
        haku,
        ohjausparametrit,
        checkJulkaisuAikaParametri,
        hasHetu
      )
    }

    val lopullisetTulokset = Välitulos(sijoitteluTulos.hakemusOid, tulokset, haku, ohjausparametrit)
      .map(näytäHyväksyttyäJulkaisematontaAlemmistaKorkeinHyvaksyttyOdottamassaYlempiä)
      .map(näytäJulkaisematontaAlemmatPeruutetutKeskeneräisinä)
      .map(peruValmistaAlemmatKeskeneräisetJosKäytetäänSijoittelua)
      .map(näytäVarasijaltaHyväksytytHyväksyttyinäJosVarasijasäännötEiVoimassa)
      .map(sovellaSijoitteluaKayttanvaKorkeakouluhaunSaantoja)
      .map(näytäAlemmatPeruutuneetKeskeneräisinäJosYlemmätKeskeneräisiä)
      .map(piilotaKuvauksetKeskeneräisiltä)
      .map(asetaVastaanotettavuusValintarekisterinPerusteella(vastaanottoKaudella))
      .map(asetaKelaURL)
      .tulokset

    Hakemuksentulos(haku.oid, h.oid, sijoitteluTulos.hakijaOid.getOrElse(h.henkiloOid), ohjausparametrit.map(_.vastaanottoaikataulu), lopullisetTulokset)
  }
  private def asetaKelaURL(hakemusOid: HakemusOid, tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Option[Ohjausparametrit]): List[Hakutoiveentulos] = {
    val hakukierrosEiOlePäättynyt = !ohjausparametrit.flatMap(_.hakukierrosPaattyy).exists(_.isBefore(DateTime.now()))
    val näytetäänSiirryKelaanURL = dynamicAppConfig.näytetäänSiirryKelaanURL
    val näytetäänKelaURL = if (hakukierrosEiOlePäättynyt && näytetäänSiirryKelaanURL && haku.sallittuKohdejoukkoKelaLinkille) Some(appConfig.settings.kelaURL) else None

    tulokset.map {
      case tulos if vastaanottanut == tulos.vastaanottotila =>
        logger.debug("asetaKelaURL vastaanottanut")
        tulos.copy(kelaURL = näytetäänKelaURL)
      case tulos =>
        tulos
    }
  }
  def tyhjäHakemuksenTulos(hakemusOid: HakemusOid, aikataulu: Option[Vastaanottoaikataulu]) = HakemuksenSijoitteluntulos(hakemusOid, None, Nil)

  private def asetaVastaanotettavuusValintarekisterinPerusteella(vastaanottoKaudella: HakukohdeOid => Option[(Kausi, Boolean)])(hakemusOid: HakemusOid, tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Option[Ohjausparametrit]): List[Hakutoiveentulos] = {
    def ottanutVastaanToisenPaikan(tulos: Hakutoiveentulos): Hakutoiveentulos =
      tulos.copy(
        vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa,
        vastaanottotila = Vastaanottotila.ottanut_vastaan_toisen_paikan
      )

    def peruuntunutOttanutVastaanToisenPaikan(tulos: Hakutoiveentulos): Hakutoiveentulos =
      ottanutVastaanToisenPaikan(if (tulos.julkaistavissa) {
        tulos.copy(
          valintatila = Valintatila.peruuntunut,
          tilanKuvaukset = Map(
            "FI" -> "Peruuntunut, vastaanottanut toisen korkeakoulupaikan",
            "SV" -> "Annullerad, tagit emot en annan högskoleplats",
            "EN" -> "Cancelled, accepted another higher education study place"
          )
        )
      } else {
        tulos
      })

    def hyvaksyttyTaiVaralla(tulos: Hakutoiveentulos): Boolean = isHyväksytty(tulos.valintatila) || tulos.valintatila == Valintatila.varalla
    val hakutoiveetGroupedByKausi: Map[Option[(Kausi, Boolean)], List[Hakutoiveentulos]] = tulokset.groupBy(tulos => vastaanottoKaudella(tulos.hakukohdeOid))

    def hakutoiveetToOriginalOrder(originalHakutoiveet: List[Hakutoiveentulos]): Ordering[Hakutoiveentulos] = {
      val oids = tulokset.map(_.hakukohdeOid)
      Ordering.by[Hakutoiveentulos,Int](u => oids.indexOf(u.hakukohdeOid))
    }

    hakutoiveetGroupedByKausi.flatMap {
      case (Some((_, vastaanotto)), kaudenTulokset) =>
        val ehdollinenVastaanottoTallaHakemuksella = kaudenTulokset.exists(x => Vastaanottotila.ehdollisesti_vastaanottanut == x.vastaanottotila)
        val sitovaVastaanottoTallaHakemuksella = kaudenTulokset.exists(x => vastaanottanut == x.vastaanottotila)
        if (ehdollinenVastaanottoTallaHakemuksella) {
          logger.debug("asetaVastaanotettavuusValintarekisterinPerusteella ehdollinenVastaanottoTallaHakemuksella")
          kaudenTulokset
        } else if (sitovaVastaanottoTallaHakemuksella) {
          kaudenTulokset.map(tulos => if (Vastaanottotila.kesken == tulos.virkailijanTilat.vastaanottotila && hyvaksyttyTaiVaralla(tulos)) {
            logger.debug("asetaVastaanotettavuusValintarekisterinPerusteella sitovaVastaanottoTallaHakemuksella peruuntunutOttanutVastaanToisenPaikan")
            peruuntunutOttanutVastaanToisenPaikan(tulos)
          } else {
            logger.debug("asetaVastaanotettavuusValintarekisterinPerusteella sitovaVastaanottoTallaHakemuksella")
            tulos
          })
        } else {
          kaudenTulokset.map(tulos => if (Vastaanottotila.kesken == tulos.virkailijanTilat.vastaanottotila && hyvaksyttyTaiVaralla(tulos) && vastaanotto) {
            logger.debug("asetaVastaanotettavuusValintarekisterinPerusteella sitovaVastaanottoTallaHakemuksella peruuntunutOttanutVastaanToisenPaikan vastaanotto")
            peruuntunutOttanutVastaanToisenPaikan(tulos)
          } else {
            tulos
          })
        }
      case (None, kaudettomatTulokset) =>
        logger.debug("asetaVastaanotettavuusValintarekisterinPerusteella kaudettomatTulokset")
        kaudettomatTulokset
    }.toList.sorted(hakutoiveetToOriginalOrder(tulokset))
  }

  private def paatteleVastaanottotilaVirkailijaaVarten(hakijaOid: String,
                                                       hakuOid: HakuOid,
                                                       hakukohde: HakukohdeRecord,
                                                       hakijanVastaanototHakukohteeseen: List[VastaanottoRecord],
                                                       kaudenVastaanototYpsnPiirissa: Set[VastaanottoRecord]): ValintatuloksenTila = {
    def resolveValintatuloksenTilaVirkailijalleFrom(several: Iterable[VastaanottoRecord]): ValintatuloksenTila = {
      if (several.groupBy(_.action).size == 1) {
        several.head.action.valintatuloksenTila
      } else {
        throw new IllegalStateException(s"Don't know how to choose relevant vastaanotto record from $several")
      }
    }

    val tilaSuoraanHakukohteenVastaanottojenPerusteella: ValintatuloksenTila = hakijanVastaanototHakukohteeseen match {
      case Nil => ValintatuloksenTila.KESKEN
      case only :: Nil => only.action.valintatuloksenTila
      case several => resolveValintatuloksenTilaVirkailijalleFrom(several)
    }

    if (!hakukohde.yhdenPaikanSaantoVoimassa) {
      tilaSuoraanHakukohteenVastaanottojenPerusteella
    } else if (tilaSuoraanHakukohteenVastaanottojenPerusteella == ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI ||
      tilaSuoraanHakukohteenVastaanottojenPerusteella == ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT ||
      tilaSuoraanHakukohteenVastaanottojenPerusteella == ValintatuloksenTila.PERUNUT ||
      tilaSuoraanHakukohteenVastaanottojenPerusteella == ValintatuloksenTila.PERUUTETTU ||
      tilaSuoraanHakukohteenVastaanottojenPerusteella == ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA
    ) {
      tilaSuoraanHakukohteenVastaanottojenPerusteella
    } else {
      paatteleVastaanottotilaYhdenPaikanSaadoksenMukaanVirkailijaaVarten(hakijaOid, hakuOid, kaudenVastaanototYpsnPiirissa)
    }
  }

  private def paatteleVastaanottotilaYhdenPaikanSaadoksenMukaanVirkailijaaVarten(henkiloOid: String,
                                                                                 hakuOid: HakuOid,
                                                                                 kaudenVastaanototYpsnPiirissa: Set[VastaanottoRecord]): ValintatuloksenTila= {
    val henkilonAiemmatVastaanototSamalleKaudelleYpsnPiirissa = kaudenVastaanototYpsnPiirissa.filter(_.henkiloOid == henkiloOid)
    def vastaanottoEriHaussa: Boolean = henkilonAiemmatVastaanototSamalleKaudelleYpsnPiirissa.exists(_.hakuOid != hakuOid)

    if (henkilonAiemmatVastaanototSamalleKaudelleYpsnPiirissa.isEmpty) {
      ValintatuloksenTila.KESKEN
    } else if (vastaanottoEriHaussa) {
      ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN
    } else ValintatuloksenTila.KESKEN
  }

  private def sovellaSijoitteluaKayttanvaKorkeakouluhaunSaantoja(hakemusOid: HakemusOid, tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Option[Ohjausparametrit]) = {
    if (haku.korkeakoulu && haku.sijoitteluJaPriorisointi) {
      val indexedTulokset = tulokset.zipWithIndex
      val firstVaralla = tulokset.indexWhere(_.valintatila == Valintatila.varalla)
      val firstPeruttuAfterFirstVaralla = if (firstVaralla >= 0) {
        indexedTulokset.indexWhere {
          case (tulos, index) => index > firstVaralla && Valintatila.perunut == tulos.valintatila
        }
      } else {
        -1
      }
      val firstVastaanotettu = tulokset.indexWhere(_.vastaanottotila == vastaanottanut)
      val firstKesken = tulokset.indexWhere(_.valintatila == Valintatila.kesken)
      val firstHyvaksyttyUnderFirstVarallaAndNoPeruttuInBetween = if (firstVaralla >= 0) {
        indexedTulokset.indexWhere {
          case (tulos, index) => index > firstVaralla && Valintatila.isHyväksytty(tulos.valintatila) && (firstPeruttuAfterFirstVaralla == -1 || firstPeruttuAfterFirstVaralla > index)
        }
      } else {
        -1
      }

      def hyvaksyttyJaVastaanottamatta(tulos: Hakutoiveentulos, index: Int): Hakutoiveentulos = {
        if (firstVastaanotettu >= 0 && index != firstVastaanotettu) {
          // Peru vastaanotettua paikkaa alemmat hyväksytyt hakutoiveet
          logger.debug("sovellaSijoitteluaKayttanvaKorkeakouluhaunSaantoja valintatila > peruuntunut, ei vastaanotettavissa {}", index)
          tulos.copy(valintatila = Valintatila.peruuntunut, vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa)
        } else if (index == firstHyvaksyttyUnderFirstVarallaAndNoPeruttuInBetween) {
          if (ehdollinenVastaanottoMahdollista(ohjausparametrit)) {
            logger.debug("sovellaSijoitteluaKayttanvaKorkeakouluhaunSaantoja vastaanotettavuustila > vastaanotettavissa_ehdollisesti {}", index)
            // Ehdollinen vastaanotto mahdollista
            tulos.copy(vastaanotettavuustila = Vastaanotettavuustila.vastaanotettavissa_ehdollisesti)
          } else {
            logger.debug("sovellaSijoitteluaKayttanvaKorkeakouluhaunSaantoja toOdottaaYlempienHakutoiveidenTuloksia {} {}", index, Valintatila.isHyväksytty(tulos.valintatila))
            // Ehdollinen vastaanotto ei vielä mahdollista, näytetään keskeneräisenä
            tulos.toOdottaaYlempienHakutoiveidenTuloksia
          }
        } else if (firstKesken >= 0 && index > firstKesken) {
          logger.debug("sovellaSijoitteluaKayttanvaKorkeakouluhaunSaantoja toOdottaaYlempienHakutoiveidenTuloksia {} {}", index, Valintatila.isHyväksytty(tulos.valintatila))
          tulos.toOdottaaYlempienHakutoiveidenTuloksia
        } else {
          logger.debug("sovellaSijoitteluaKayttanvaKorkeakouluhaunSaantoja default")
          tulos
        }
      }

      tulokset.zipWithIndex.map {
        case (tulos, index) if isHyväksytty(tulos.valintatila) && tulos.vastaanottotila == Vastaanottotila.kesken => hyvaksyttyJaVastaanottamatta(tulos,index)
        case (tulos, index) if firstVastaanotettu >= 0 && index != firstVastaanotettu && List(Valintatila.varalla, Valintatila.kesken).contains(tulos.valintatila) =>
          // Peru muut varalla/kesken toiveet, jos jokin muu vastaanotettu
          logger.debug("sovellaSijoitteluaKayttanvaKorkeakouluhaunSaantoja valintatila > peruuntunut, ei vastaanotettavissa {}", index)
          tulos.copy(valintatila = Valintatila.peruuntunut, vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa)
        case (tulos, index) =>
          logger.debug("sovellaSijoitteluaKayttanvaKorkeakouluhaunSaantoja ei muutosta {}", index)
          tulos
      }
    } else {
      logger.debug("sovellaSijoitteluaKayttanvaKorkeakouluhaunSaantoja ei käytä sijoittelua")
      tulokset
    }
  }

  private def peruValmistaAlemmatKeskeneräisetJosKäytetäänSijoittelua(hakemusOid: HakemusOid, tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Option[Ohjausparametrit]) = {
    if (haku.sijoitteluJaPriorisointi) {
      val firstFinished = tulokset.indexWhere { t =>
        isHyväksytty(t.valintatila) || t.valintatila == Valintatila.perunut
      }
      tulokset.zipWithIndex.map {
        case (tulos, index) if firstFinished > -1 && index > firstFinished && tulos.valintatila == Valintatila.kesken =>
          logger.debug("peruValmistaAlemmatKeskeneräisetJosKäytetäänSijoittelua valintatila > peruuntunut {}", index)
          tulos.copy(valintatila = Valintatila.peruuntunut)
        case (tulos, _) =>
          tulos
      }
    } else {
      logger.debug("peruValmistaAlemmatKeskeneräisetJosKäytetäänSijoittelua ei käytä sijoittelua")
      tulokset
    }
  }


  private def näytäAlemmatPeruutuneetKeskeneräisinäJosYlemmätKeskeneräisiä(hakemusOid: HakemusOid, tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Option[Ohjausparametrit]) = {
    val firstKeskeneräinen: Int = tulokset.indexWhere (_.valintatila == Valintatila.kesken)
    tulokset.zipWithIndex.map {
      case (tulos, index) if firstKeskeneräinen >= 0 && index > firstKeskeneräinen && tulos.valintatila == Valintatila.peruuntunut =>
        logger.debug("näytäAlemmatPeruutuneetKeskeneräisinäJosYlemmätKeskeneräisiä toKesken {}", index)
        tulos.toKesken
      case (tulos, _) =>
        logger.debug("tulos.valintatila "+tulos.valintatila)
        tulos
    }
  }

  private def näytäJulkaisematontaAlemmatPeruutetutKeskeneräisinä(hakemusOid: HakemusOid, tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Option[Ohjausparametrit]) = {
    val firstJulkaisematon: Int = tulokset.indexWhere (!_.julkaistavissa)
    tulokset.zipWithIndex.map {
      case (tulos, index) if firstJulkaisematon >= 0 && index > firstJulkaisematon && tulos.valintatila == Valintatila.peruuntunut =>
        logger.debug("näytäJulkaisematontaAlemmatPeruutetutKeskeneräisinä toKesken {}", index)
        tulos.toKesken
      case (tulos, _) =>
        logger.debug("näytäJulkaisematontaAlemmatPeruutetutKeskeneräisinä {}", tulos.valintatila)
        tulos
    }
  }

  private def näytäHyväksyttyäJulkaisematontaAlemmistaKorkeinHyvaksyttyOdottamassaYlempiä(hakemusOid: HakemusOid, tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Option[Ohjausparametrit]) = {
    val firstJulkaisematon: Int = tulokset.indexWhere (!_.julkaistavissa)
    var firstChanged = false
    val tuloksetWithIndex = tulokset.zipWithIndex

    tuloksetWithIndex.map {
      case (tulos, index) =>
        val higherJulkaisematon = firstJulkaisematon >= 0 && index > firstJulkaisematon
        if (higherJulkaisematon && tulos.valintatila == Valintatila.peruuntunut) {
          val wasHyvaksyttyJulkaistu = valinnantulosRepository.getViimeisinValinnantilaMuutosHyvaksyttyJaJulkaistuCountHistoriasta(hakemusOid, tulos.hakukohdeOid) > 0
          val existsHigherHyvaksyttyJulkaistu = tuloksetWithIndex.exists(twi => twi._2 < index && twi._1.valintatila.equals(Valintatila.hyväksytty) && twi._1.julkaistavissa)
          if (wasHyvaksyttyJulkaistu && !existsHigherHyvaksyttyJulkaistu && !firstChanged) {
            logger.info("Merkitään aiemmin hyväksyttynä ollut peruuntunut hyväksytyksi koska ylemmän hakutoiveen tuloksia ei ole vielä julkaistu. Index {}, tulos {}", index, tulos)
            firstChanged = true
            tulos.copy(valintatila = Valintatila.hyväksytty, tilanKuvaukset = Map.empty)
          } else {
            logger.debug("näytäHyväksyttyäJulkaisematontaAlemmistaKorkeinHyvaksyttyOdottamassaYlempiä {}", tulos.valintatila)
            tulos
          }
        } else {
          logger.debug("näytäHyväksyttyäJulkaisematontaAlemmistaKorkeinHyvaksyttyOdottamassaYlempiä {}", tulos.valintatila)
          tulos
        }
    }
  }

  private def piilotaKuvauksetKeskeneräisiltä(hakemusOid: HakemusOid, tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Option[Ohjausparametrit]) = {
    tulokset.map {
      case h if h.valintatila == Valintatila.kesken =>
        logger.debug("piilotaKuvauksetKeskeneräisiltä tilankuvaukset empty")
        h.copy(tilanKuvaukset = Map.empty)
      case h =>
        h
    }
  }

  private def näytäVarasijaltaHyväksytytHyväksyttyinäJosVarasijasäännötEiVoimassa(hakemusOid: HakemusOid, tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Option[Ohjausparametrit]) = {
    tulokset.map {
      case tulos if tulos.valintatila == Valintatila.varasijalta_hyväksytty && !ehdollinenVastaanottoMahdollista(ohjausparametrit) =>
        logger.debug("näytäVarasijaltaHyväksytytHyväksyttyinäJosVarasijasäännötEiVoimassa valintatila > hyväksytty")
        tulos.copy(valintatila = Valintatila.hyväksytty, tilanKuvaukset = Map.empty)
      case tulos =>
        tulos
    }
  }


  case class Välitulos(hakemusOid: HakemusOid, tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Option[Ohjausparametrit]) {
    def map(f: (HakemusOid, List[Hakutoiveentulos], Haku, Option[Ohjausparametrit]) => List[Hakutoiveentulos]): Välitulos = {
      Välitulos(hakemusOid, f(hakemusOid, tulokset, haku, ohjausparametrit), haku, ohjausparametrit)
    }
  }

  def crashOrLog[T](msg: String): Option[T] = {
    if (appConfig.settings.lenientSijoitteluntuloksetParsing) {
      logger.warn(msg)
      None
    } else {
      throw new IllegalStateException(msg)
    }
  }
}

object ValintatulosService {
  def toVirkailijaTila(valintatuloksenTilaForHakija: ValintatuloksenTila,
                       hakijanVastaanototHaussa: Option[Set[VastaanottoRecord]],
                       hakukohdeOid: HakukohdeOid): ValintatuloksenTila = {

    def merkittyMyohastyneeksi(v: VastaanottoRecord): Boolean = v.hakukohdeOid == hakukohdeOid && v.action == MerkitseMyohastyneeksi
    if(valintatuloksenTilaForHakija == ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA && !hakijanVastaanototHaussa.exists(_.exists(merkittyMyohastyneeksi))) {
      ValintatuloksenTila.KESKEN
    } else {
      valintatuloksenTilaForHakija
    }
  }
}
