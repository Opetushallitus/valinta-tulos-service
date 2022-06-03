package fi.vm.sade.valintatulosservice

import java.time.Instant
import java.util.Date
import fi.vm.sade.sijoittelu.domain.TilankuvauksenTarkenne.{PERUUNTUNUT_EI_VASTAANOTTANUT_MAARAAIKANA, PERUUNTUNUT_VASTAANOTTANUT_TOISEN_PAIKAN_YHDEN_SAANNON_PAIKAN_PIIRISSA}
import fi.vm.sade.sijoittelu.domain.{TilanKuvaukset, TilankuvauksenTarkenne, ValintatuloksenTila, Valintatulos}
import fi.vm.sade.sijoittelu.tulos.dto
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.{HakijaDTO, HakijaPaginationObject, HakutoiveDTO}
import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.domain.Valintatila.isHyväksytty
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, OhjausparametritService}
import fi.vm.sade.valintatulosservice.sijoittelu.{SijoittelutulosService, ValintarekisteriValintatulosDao}
import fi.vm.sade.valintatulosservice.tarjonta.{Haku, HakuService}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, ValinnantulosRepository, VastaanottoRecord, VirkailijaVastaanottoRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila.vastaanottanut
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import fi.vm.sade.valintatulosservice.vastaanotto.VastaanottoUtils.ehdollinenVastaanottoMahdollista
import org.apache.commons.lang3.StringUtils
import slick.dbio.DBIO

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._

