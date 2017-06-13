package fi.vm.sade.valintatulosservice

import fi.vm.sade.sijoittelu.domain.{ValintatuloksenTila, Valintatulos}
import fi.vm.sade.sijoittelu.tulos.dto
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.{HakijaDTO, HakijaPaginationObject}
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
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, VastaanottoRecord, VirkailijaVastaanottoRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila.vastaanottanut
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime

import scala.collection.JavaConverters._

private object HakemustenTulosHakuLock

class ValintatulosService(vastaanotettavuusService: VastaanotettavuusService,
                          sijoittelutulosService: SijoittelutulosService,
                          ohjausparametritService: OhjausparametritService,
                          hakemusRepository: HakemusRepository,
                          virkailijaVastaanottoRepository: VirkailijaVastaanottoRepository,
                          hakuService: HakuService,
                          hakijaVastaanottoRepository: HakijaVastaanottoRepository,
                          hakukohdeRecordService: HakukohdeRecordService,
                          valintatulosDao: ValintarekisteriValintatulosDao,
                          hakijaDTOClient: ValintarekisteriHakijaDTOClient)(implicit appConfig: VtsAppConfig, dynamicAppConfig: VtsDynamicAppConfig) extends Logging {
  def this(vastaanotettavuusService: VastaanotettavuusService,
           sijoittelutulosService: SijoittelutulosService,
           virkailijaVastaanottoRepository: VirkailijaVastaanottoRepository,
           hakuService: HakuService,
           hakijaVastaanottoRepository: HakijaVastaanottoRepository,
           hakukohdeRecordService: HakukohdeRecordService,
           valintatulosDao: ValintarekisteriValintatulosDao,
           hakijaDTOClient: ValintarekisteriHakijaDTOClient )(implicit appConfig: VtsAppConfig, dynamicAppConfig: VtsDynamicAppConfig) =
    this(vastaanotettavuusService, sijoittelutulosService, appConfig.ohjausparametritService, new HakemusRepository(), virkailijaVastaanottoRepository, hakuService, hakijaVastaanottoRepository, hakukohdeRecordService, valintatulosDao, hakijaDTOClient)

  def haunKoulutuksenAlkamiskaudenVastaanototYhdenPaikanSaadoksenPiirissa(hakuOid: HakuOid) : Set[VastaanottoRecord] = {
    hakuJaSenAlkamiskaudenVastaanototYhdenPaikanSaadoksenPiirissa(hakuOid)._2
  }

  private def hakuJaSenAlkamiskaudenVastaanototYhdenPaikanSaadoksenPiirissa(hakuOid: HakuOid): (Haku, Set[VastaanottoRecord]) = {
    val haku = hakuService.getHaku(hakuOid) match {
      case Right(h) => h
      case Left(e) => throw e
    }
    if (haku.yhdenPaikanSaanto.voimassa) {
      hakukohdeRecordService.getHaunKoulutuksenAlkamiskausi(hakuOid) match {
        case Right(kausi) => (haku, virkailijaVastaanottoRepository.findkoulutuksenAlkamiskaudenVastaanottaneetYhdenPaikanSaadoksenPiirissa(kausi))
        case Left(e) => throw new IllegalStateException(s"No koulutuksen alkamiskausi for haku $hakuOid", e)
      }
    } else {
      (haku, Set())
    }
  }

  def hakemuksentulos(hakemusOid: HakemusOid): Option[Hakemuksentulos] = {
    for {
      h <- hakemusRepository.findHakemus(hakemusOid).right.toOption
      haku <- hakuService.getHaku(h.hakuOid).right.toOption
      latestSijoitteluajoId = sijoittelutulosService.findLatestSijoitteluajoId(h.hakuOid)
      hakukohdeRecords <- hakukohdeRecordService.getHakukohdeRecords(h.toiveet.map(_.oid)).right.toOption
      uniqueKaudet <- Right(hakukohdeRecords.filter(_.yhdenPaikanSaantoVoimassa)
        .map(_.koulutuksenAlkamiskausi)).right.toOption
      vastaanototByKausi <- Some(uniqueKaudet.map(kausi => kausi ->
        hakijaVastaanottoRepository.runBlocking(hakijaVastaanottoRepository.findYhdenPaikanSaannonPiirissaOlevatVastaanotot(h.henkiloOid, kausi))).toMap)
      hakemus <- fetchTulokset(
        haku,
        () => List(h).iterator,
        hakijaOidsByHakemusOids => sijoittelutulosService.hakemuksenTulos(haku,
          hakemusOid,
          hakijaOidsByHakemusOids.findBy(hakemusOid),
          sijoittelutulosService.findAikatauluFromOhjausparametritService(h.hakuOid),
          latestSijoitteluajoId).toSeq,
        vastaanottoKaudella = hakukohdeOid => {
          hakukohdeRecords.find(_.oid == hakukohdeOid) match {
            case Some(hakukohde) if hakukohde.yhdenPaikanSaantoVoimassa => {
              val vastaanotto = vastaanototByKausi.get(hakukohde.koulutuksenAlkamiskausi).flatten
              Some(hakukohde.koulutuksenAlkamiskausi, vastaanotto.map(_.henkiloOid).toSet)
            }
            case Some(hakukohde) => None
            case None => throw new RuntimeException(s"Hakukohde $hakukohdeOid not found!")
          }
        }
      ).toSeq.headOption
    } yield hakemus

  }

  def hakemustenTulosByHaku(hakuOid: HakuOid, checkJulkaisuAikaParametri: Boolean): Option[Iterator[Hakemuksentulos]] = {
    val haunVastaanotot = timed("Fetch haun vastaanotot for haku: " + hakuOid, 1000) {
      virkailijaVastaanottoRepository.findHaunVastaanotot(hakuOid).groupBy(_.henkiloOid)
    }
    hakemustenTulosByHaku(hakuOid, Some(haunVastaanotot), checkJulkaisuAikaParametri)
  }

  private def hakukohdeRecordToHakutoiveenKausi(hakukohdes: Seq[HakukohdeRecord])(oid: String): Option[Kausi] = {
    hakukohdes.find(_.oid == oid).filter(_.yhdenPaikanSaantoVoimassa).map(_.koulutuksenAlkamiskausi)
  }
  def haeTilatHakijoille(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid, valintatapajonoOid: ValintatapajonoOid, hakemusOids: Set[HakemusOid]): Set[TilaHakijalle] = {
    val hakemukset = hakemusRepository.findHakemuksetByOids(hakemusOids).toSeq
    val uniqueHakukohdeOids = hakemukset.flatMap(_.toiveet.map(_.oid)).toSet.toSeq
    (hakuService.getHaku(hakuOid), hakukohdeRecordService.getHakukohdeRecords(uniqueHakukohdeOids))  match {
      case (Right(haku), Right(hakukohdes)) =>
        val vastaanototByKausi = timed("kaudenVastaanotot", 1000)({
          virkailijaVastaanottoRepository.findkoulutuksenAlkamiskaudenVastaanottaneetYhdenPaikanSaadoksenPiirissa(
            hakukohdes.filter(_.yhdenPaikanSaantoVoimassa).map(_.koulutuksenAlkamiskausi).toSet)
        })
        val vastaanottoaikataulu = sijoittelutulosService.findAikatauluFromOhjausparametritService(hakuOid)
        val latestSijoitteluajoId = sijoittelutulosService.findLatestSijoitteluajoId(hakuOid)
        val hakemustenTulokset = fetchTulokset(
          haku,
          () => hakemukset.toIterator,
          personOidFromHakemusResolver => hakemusOids.flatMap(hakemusOid => sijoittelutulosService.hakemuksenTulos(haku, hakemusOid, personOidFromHakemusResolver.findBy(hakemusOid), vastaanottoaikataulu, latestSijoitteluajoId)).toSeq,
          vastaanottoKaudella = hakukohdeOid => {
            hakukohdes.find(_.oid == hakukohdeOid) match {
              case Some(hakukohde) if(hakukohde.yhdenPaikanSaantoVoimassa) =>
                val vastaanottaneet: Set[String] = vastaanototByKausi.get(hakukohde.koulutuksenAlkamiskausi).map(_.map(_.henkiloOid)).getOrElse(
                  throw new RuntimeException(s"Missing vastaanotot for kausi ${hakukohde.koulutuksenAlkamiskausi} and hakukohde ${hakukohde.oid}"))
                Some(hakukohde.koulutuksenAlkamiskausi, vastaanottaneet)
              case Some(hakukohde) => None
              case None => throw new RuntimeException(s"Hakukohde $hakukohdeOid is missing")
            }
          }
        )
        hakemustenTulokset.map { hakemuksenTulos =>
          hakemuksenTulos.hakutoiveet.find(_.valintatapajonoOid == valintatapajonoOid).map { hakutoiveenTulos =>
            val tilaHakijalle = ValintatuloksenTila.valueOf(hakutoiveenTulos.vastaanottotila.toString)
            TilaHakijalle(hakemusOid = hakemuksenTulos.hakemusOid,
              hakukohdeOid = hakutoiveenTulos.hakukohdeOid,
              valintatapajonoOid = hakutoiveenTulos.valintatapajonoOid,
              tilaHakijalle = tilaHakijalle.toString)
          }
        }.flatten.toSet
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

  private def hakemustenTulosByHaku(hakuOid: HakuOid, haunVastaanotot: Option[Map[String,Set[VastaanottoRecord]]], checkJulkaisuAikaParametri: Boolean = true): Option[Iterator[Hakemuksentulos]] = {
    timed("Fetch hakemusten tulos for haku: " + hakuOid, 1000) (
      for {
        haku <- hakuService.getHaku(hakuOid).right.toOption
        hakukohdeOids <- hakuService.getHakukohdeOids(hakuOid).right.toOption
        koulutuksenAlkamisKaudet <- timed(s"haun $hakuOid hakukohteiden koulutuksen alkamiskaudet", 1000)(
          hakukohdeRecordService.getHakukohteidenKoulutuksenAlkamiskausi(hakuOid, hakukohdeOids)
            .right.map(_.toMap).right.toOption
        )
        vastaanototByKausi = timed(s"kausien ${koulutuksenAlkamisKaudet.values.flatten.toSet} vastaanotot", 1000)({
          virkailijaVastaanottoRepository.findkoulutuksenAlkamiskaudenVastaanottaneetYhdenPaikanSaadoksenPiirissa(koulutuksenAlkamisKaudet.values.flatten.toSet)
            .map { case (kausi, vastaanotot) => kausi -> vastaanotot.map(_.henkiloOid) }
        })
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
          vastaanottoKaudella = hakukohdeOid => {
            val hk: Option[Option[Kausi]] = koulutuksenAlkamisKaudet.get(hakukohdeOid)
            val result: Option[(Kausi, Set[String])] = hk match {
              case Some(Some(kausi)) =>
                Some(kausi, vastaanototByKausi.getOrElse(kausi, throw new RuntimeException(s"Missing vastaanotot for kausi $kausi and hakukohde $hakukohdeOid")))
              case Some(None) => None
              case None =>
                logger.error(s"Hakukohde $hakukohdeOid is missing while getting hakemusten tulos by haku $hakuOid")
                None // throwing exception here would be better. overhaul needed for test fixtures if exception is thrown here
            }
            result
          }
        )
      }
    )
  }

  def hakemustenTulosByHakukohde(hakuOid: HakuOid,
                                 hakukohdeOid: HakukohdeOid,
                                 hakukohteenVastaanotot: Option[Map[String,Set[VastaanottoRecord]]] = None,
                                 checkJulkaisuAikaParametri: Boolean = true): Either[Throwable, Iterator[Hakemuksentulos]] = {
    val hakemukset = hakemusRepository.findHakemuksetByHakukohde(hakuOid, hakukohdeOid).toSeq //hakemus-mongo
    val uniqueHakukohdeOids = hakemukset.flatMap(_.toiveet.map(_.oid)).distinct
    timed("Fetch hakemusten tulos for haku: "+ hakuOid + " and hakukohde: " + hakukohdeOid, 1000) (
      for {
        haku <- hakuService.getHaku(hakuOid).right //tarjonta
        hakukohdes <- hakukohdeRecordService.getHakukohdeRecords(uniqueHakukohdeOids).right //valintarekisteri/hakukohde
      } yield {
        val vastaanototByKausi = timed("kaudenVastaanotot", 1000)({
          virkailijaVastaanottoRepository.findkoulutuksenAlkamiskaudenVastaanottaneetYhdenPaikanSaadoksenPiirissa(
            hakukohdes.filter(_.yhdenPaikanSaantoVoimassa).map(_.koulutuksenAlkamiskausi).toSet)
            .map { case (kausi, vastaanotot) => kausi -> vastaanotot.map(_.henkiloOid) }
        })
        fetchTulokset(
          haku,
          () => hakemukset.toIterator,
          personOidFromHakemusResolver => sijoittelutulosService.hakemustenTulos(hakuOid, Some(hakukohdeOid), personOidFromHakemusResolver, hakukohteenVastaanotot),
          Some(new PersonOidFromHakemusResolver {
            private lazy val hakijaOidByHakemusOid = timed("personOids from hakemus", 1000)(hakemusRepository.findPersonOids(hakuOid, hakukohdeOid))
            override def findBy(hakemusOid: HakemusOid): Option[String] = hakijaOidByHakemusOid.get(hakemusOid)
          }),
          checkJulkaisuAikaParametri,
          vastaanottoKaudella = hakukohdeOid => {
            val result: Option[(Kausi, Set[String])] = hakukohdes.find(_.oid == hakukohdeOid) match {
              case Some(hakukohde) if hakukohde.yhdenPaikanSaantoVoimassa =>
                Some(hakukohde.koulutuksenAlkamiskausi, vastaanototByKausi.getOrElse(hakukohde.koulutuksenAlkamiskausi,
                  throw new RuntimeException(s"Missing vastaanotot for kausi ${hakukohde.koulutuksenAlkamiskausi} and hakukohde ${hakukohde.oid}")
                ))
              case Some(hakukohde) => None
              case None => throw new RuntimeException(s"Missing hakukohde $hakukohdeOid")
            }
            result
          }
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
    val haunVastaanotot = virkailijaVastaanottoRepository.findHaunVastaanotot(hakuOid).groupBy(_.henkiloOid)
    val hakemustenTulokset = hakemustenTulosByHakukohde(hakuOid, hakukohdeOid, Some(haunVastaanotot)) match {
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

    val (haku, kaudenVastaanotot) = timed(s"Fetch YPS related kauden vastaanotot for haku $hakuOid", 1000) {
      hakuJaSenAlkamiskaudenVastaanototYhdenPaikanSaadoksenPiirissa(hakuOid)
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
      val tilaVirkailijalle: ValintatuloksenTila = paatteleVastaanottotilaVirkailijaaVarten(hakijaOid, hakijanVastaanototHakukohteeseen, haku, kaudenVastaanotot)
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

  @Deprecated //Ei käytetä/sivutusta ei käytetä
  def sijoittelunTulokset(hakuOid: HakuOid, sijoitteluajoId: String, hyvaksytyt: Option[Boolean], ilmanHyvaksyntaa: Option[Boolean], vastaanottaneet: Option[Boolean],
                          hakukohdeOid: Option[List[HakukohdeOid]], count: Option[Int], index: Option[Int]): HakijaPaginationObject = timed(s"Getting sijoittelun tulokset for haku ${hakuOid}") {
    val haunVastaanototByHakijaOid = timed("Fetch haun vastaanotot for haku: " + hakuOid, 1000) {
      virkailijaVastaanottoRepository.findHaunVastaanotot(hakuOid).groupBy(_.henkiloOid)
    }
    val hakemustenTulokset = hakemustenTulosByHaku(hakuOid, Some(haunVastaanototByHakijaOid))
    val hakutoiveidenTuloksetByHakemusOid: Map[HakemusOid, List[Hakutoiveentulos]] = hakemustenTulokset match {
      case Some(hakemustenTulosIterator) => hakemustenTulosIterator.map(h => (h.hakemusOid, h.hakutoiveet)).toMap
      case None => Map()
    }

    val personOidsByHakemusOids: Map[HakemusOid, String] = hakukohdeOid match {
      case Some(oids) => timed("Fetching hakemukset from hakemusRepository for haku and hakukohteet", 1000) (hakemusRepository.findPersonOids(hakuOid, oids))
      case _ => timed("Fetching hakemukset from hakemusRepository for haku", 1000) (hakemusRepository.findPersonOids(hakuOid))
    }
    
    try {
      val haku = hakuService.getHaku(hakuOid) match {
        case Right(h) => h
        case Left(e) => throw e
      }
      val hakijaPaginationObject = sijoittelutulosService.sijoittelunTuloksetWithoutVastaanottoTieto(hakuOid, sijoitteluajoId, hyvaksytyt, ilmanHyvaksyntaa, vastaanottaneet,
        hakukohdeOid, count, index, haunVastaanototByHakijaOid)
      val hakukohdes: Map[HakukohdeOid, Option[Kausi]] = (hakukohdeRecordService.getHaunHakukohdeRecords(hakuOid) match {
        case Right(hks) => hks.map(hk => hk.oid -> {if(hk.yhdenPaikanSaantoVoimassa) Some(hk.koulutuksenAlkamiskausi) else None})
        case Left(e) => throw e
      }).toMap
      val uniqueKaudetInHaku: Set[Kausi] = hakukohdes.values.flatten.toSet
      val kausiToVastaanotto: Map[Kausi, Set[String]] = uniqueKaudetInHaku.map(kausi => kausi ->
        virkailijaVastaanottoRepository.findkoulutuksenAlkamiskaudenVastaanottaneetYhdenPaikanSaadoksenPiirissa(kausi).map(_.henkiloOid)).toMap

      hakijaPaginationObject.getResults.asScala.foreach { hakijaDto =>
        val hakijaOidFromHakemus = personOidsByHakemusOids(HakemusOid(hakijaDto.getHakemusOid))
        hakijaDto.setHakijaOid(hakijaOidFromHakemus)
        val hakijanVastaanotot = haunVastaanototByHakijaOid.get(hakijaDto.getHakijaOid)
        val hakutoiveidenTulokset = hakutoiveidenTuloksetByHakemusOid.getOrElse(HakemusOid(hakijaDto.getHakemusOid), throw new IllegalArgumentException(s"Hakemusta ${hakijaDto.getHakemusOid} ei löydy"))
        val yhdenPaikanSannonHuomioiminen = asetaVastaanotettavuusValintarekisterinPerusteella(hakukohdeOid => {
          hakukohdes.get(hakukohdeOid).flatten.flatMap(kausi => Some(kausi, kausiToVastaanotto.get(kausi).map(_.contains(hakijaDto.getHakijaOid)).getOrElse(false)))
        })(hakutoiveidenTulokset, haku, None)
        hakijaDto.getHakutoiveet.asScala.foreach(palautettavaHakutoiveDto =>
          hakijanVastaanotot match {
            case Some(vastaanottos) =>
              vastaanottos.find(_.hakukohdeOid.toString == palautettavaHakutoiveDto.getHakukohdeOid).foreach(vastaanotto => {
                yhdenPaikanSannonHuomioiminen.find(_.hakukohdeOid.toString == palautettavaHakutoiveDto.getHakukohdeOid).foreach(hakutoiveenOikeaTulos => {
                  palautettavaHakutoiveDto.setVastaanottotieto(fi.vm.sade.sijoittelu.tulos.dto.ValintatuloksenTila.valueOf(hakutoiveenOikeaTulos.vastaanottotila.toString))
                  palautettavaHakutoiveDto.getHakutoiveenValintatapajonot.asScala.foreach(_.setTilanKuvaukset(hakutoiveenOikeaTulos.tilanKuvaukset.asJava))
                })
              })
            case None => palautettavaHakutoiveDto.setVastaanottotieto(dto.ValintatuloksenTila.KESKEN)
          }
        )
      }
      hakijaPaginationObject
    } catch {
      case e: Exception =>
        logger.error(s"Sijoittelun hakemuksia ei saatu haulle $hakuOid", e)
        new HakijaPaginationObject
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

  def streamSijoittelunTulokset(hakuOid: HakuOid, sijoitteluajoId: String, writeResult: HakijaDTO => Unit): Unit = {
    val haunVastaanototByHakijaOid = timed("Fetch haun vastaanotot for haku: " + hakuOid, 1000) {
      virkailijaVastaanottoRepository.findHaunVastaanotot(hakuOid).groupBy(_.henkiloOid)
    }
    val hakemustenTulokset = hakemustenTulosByHaku(hakuOid, Some(haunVastaanototByHakijaOid))
    val hakutoiveidenTuloksetByHakemusOid: Map[HakemusOid, (String, List[Hakutoiveentulos])] = timed(s"Find hakutoiveiden tulokset for haku $hakuOid", 1000) {
      hakemustenTulokset match {
        case Some(hakemustenTulosIterator) => hakemustenTulosIterator.map(h => (h.hakemusOid, (h.hakijaOid, h.hakutoiveet))).toMap
        case None => Map()
      }
    }
    logger.info(s"Found ${hakutoiveidenTuloksetByHakemusOid.keySet.size} hakemus objects for sijoitteluajo $sijoitteluajoId of haku $hakuOid")

    try {
      hakijaDTOClient.processSijoittelunTulokset(hakuOid, sijoitteluajoId, { hakijaDto: HakijaDTO =>
        hakutoiveidenTuloksetByHakemusOid.get(HakemusOid(hakijaDto.getHakemusOid)) match {
          case Some((hakijaOid, hakutoiveidenTulokset)) =>
            hakijaDto.setHakijaOid(hakijaOid)
            populateVastaanottotieto(hakijaDto, hakutoiveidenTulokset)
            writeResult(hakijaDto)
          case None => crashOrLog(s"Hakemus ${hakijaDto.getHakemusOid} not found in hakemusten tulokset for haku $hakuOid")
        }
      })
    } catch {
      case e: Exception =>
        logger.error(s"Sijoitteluajon $sijoitteluajoId hakemuksia ei saatu palautettua haulle $hakuOid", e)
        throw e
    }
  }

  private def populateVastaanottotieto(hakijaDto: HakijaDTO, hakemuksenHakutoiveidenTuloksetVastaanottotiedonKanssa: List[Hakutoiveentulos]): Unit = {
    hakijaDto.getHakutoiveet.asScala.foreach(palautettavaHakutoiveDto =>
      hakemuksenHakutoiveidenTuloksetVastaanottotiedonKanssa.find(_.hakukohdeOid.toString == palautettavaHakutoiveDto.getHakukohdeOid) match {
        case Some(hakutoiveenOikeaTulos) =>
          palautettavaHakutoiveDto.setVastaanottotieto(fi.vm.sade.sijoittelu.tulos.dto.ValintatuloksenTila.valueOf(hakutoiveenOikeaTulos.vastaanottotila.toString))
          palautettavaHakutoiveDto.getHakutoiveenValintatapajonot.asScala.foreach(_.setTilanKuvaukset(hakutoiveenOikeaTulos.tilanKuvaukset.asJava))
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
                    vastaanottoKaudella: HakukohdeOid => Option[(Kausi, Set[String])]): Iterator[Hakemuksentulos] = {
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
      val hakemuksenVastaanototKaudella: HakukohdeOid => Option[(Kausi, Boolean)] = hakukohdeOid =>
        vastaanottoKaudella(hakukohdeOid).map(a => (a._1, a._2.contains(hakemus.henkiloOid)))

      julkaistavaTulos(sijoitteluTulos, haku, ohjausparametrit, checkJulkaisuAikaParametri, hakemuksenVastaanototKaudella, hakemus.henkilotiedot.hasHetu)(hakemus)
    })
  }

  def julkaistavaTulos(sijoitteluTulos: HakemuksenSijoitteluntulos,
                       haku: Haku,
                       ohjausparametrit: Option[Ohjausparametrit],
                       checkJulkaisuAikaParametri: Boolean,
                       vastaanottoKaudella: HakukohdeOid => Option[(Kausi, Boolean)],
                       hasHetu: Boolean
                      )(h:Hakemus): Hakemuksentulos = {
    val tulokset = h.toiveet.map { toive =>
      val hakutoiveenSijoittelunTulos: HakutoiveenSijoitteluntulos = sijoitteluTulos.hakutoiveet.find { t =>
        t.hakukohdeOid == toive.oid
      }.getOrElse(HakutoiveenSijoitteluntulos.kesken(toive.oid, toive.tarjoajaOid))

      Hakutoiveentulos.julkaistavaVersioSijoittelunTuloksesta(hakutoiveenSijoittelunTulos, toive, haku, ohjausparametrit, checkJulkaisuAikaParametri, hasHetu)
    }

    val lopullisetTulokset = Välitulos(tulokset, haku, ohjausparametrit)
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
  private def asetaKelaURL(tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Option[Ohjausparametrit]): List[Hakutoiveentulos] = {
    val hakukierrosEiOlePäättynyt = !(ohjausparametrit.flatMap(_.hakukierrosPaattyy).map(_.isBefore(DateTime.now())).getOrElse(false))
    val näytetäänSiirryKelaanURL = dynamicAppConfig.näytetäänSiirryKelaanURL
    val näytetäänKelaURL = if (hakukierrosEiOlePäättynyt&&näytetäänSiirryKelaanURL&&haku.sallittuKohdejoukkoKelaLinkille) Some(appConfig.settings.kelaURL) else None

    tulokset.map {
      case tulos if vastaanottanut == tulos.vastaanottotila =>
        tulos.copy(kelaURL = näytetäänKelaURL)
      case tulos =>
        tulos
    }
  }
  def tyhjäHakemuksenTulos(hakemusOid: HakemusOid, aikataulu: Option[Vastaanottoaikataulu]) = HakemuksenSijoitteluntulos(hakemusOid, None, Nil)

  private def asetaVastaanotettavuusValintarekisterinPerusteella(vastaanottoKaudella: HakukohdeOid => Option[(Kausi, Boolean)])(tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Option[Ohjausparametrit]): List[Hakutoiveentulos] = {
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
      case (Some((kausi, vastaanotto)), kaudenTulokset) =>
        val ehdollinenVastaanottoTallaHakemuksella = kaudenTulokset.exists(x => Vastaanottotila.ehdollisesti_vastaanottanut == x.vastaanottotila)
        val sitovaVastaanottoTallaHakemuksella = kaudenTulokset.exists(x => vastaanottanut == x.vastaanottotila)
        if (ehdollinenVastaanottoTallaHakemuksella) {
          kaudenTulokset
        } else if (sitovaVastaanottoTallaHakemuksella) {
          kaudenTulokset.map(tulos => if (Vastaanottotila.kesken == tulos.virkailijanTilat.vastaanottotila && hyvaksyttyTaiVaralla(tulos)) {
            peruuntunutOttanutVastaanToisenPaikan(tulos)
          } else {
            tulos
          })
        } else {
          kaudenTulokset.map(tulos => if (Vastaanottotila.kesken == tulos.virkailijanTilat.vastaanottotila && hyvaksyttyTaiVaralla(tulos) && vastaanotto) {
            peruuntunutOttanutVastaanToisenPaikan(tulos)
          } else {
            tulos
          })
        }
      case (None, kaudettomatTulokset) => kaudettomatTulokset
    }.toList.sorted(hakutoiveetToOriginalOrder(tulokset))
  }

  private def paatteleVastaanottotilaVirkailijaaVarten(hakijaOid: String, hakijanVastaanototHakukohteeseen: List[VastaanottoRecord],
                                                       haku: Haku, kaudenVastaanototYpsnPiirissa: Set[VastaanottoRecord]): ValintatuloksenTila = {
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

    if (!haku.yhdenPaikanSaanto.voimassa) {
      tilaSuoraanHakukohteenVastaanottojenPerusteella
    } else if (tilaSuoraanHakukohteenVastaanottojenPerusteella == ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI ||
      tilaSuoraanHakukohteenVastaanottojenPerusteella == ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT ||
      tilaSuoraanHakukohteenVastaanottojenPerusteella == ValintatuloksenTila.PERUNUT ||
      tilaSuoraanHakukohteenVastaanottojenPerusteella == ValintatuloksenTila.PERUUTETTU ||
      tilaSuoraanHakukohteenVastaanottojenPerusteella == ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA
    ) {
      tilaSuoraanHakukohteenVastaanottojenPerusteella
    } else {
      paatteleVastaanottotilaYhdenPaikanSaadoksenMukaanVirkailijaaVarten(hakijaOid, kaudenVastaanototYpsnPiirissa, haku)
    }
  }

  private def paatteleVastaanottotilaYhdenPaikanSaadoksenMukaanVirkailijaaVarten(henkiloOid: String, kaudenVastaanototYpsnPiirissa: Set[VastaanottoRecord],
                                                                                 haku: Haku): ValintatuloksenTila= {
    val henkilonAiemmatVastaanototSamalleKaudelleYpsnPiirissa = kaudenVastaanototYpsnPiirissa.filter(_.henkiloOid == henkiloOid)
    def vastaanottoEriHaussa: Boolean = henkilonAiemmatVastaanototSamalleKaudelleYpsnPiirissa.exists(_.hakuOid != haku.oid)

    if (henkilonAiemmatVastaanototSamalleKaudelleYpsnPiirissa.isEmpty) {
      ValintatuloksenTila.KESKEN
    } else if (vastaanottoEriHaussa) {
      ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN
    } else ValintatuloksenTila.KESKEN
  }

  private def sovellaSijoitteluaKayttanvaKorkeakouluhaunSaantoja(tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Option[Ohjausparametrit]) = {
    if (haku.korkeakoulu && haku.käyttääSijoittelua) {
      val firstVaralla = tulokset.indexWhere(_.valintatila == Valintatila.varalla)
      val firstVastaanotettu = tulokset.indexWhere(_.vastaanottotila == vastaanottanut)
      val firstKesken = tulokset.indexWhere(_.valintatila == Valintatila.kesken)
      val indexedTulokset = tulokset.zipWithIndex
      val firstHyvaksyttyUnderFirstVaralla = if (firstVaralla >= 0) {
        indexedTulokset.indexWhere { case (tulos, index) => index > firstVaralla && Valintatila.isHyväksytty(tulos.valintatila) }
      } else {
        -1
      }

      tulokset.zipWithIndex.map {
        case (tulos, index) if isHyväksytty(tulos.valintatila) && tulos.vastaanottotila == Vastaanottotila.kesken =>
          if (firstVastaanotettu >= 0 && index != firstVastaanotettu) {
            // Peru vastaanotettua paikkaa alemmat hyväksytyt hakutoiveet
            tulos.copy(valintatila = Valintatila.peruuntunut, vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa)
          } else if (index == firstHyvaksyttyUnderFirstVaralla) {
            if (ehdollinenVastaanottoMahdollista(ohjausparametrit)) {
              // Ehdollinen vastaanotto mahdollista
              tulos.copy(vastaanotettavuustila = Vastaanotettavuustila.vastaanotettavissa_ehdollisesti)
            } else {
              // Ehdollinen vastaanotto ei vielä mahdollista, näytetään keskeneräisenä
              tulos.toOdottaaYlempienHakutoiveidenTuloksia
            }
          } else if (firstKesken >= 0 && index > firstKesken) {
            tulos.toOdottaaYlempienHakutoiveidenTuloksia
          } else {
            tulos
          }
        case (tulos, index) if firstVastaanotettu >= 0 && index != firstVastaanotettu && List(Valintatila.varalla, Valintatila.kesken).contains(tulos.valintatila) =>
          // Peru muut varalla/kesken toiveet, jos jokin muu vastaanotettu
          tulos.copy(valintatila = Valintatila.peruuntunut, vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa)
        case (tulos, index) =>
          tulos
      }
    } else {
      tulokset
    }
  }

  private def peruValmistaAlemmatKeskeneräisetJosKäytetäänSijoittelua(tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Option[Ohjausparametrit]) = {
    if (haku.käyttääSijoittelua) {
      val firstFinished = tulokset.indexWhere { t =>
        isHyväksytty(t.valintatila) || List(Valintatila.perunut, Valintatila.peruutettu, Valintatila.peruuntunut).contains(t.valintatila)
      }

      tulokset.zipWithIndex.map {
        case (tulos, index) if haku.käyttääSijoittelua && firstFinished > -1 && index > firstFinished && tulos.valintatila == Valintatila.kesken =>
          tulos.copy(valintatila = Valintatila.peruuntunut)
        case (tulos, _) => tulos
      }
    } else {
      tulokset
    }
  }

  private def näytäAlemmatPeruutuneetKeskeneräisinäJosYlemmätKeskeneräisiä(tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Option[Ohjausparametrit]) = {
    val firstKeskeneräinen = tulokset.indexWhere (_.valintatila == Valintatila.kesken)
    tulokset.zipWithIndex.map {
      case (tulos, index) if firstKeskeneräinen >= 0 && index > firstKeskeneräinen && tulos.valintatila == Valintatila.peruuntunut => tulos.toKesken
      case (tulos, _) => tulos
    }
  }

  private def näytäJulkaisematontaAlemmatPeruutetutKeskeneräisinä(tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Option[Ohjausparametrit]) = {
    val firstJulkaisematon = tulokset.indexWhere (!_.julkaistavissa)
    tulokset.zipWithIndex.map {
      case (tulos, index) if firstJulkaisematon >= 0 && index > firstJulkaisematon && tulos.valintatila == Valintatila.peruuntunut => tulos.toKesken
      case (tulos, _) => tulos
    }
  }

  private def piilotaKuvauksetKeskeneräisiltä(tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Option[Ohjausparametrit]) = {
    tulokset.map {
      case h if h.valintatila == Valintatila.kesken => h.copy(tilanKuvaukset = Map.empty)
      case h => h
    }
  }

  private def näytäVarasijaltaHyväksytytHyväksyttyinäJosVarasijasäännötEiVoimassa(tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Option[Ohjausparametrit]) = {
    tulokset.map {
      case tulos if tulos.valintatila == Valintatila.varasijalta_hyväksytty && !ehdollinenVastaanottoMahdollista(ohjausparametrit) =>
        tulos.copy(valintatila = Valintatila.hyväksytty, tilanKuvaukset = Map.empty)
      case tulos => tulos
    }
  }

  private def ehdollinenVastaanottoMahdollista(ohjausparametrit: Option[Ohjausparametrit]): Boolean = {
    val now: DateTime = new DateTime()
    val varasijaSaannotVoimassa = ohjausparametrit.flatMap(_.varasijaSaannotAstuvatVoimaan).fold(true)(_.isBefore(now))
    val kaikkiJonotSijoittelussa = ohjausparametrit.flatMap(_.kaikkiJonotSijoittelussa).fold(true)(_.isBefore(now))
    varasijaSaannotVoimassa && kaikkiJonotSijoittelussa
  }

  case class Välitulos(tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Option[Ohjausparametrit]) {
    def map(f: (List[Hakutoiveentulos], Haku, Option[Ohjausparametrit]) => List[Hakutoiveentulos]) = {
      Välitulos(f(tulokset, haku, ohjausparametrit), haku, ohjausparametrit)
    }
  }

  private def crashOrLog[T](msg: String): Option[T] = {
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

    def merkittyMyohastyneeksi(v: VastaanottoRecord) = v.hakukohdeOid == hakukohdeOid && v.action == MerkitseMyohastyneeksi
    if(valintatuloksenTilaForHakija == ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA && !hakijanVastaanototHaussa.exists(_.exists(merkittyMyohastyneeksi))) {
      ValintatuloksenTila.KESKEN
    } else {
      valintatuloksenTilaForHakija
    }
  }
}