class ValintatulosService(valinnantulosRepository: ValinnantulosRepository,
                          sijoittelutulosService: SijoittelutulosService,
                          ohjausparametritService: OhjausparametritService,
                          hakemusRepository: HakemusRepository,
                          virkailijaVastaanottoRepository: VirkailijaVastaanottoRepository,
                          hakuService: HakuService,
                          hakijaVastaanottoRepository: HakijaVastaanottoRepository,
                          hakukohdeRecordService: HakukohdeRecordService,
                          valintatulosDao: ValintarekisteriValintatulosDao)(implicit appConfig: VtsAppConfig) extends Logging {
  def this(valinnantulosRepository: ValinnantulosRepository,
           sijoittelutulosService: SijoittelutulosService,
           hakemusRepository: HakemusRepository,
           virkailijaVastaanottoRepository: VirkailijaVastaanottoRepository,
           ohjausparametritService: OhjausparametritService,
           hakuService: HakuService,
           hakijaVastaanottoRepository: HakijaVastaanottoRepository,
           hakukohdeRecordService: HakukohdeRecordService,
           valintatulosDao: ValintarekisteriValintatulosDao)(implicit appConfig: VtsAppConfig) =
    this(valinnantulosRepository, sijoittelutulosService, ohjausparametritService, hakemusRepository, virkailijaVastaanottoRepository, hakuService, hakijaVastaanottoRepository, hakukohdeRecordService, valintatulosDao)

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

  def hakemuksentulos(hakemusOids: Set[HakemusOid]): List[Hakemuksentulos] =
    hakemusRepository.findHakemuksetByOids(hakemusOids).flatMap(h =>
      hakemuksentulos(h).toList).toList

  def valpasHakemuksienTulokset(hakuOid: HakuOid, hakemukset: ValpasValinnantuloksetKysely): List[Hakemuksentulos] = {
    val hetulla = hakemukset.hetu.flatMap(h => h._2.map(hh => HakemusOidAndHenkiloOid(hh.toString, h._1,hetullinen = true)))
    val ilman = hakemukset.hetuton.flatMap(h => h._2.map(hh => HakemusOidAndHenkiloOid(hh.toString, h._1,hetullinen = false)))

    valpasHakemuksienTuloksetHaulle(hakuOid, (hetulla ++ ilman).toList).toList.flatten
  }

  def valpasHakemuksienTuloksetHaulle(hakuOid: HakuOid, hakemukset: List[HakemusOidAndHenkiloOid]): Option[List[Hakemuksentulos]] = {
    timed("Fetch hakemusten tulos for Valpas in haku: "+ hakuOid, 1000) (
      for {
        haku: Haku <- hakuService.getHaku(hakuOid).right.toOption
        ohjausparametrit: Ohjausparametrit <- ohjausparametritService.ohjausparametrit(hakuOid).right.toOption
      } yield {
        val ilmoittautumisenAikaleimat = timed(s"Ilmoittautumisten aikaleimojen haku haulle $hakuOid", 1000) {
          valinnantulosRepository.runBlocking(valinnantulosRepository.getIlmoittautumisenAikaleimat(hakuOid))
            .groupBy(_._1).mapValues(i => i.map(t => (t._2, t._3)))
        }
        val hakemusToHenkilo: Map[String, List[HakemusOidAndHenkiloOid]] = hakemukset.groupBy(_.hakemusOid)
        val tulokset: List[HakemuksenSijoitteluntulos] = sijoittelutulosService.tuloksetForValpas(hakuOid, hakemukset, ohjausparametrit, vastaanotettavuusVirkailijana = false)
        def tulosToHakemus(t: HakemuksenSijoitteluntulos): List[Hakemus] = {
          val oid = t.hakemusOid
          def toiveToHakutoive(ht: HakutoiveenSijoitteluntulos): Hakutoive = {
            Hakutoive(ht.hakukohdeOid, ht.tarjoajaOid, "","")
          }
          hakemusToHenkilo.get(oid.toString).flatMap(_.headOption).map(henkilo => {
            Hakemus(oid,
              hakuOid,
              henkilo.henkiloOid,
              "fi",
              t.hakutoiveet.map(toiveToHakutoive),
              Henkilotiedot(None,None,hasHetu = henkilo.hetullinen),
              Map.empty)
          }).toList
        }
        fetchTulokset(
          haku,
          () =>
            tulokset.flatMap(tulosToHakemus).toIterator,
          _ => tulokset,
          vastaanottoKaudella = _ => None,
          ilmoittautumisenAikaleimat = ilmoittautumisenAikaleimat
        ).toList
      })
  }

  def hakemuksentulos(h: Hakemus): Option[Hakemuksentulos] = {
    for {
      haku <- hakuService.getHaku(h.hakuOid).right.toOption
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
      ohjausparametrit = sijoittelutulosService.findOhjausparametritFromOhjausparametritService(h.hakuOid)
      tulos = valinnantulosRepository.runBlocking(sijoittelutulosService.tulosHakijana(haku.oid, h.oid, h.henkiloOid, ohjausparametrit))
      hakemus <- fetchTulokset(
        haku,
        () => List(h).iterator,
        _ => Seq(tulos),
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

  def haeTilatHakijoille(hakuOid: HakuOid, valintatapajonoOid: ValintatapajonoOid, hakemusOids: Set[HakemusOid]): Set[TilaHakijalle] = {
    val hakemukset: Seq[Hakemus] = hakemusRepository.findHakemuksetByOids(hakemusOids).toSeq
    val hakijaOidByHakemusOid = hakemukset.map(h => h.oid -> h.henkiloOid).toMap
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
        val ohjausparametrit = sijoittelutulosService.findOhjausparametritFromOhjausparametritService(hakuOid)
        val tulokset = valinnantulosRepository.runBlocking(DBIO.sequence(hakemusOids.map(oid => sijoittelutulosService.tulosHakijana(haku.oid, oid, hakijaOidByHakemusOid(oid), ohjausparametrit)).toSeq))
        val hakemustenTulokset = fetchTulokset(
          haku,
          () => hakemukset.toIterator,
          _ => tulokset,
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
        logger.info(s"Esitiedot haettu, haetaan tulokset haulle $hakuOid")
        fetchTulokset(
          haku,
          () => hakemusRepository.findHakemukset(hakuOid),
          personOidFromHakemusResolver => sijoittelutulosService.hakemustenTulos(hakuOid, None, personOidFromHakemusResolver, haunVastaanotot = haunVastaanotot),
          Some(new PersonOidFromHakemusResolver {
            private val haku = hakuOid
            private val hakijaOidByHakemusOid = timed("personOids from hakemus", 1000)(hakemusRepository.findPersonOidsAtaruFirst(hakuOid))
            override def findBy(hakemusOid: HakemusOid): Option[String] = {
              logger.info(s"Findby hakemus $hakemusOid for haku $haku")
              hakijaOidByHakemusOid.get(hakemusOid)
            }
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

    setValintatuloksetTilat(valintatulokset, mapHakemustenTuloksetByHakemusOid(hakemustenTulokset), haunVastaanotot)
    valintatulokset
  }

  def findValintaTuloksetForVirkailija(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): List[Valintatulos] = {
    val haunVastaanotot: Map[String, Set[VastaanottoRecord]] = virkailijaVastaanottoRepository.findHaunVastaanotot(hakuOid).groupBy(_.henkiloOid)
    val hakemustenTulokset: Iterator[Hakemuksentulos] = hakemustenTulosByHakukohde(hakuOid, hakukohdeOid, Some(haunVastaanotot)) match {
      case Right(x) => x
      case Left(e) => throw e
    }
    val valintatulokset:List[Valintatulos] = valintatulosDao.loadValintatuloksetForHakukohde(hakukohdeOid)

    setValintatuloksetTilat(valintatulokset, mapHakemustenTuloksetByHakemusOid(hakemustenTulokset), haunVastaanotot)
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

  private def findTuloksetForHakemustulos(hakemuksenTulos: Hakemuksentulos): List[Valintatulos] = {
    val hakemusOid = hakemuksenTulos.hakemusOid
    val hakuOid = hakemuksenTulos.hakuOid
    val henkiloOid = hakemuksenTulos.hakijaOid
    val vastaanotot = virkailijaVastaanottoRepository.runBlocking(virkailijaVastaanottoRepository.findHenkilonVastaanototHaussa(henkiloOid, hakuOid))
    val valintatulokset: List[Valintatulos] = valintatulosDao.loadValintatuloksetForHakemus(hakemusOid)

    setValintatuloksetTilat(valintatulokset, Map(hakemusOid -> hakemuksenTulos), Map(henkiloOid -> vastaanotot))
    valintatulokset
  }
  def findValintaTuloksetForVirkailijaByHakemukset(hakemusOids: Set[HakemusOid]): List[Valintatulos] = {
    val hakemuksenTulokset: Seq[Hakemuksentulos] = hakemuksentulos(hakemusOids)
    hakemuksenTulokset.flatMap(findTuloksetForHakemustulos).toList
  }
  def findValintaTuloksetForVirkailijaByHakemus(hakemusOid: HakemusOid): List[Valintatulos] = {
    val hakemuksenTulos = hakemuksentulos(hakemusOid)
      .getOrElse(throw new IllegalArgumentException(s"Not hakemuksen tulos for hakemus $hakemusOid"))
    findTuloksetForHakemustulos(hakemuksenTulos)
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
        })
      }
      hakijaPaginationObject
    } catch {
      case e: Exception =>
        logger.error(s"Sijoittelun hakemuksia ei saatu haulle $hakuOid", e)
        throw new Exception(s"Sijoittelun hakemuksia ei saatu haulle $hakuOid", e)
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

  private def setValintatuloksetTilat(valintatulokset: Seq[Valintatulos],
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
          val tilaHakijalle = ValintatuloksenTila.valueOf(hakutoiveenTulos.vastaanottotila)

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
    logger.info(s"sijoitteluTulokset haettu haulle ${haku.oid}, muodostetaan Hakemuksentulokset")
    hakemukset.map(hakemus => {
      logger.info(s"handling haun ${haku.oid} hakemus ${hakemus.oid}")
      val sijoitteluTulos = sijoitteluTulokset.getOrElse(hakemus.oid, HakemuksenSijoitteluntulos(hakemus.oid, None, Nil))
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
                       ohjausparametrit: Ohjausparametrit,
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
      .map(näytäJulkaisematontaAlemmatPeruuntuneetKeskeneräisinä)
      .map(peruunnutaValmistaAlemmatKeskeneräisetJosKäytetäänSijoittelua)
      .map(näytäVarasijaltaHyväksytytHyväksyttyinäJosVarasijasäännötEiVoimassa)
      .map(sovellaSijoitteluaKayttanvaKorkeakouluhaunSaantoja)
      .map(näytäAlemmatPeruuntuneetHakukohteetKeskeneräisinäJosYlemmätKeskeneräisiä)
      .map(näytäAlemmatPeruuntuneetJonotKeskeneräisinäJosYlemmätKeskeneräisiä)
      .map(piilotaKuvauksetKeskeneräisiltä)
      .map(asetaVastaanotettavuusValintarekisterinPerusteella(vastaanottoKaudella))
      .map(asetaKelaURL)
      .map(piilotaKuvauksetEiJulkaistuiltaValintatapajonoilta)
      .map(piilotaVarasijanumeroJonoiltaJosValintatilaEiVaralla)
      .map(merkitseValintatapajonotPeruuntuneeksiKunEiVastaanottanutMääräaikaanMennessä)
      .map(piilotaEhdollisenHyväksymisenEhdotJonoiltaKunEiEhdollisestiHyväksytty)
      .map(muutaJonojenPeruuntumistenSyytHakukohteissaJoissaOnHyväksyttyTulos)
      .tulokset

    Hakemuksentulos(haku.oid, h.oid, sijoitteluTulos.hakijaOid.getOrElse(h.henkiloOid), ohjausparametrit.vastaanottoaikataulu, lopullisetTulokset)
  }

  private def piilotaEhdollisenHyväksymisenEhdotJonoiltaKunEiEhdollisestiHyväksytty(hakemusOid: HakemusOid, tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Ohjausparametrit): List[Hakutoiveentulos] = {
    tulokset.map {
      tulos =>
        tulos.copy(
          jonokohtaisetTulostiedot = tulos.jonokohtaisetTulostiedot.map {
            jonokohtainenTulostieto =>
              if (jonokohtainenTulostieto.julkaistavissa && Valintatila.isHyväksytty(jonokohtainenTulostieto.valintatila)) {
                jonokohtainenTulostieto
              } else {
                jonokohtainenTulostieto.copy(
                  ehdollisenHyvaksymisenEhto = None,
                  ehdollisestiHyvaksyttavissa = false
                )
              }
          }
        )
    }
  }

  private def merkitseValintatapajonotPeruuntuneeksiKunEiVastaanottanutMääräaikaanMennessä(hakemusOid: HakemusOid, tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Ohjausparametrit): List[Hakutoiveentulos] = {
    tulokset.map {
      tulos =>
        if (tulos.vastaanottotila == Vastaanottotila.ei_vastaanotettu_määräaikana && Valintatila.isHyväksytty(tulos.valintatila)) {
          tulos.copy(
            jonokohtaisetTulostiedot = tulos.jonokohtaisetTulostiedot.map {
              jonokohtainenTulostieto =>
                if (Valintatila.voiTullaHyväksytyksi(jonokohtainenTulostieto.valintatila)) {
                  jonokohtainenTulostieto.copy(
                    valintatila = Valintatila.perunut,
                    pisteet = None,
                    alinHyvaksyttyPistemaara = None,
                    tilanKuvaukset = tilankuvaukset(PERUUNTUNUT_EI_VASTAANOTTANUT_MAARAAIKANA)
                  )
                } else {
                  jonokohtainenTulostieto
                }
            }
          )
        } else {
          tulos
        }
    }
  }

  private def piilotaVarasijanumeroJonoiltaJosValintatilaEiVaralla(hakemusOid: HakemusOid, tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Ohjausparametrit): List[Hakutoiveentulos] = {
    tulokset.map {
      tulos =>
        tulos.copy(
          jonokohtaisetTulostiedot = tulos.jonokohtaisetTulostiedot.map {
            jonokohtainenTulostieto =>
              jonokohtainenTulostieto.copy(
                varasijanumero = if (jonokohtainenTulostieto.valintatila == Valintatila.varalla) {
                  jonokohtainenTulostieto.varasijanumero
                } else {
                  None
                }
              )
          }
        )
    }
  }

  private def piilotaKuvauksetEiJulkaistuiltaValintatapajonoilta(hakemusOid: HakemusOid, tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Ohjausparametrit): List[Hakutoiveentulos] = {
    tulokset.map {
      tulos =>
        tulos.copy(
          jonokohtaisetTulostiedot = tulos.jonokohtaisetTulostiedot.map {
            case jonokohtainenTulostieto if !jonokohtainenTulostieto.julkaistavissa =>
              jonokohtainenTulostieto.copy(
                tilanKuvaukset = None,
                ehdollisenHyvaksymisenEhto = None,
                ehdollisestiHyvaksyttavissa = false
              )
            case jonokohtainenTulostieto =>
              jonokohtainenTulostieto
          }
        )
    }
  }

  private def asetaKelaURL(hakemusOid: HakemusOid, tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Ohjausparametrit): List[Hakutoiveentulos] = {
    val hakukierrosEiOlePäättynyt = !ohjausparametrit.hakukierrosPaattyy.exists(_.isBeforeNow())
    val näytetäänSiirryKelaanURL = ohjausparametrit.naytetaankoSiirryKelaanURL
    val näytetäänKelaURL = if (hakukierrosEiOlePäättynyt && näytetäänSiirryKelaanURL && haku.sallittuKohdejoukkoKelaLinkille) Some(appConfig.settings.kelaURL) else None

    tulokset.map {
      case tulos if vastaanottanut == tulos.vastaanottotila =>
        logger.debug("asetaKelaURL vastaanottanut")
        tulos.copy(kelaURL = näytetäänKelaURL)
      case tulos =>
        tulos
    }
  }

  private def asetaVastaanotettavuusValintarekisterinPerusteella(vastaanottoKaudella: HakukohdeOid => Option[(Kausi, Boolean)])(hakemusOid: HakemusOid, tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Ohjausparametrit): List[Hakutoiveentulos] = {
    def ottanutVastaanToisenPaikan(tulos: Hakutoiveentulos): Hakutoiveentulos =
      tulos.copy(
        vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa,
        vastaanottotila = Vastaanottotila.ottanut_vastaan_toisen_paikan,
        jonokohtaisetTulostiedot = tulos.jonokohtaisetTulostiedot.map(merkitseJonokohtainenTulostietoPerutuksiJosVoiTullaHyväksytyksi)
      )

    def merkitseJonokohtainenTulostietoPerutuksiJosVoiTullaHyväksytyksi(jonokohtainenTulostieto: JonokohtainenTulostieto) = {
      if (Valintatila.voiTullaHyväksytyksi(jonokohtainenTulostieto.valintatila) && jonokohtainenTulostieto.julkaistavissa) {
        jonokohtainenTulostieto.copy(
          valintatila = Valintatila.peruuntunut,
          pisteet = None,
          alinHyvaksyttyPistemaara = None,
          tilanKuvaukset = tilankuvaukset(PERUUNTUNUT_VASTAANOTTANUT_TOISEN_PAIKAN_YHDEN_SAANNON_PAIKAN_PIIRISSA)
        )
      } else {
        jonokohtainenTulostieto
      }
    }

    def peruuntunutOttanutVastaanToisenPaikan(tulos: Hakutoiveentulos): Hakutoiveentulos =
      ottanutVastaanToisenPaikan(if (tulos.julkaistavissa) {

        tulos.copy(
          valintatila = Valintatila.peruuntunut,
          tilanKuvaukset = tilankuvaukset(PERUUNTUNUT_VASTAANOTTANUT_TOISEN_PAIKAN_YHDEN_SAANNON_PAIKAN_PIIRISSA).get
        )
      } else {
        tulos
      })

    def hyvaksyttyTaiVaralla(tulos: Hakutoiveentulos): Boolean = isHyväksytty(tulos.valintatila) || tulos.valintatila == Valintatila.varalla
    val hakutoiveetGroupedByKausi: Map[Option[(Kausi, Boolean)], List[Hakutoiveentulos]] = tulokset.groupBy(tulos => vastaanottoKaudella(tulos.hakukohdeOid))

    val hakutoiveetToOriginalOrder: Ordering[Hakutoiveentulos] = {
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
    }.toList.sorted(hakutoiveetToOriginalOrder)
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

  private def sovellaSijoitteluaKayttanvaKorkeakouluhaunSaantoja(hakemusOid: HakemusOid, tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Ohjausparametrit) = {
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
          tulos.copy(
            valintatila = Valintatila.peruuntunut,
            vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa,
            jonokohtaisetTulostiedot = tulos.jonokohtaisetTulostiedot.map {
              jonokohtainenTulostieto =>
                jonokohtainenTulostieto.copy(
                  valintatila = if (List(Valintatila.varalla, Valintatila.kesken).contains(jonokohtainenTulostieto.valintatila)) {
                    Valintatila.peruuntunut
                  } else {
                    jonokohtainenTulostieto.valintatila
                  }
                )
            }
          )
        case (tulos, index) =>
          logger.debug("sovellaSijoitteluaKayttanvaKorkeakouluhaunSaantoja ei muutosta {}", index)
          tulos
      }
    } else {
      logger.debug("sovellaSijoitteluaKayttanvaKorkeakouluhaunSaantoja ei käytä sijoittelua")
      tulokset
    }
  }

  private def peruunnutaValmistaAlemmatKeskeneräisetJosKäytetäänSijoittelua(hakemusOid: HakemusOid, tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Ohjausparametrit) = {
    if (haku.sijoitteluJaPriorisointi) {
      val firstFinished = tulokset.indexWhere { t =>
        isHyväksytty(t.valintatila) || t.valintatila == Valintatila.perunut
      }
      tulokset.zipWithIndex.map {
        case (tulos, index) if firstFinished > -1 && index > firstFinished && tulos.valintatila == Valintatila.kesken =>
          logger.debug("peruunnutaValmistaAlemmatKeskeneräisetJosKäytetäänSijoittelua valintatila > peruuntunut {}", index)
          tulos.copy(valintatila = Valintatila.peruuntunut)
        case (tulos, _) =>
          tulos
      }
    } else {
      logger.debug("peruunnutaValmistaAlemmatKeskeneräisetJosKäytetäänSijoittelua ei käytä sijoittelua")
      tulokset
    }
  }


  private def näytäAlemmatPeruuntuneetHakukohteetKeskeneräisinäJosYlemmätKeskeneräisiä(hakemusOid: HakemusOid, tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Ohjausparametrit) = {
    if(haku.sijoitteluJaPriorisointi) {
      val firstKeskeneräinen: Int = tulokset.indexWhere (_.valintatila == Valintatila.kesken)
      tulokset.zipWithIndex.map {
        case (tulos, index) if firstKeskeneräinen >= 0 && index > firstKeskeneräinen && tulos.valintatila == Valintatila.peruuntunut =>
          logger.debug("näytäAlemmatPeruuntuneetHakukohteetKeskeneräisinäJosYlemmätKeskeneräisiä toKesken {}", index)
          tulos.toKesken
        case (tulos, _) =>
          logger.debug("tulos.valintatila "+tulos.valintatila)
          tulos
      }
    } else tulokset
  }

  private def näytäAlemmatPeruuntuneetJonotKeskeneräisinäJosYlemmätKeskeneräisiä(hakemusOid: HakemusOid, tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Ohjausparametrit) = {
    if(haku.sijoitteluJaPriorisointi) {
      tulokset.map { hakutoiveenTulos =>
        hakutoiveenTulos.copy(jonokohtaisetTulostiedot = näytäToiveenAlemmatPeruuntuneetJonotKeskeneräisinäJosYlemmätKeskeneräisiä(hakutoiveenTulos))
      }
    }
    else tulokset
  }

  private def näytäToiveenAlemmatPeruuntuneetJonotKeskeneräisinäJosYlemmätKeskeneräisiä(hakutoiveenTulos: Hakutoiveentulos): List[JonokohtainenTulostieto] = {
    val firstKeskeneräinen = hakutoiveenTulos.jonokohtaisetTulostiedot.indexWhere(_.valintatila == Valintatila.kesken)
    hakutoiveenTulos.jonokohtaisetTulostiedot.zipWithIndex.map {
      case (jononTulos, index) if firstKeskeneräinen >= 0 && index > firstKeskeneräinen && jononTulos.valintatila == Valintatila.peruuntunut =>
        logger.debug("näytäToiveenAlemmatPeruuntuneetJonotKeskeneräisinäJosYlemmätKeskeneräisiä toKesken {}", index)
        jononTulos.toKesken
      case (jononTulos, _) =>
        logger.debug("tulos.valintatila " + jononTulos.valintatila)
        jononTulos
    }
  }

  /**
   * Jos kaikki jonot eivät ole vielä sijoittelussa, SijoittelutulosService.jononValintatila asettaa
   * hakutoiveen tilaksi "kesken", mutta jättää jonokohtaiset tulostiedot paikalleen. Koska jonokohtaisia
   * tulostietoja joistakin muista tiloista voidaan haluta näyttää, ei siivota niitä vielä siellä, vaan
   * vasta tapauskohtaisesti täällä.
   *
   * @see [[fi.vm.sade.valintatulosservice.sijoittelu.SijoittelutulosService.jononValintatila]]
   */
  private def onJulkaisematontaAlempiaPeruuntuneitaTaiKeskeneräisiä(ylimmänJulkaisemattomanIndeksi: Int,
                                                                    tulos: Hakutoiveentulos,
                                                                    hakutoiveenIndeksi: Int): Boolean = {
    ylimmänJulkaisemattomanIndeksi >= 0 &&
      hakutoiveenIndeksi > ylimmänJulkaisemattomanIndeksi &&
      List(Valintatila.peruuntunut, Valintatila.kesken).contains(tulos.valintatila)
  }

  private def näytäJulkaisematontaAlemmatPeruuntuneetKeskeneräisinä(hakemusOid: HakemusOid, tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Ohjausparametrit) = {
    if(haku.sijoitteluJaPriorisointi) {
      val firstJulkaisematon: Int = tulokset.indexWhere (!_.julkaistavissa)
      tulokset.zipWithIndex.map {
        case (tulos, index) if onJulkaisematontaAlempiaPeruuntuneitaTaiKeskeneräisiä(firstJulkaisematon, tulos, index) =>
          logger.debug("näytäJulkaisematontaAlemmatPeruuntuneetKeskeneräisinä toKesken {}", index)
          tulos.
            toKesken.
            copy(jonokohtaisetTulostiedot = tulos.jonokohtaisetTulostiedot.map { t =>
              if (t.valintatila == Valintatila.peruuntunut || Valintatila.isHyväksytty(t.valintatila)) {
                t.toKesken
              } else {
                t
              }
            })
        case (tulos, _) =>
          logger.debug("näytäJulkaisematontaAlemmatPeruuntuneetKeskeneräisinä {}", tulos.valintatila)
          tulos
      }
    } else tulokset
  }

  private def näytäHyväksyttyäJulkaisematontaAlemmistaKorkeinHyvaksyttyOdottamassaYlempiä(hakemusOid: HakemusOid, tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Ohjausparametrit) = {
    val firstJulkaisematon: Int = tulokset.indexWhere (!_.julkaistavissa)
    var firstChanged = false
    val tuloksetWithIndex = tulokset.zipWithIndex

    tuloksetWithIndex.map {
      case (tulos, index) =>
        val higherJulkaisematon = firstJulkaisematon >= 0 && index > firstJulkaisematon
        if (higherJulkaisematon && List(Valintatila.peruuntunut, Valintatila.kesken).contains(tulos.valintatila)) {
          val jonoJostaOliHyvaksyttyJulkaistu: Option[ValintatapajonoOid] = valinnantulosRepository.getViimeisinValinnantilaMuutosHyvaksyttyJaJulkaistuJonoOidHistoriasta(hakemusOid, tulos.hakukohdeOid)
          val wasHyvaksyttyJulkaistu = jonoJostaOliHyvaksyttyJulkaistu.nonEmpty
          val existsHigherHyvaksyttyJulkaistu = tuloksetWithIndex.exists(twi => twi._2 < index && twi._1.valintatila.equals(Valintatila.hyväksytty) && twi._1.julkaistavissa)
          if (wasHyvaksyttyJulkaistu && !existsHigherHyvaksyttyJulkaistu && !firstChanged) {
            logger.info(s"Merkitään aiemmin hyväksyttynä ollut ${tulos.valintatila} hyväksytyksi koska ylemmän hakutoiveen tuloksia ei ole vielä julkaistu. Index {}, tulos {}", index, tulos)
            firstChanged = true
            tulos.copy(
              valintatila = Valintatila.hyväksytty,
              tilanKuvaukset = Map.empty,
              jonokohtaisetTulostiedot = tulos.jonokohtaisetTulostiedot.map {
                jonokohtainenTulostieto =>
                  if (jonoJostaOliHyvaksyttyJulkaistu.get == jonokohtainenTulostieto.oid && jonokohtainenTulostieto.valintatila == Valintatila.peruuntunut) {
                    jonokohtainenTulostieto.copy(
                      valintatila = Valintatila.hyväksytty,
                      tilanKuvaukset = None)
                  } else {
                    jonokohtainenTulostieto
                  }
              }
            )
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

  private def piilotaKuvauksetKeskeneräisiltä(hakemusOid: HakemusOid, tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Ohjausparametrit) = {
    tulokset.map {
      case h if h.valintatila == Valintatila.kesken =>
        logger.debug("piilotaKuvauksetKeskeneräisiltä tilankuvaukset empty")
        h.copy(tilanKuvaukset = Map.empty)
      case h =>
        h
    }
  }

  private def näytäVarasijaltaHyväksytytHyväksyttyinäJosVarasijasäännötEiVoimassa(hakemusOid: HakemusOid, tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Ohjausparametrit) = {
    tulokset.map {
      case tulos if tulos.valintatila == Valintatila.varasijalta_hyväksytty && !ehdollinenVastaanottoMahdollista(ohjausparametrit) =>
        logger.debug("näytäVarasijaltaHyväksytytHyväksyttyinäJosVarasijasäännötEiVoimassa valintatila > hyväksytty")
        tulos.copy(valintatila = Valintatila.hyväksytty, tilanKuvaukset = Map.empty)
      case tulos =>
        tulos
    }
  }

  private def peruuntumisenSyyksiHyväksyttyToisessaJonossa(hakemusOid: HakemusOid,
                                                           hakukohdeOid: HakukohdeOid,
                                                           jonokohtainenTulostieto: JonokohtainenTulostieto
                                                          ): JonokohtainenTulostieto = {
    if (jonokohtainenTulostieto.valintatila == Valintatila.peruuntunut && !jonokohtainenTulostieto.tilanKuvaukset.map(_.asJava).contains(TilanKuvaukset.peruuntunutHyvaksyttyToisessaJonossa)) {
      val uusiSyy: Some[Map[String, String]] = Some(TilanKuvaukset.peruuntunutHyvaksyttyToisessaJonossa.asScala.toMap)
      logger.info(s"Vaihdetaan peruuntumisen syyn ilmoittavat tilankuvaukset peruuntuneelta tulokselta " +
        s"hakemuksen $hakemusOid toiveen $hakukohdeOid jonolta ${jonokohtainenTulostieto.oid} . " +
        s"Vanhat tilankuvaukset: ${jonokohtainenTulostieto.tilanKuvaukset} , " +
        s"Uudet tilankuvaukset: $uusiSyy")
      jonokohtainenTulostieto.copy(tilanKuvaukset = uusiSyy)
    } else {
      jonokohtainenTulostieto
    }
  }

  private def muutaJonojenPeruuntumistenSyytHakukohteissaJoissaOnHyväksyttyTulos(hakemusOid: HakemusOid,
                                                                                 tulokset: List[Hakutoiveentulos],
                                                                                 haku: Haku,
                                                                                 ohjausparametrit: Ohjausparametrit) = {
    tulokset.map {
      case tulos if tulos.valintatila == Valintatila.hyväksytty =>
        tulos.copy(jonokohtaisetTulostiedot = tulos.jonokohtaisetTulostiedot.map(peruuntumisenSyyksiHyväksyttyToisessaJonossa(hakemusOid, tulos.hakukohdeOid, _)))
      case tulos =>
        tulos
    }
  }

  case class Välitulos(hakemusOid: HakemusOid, tulokset: List[Hakutoiveentulos], haku: Haku, ohjausparametrit: Ohjausparametrit) {
    def map(f: (HakemusOid, List[Hakutoiveentulos], Haku, Ohjausparametrit) => List[Hakutoiveentulos]): Välitulos = {
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

  private def tilankuvaukset(tarkenne: TilankuvauksenTarkenne): Option[Map[String, String]] = {
    tarkenne.vakioTilanKuvaus().asScala.map(_.asScala.toMap)
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
