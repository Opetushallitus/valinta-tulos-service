package fi.vm.sade.valintatulosservice

import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import fi.vm.sade.sijoittelu.domain.{ValintatuloksenTila, Valintatulos}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.ohjausparametrit.OhjausparametritService
import fi.vm.sade.valintatulosservice.sijoittelu.SijoittelutulosService
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, ValinnantulosRepository, VastaanottoEvent, VastaanottoRecord}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila.Vastaanottotila
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import slick.dbio.DBIO

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

class VastaanottoService(hakuService: HakuService,
                         hakukohdeRecordService: HakukohdeRecordService,
                         valintatulosService: ValintatulosService,
                         hakijaVastaanottoRepository: HakijaVastaanottoRepository,
                         ohjausparametritService: OhjausparametritService,
                         sijoittelutulosService: SijoittelutulosService,
                         hakemusRepository: HakemusRepository,
                         valinnantulosRepository: ValinnantulosRepository) extends Logging {

  private val statesMatchingInexistentActions = Set(
    Vastaanottotila.kesken,
    Vastaanottotila.ei_vastaanotettu_määräaikana,
    Vastaanottotila.ottanut_vastaan_toisen_paikan
  )

  def vastaanotaVirkailijana(vastaanotot: List[VastaanottoEventDto]): Iterable[VastaanottoResult] = {
    vastaanotot.groupBy(v => (v.hakukohdeOid, v.hakuOid)).flatMap {
      case ((hakukohdeOid, hakuOid), vastaanottoEventDtos) => tallennaVirkailijanHakukohteenVastaanotot(hakukohdeOid, hakuOid, vastaanottoEventDtos)
    }
  }

  def vastaanotaVirkailijanaInTransaction(vastaanotot: List[VastaanottoEventDto]): Try[Unit] = {
    val tallennettavatVastaanotot = generateTallennettavatVastaanototList(vastaanotot)
    logger.info(s"Tallennettavat vastaanotot (${tallennettavatVastaanotot.size} kpl): " + tallennettavatVastaanotot)
    (for {
      _ <- MonadHelper.sequence(tallennettavatVastaanotot.map(v => findHakutoive(v.hakemusOid, v.hakukohdeOid))).right
      hakukohdeRecords <- hakukohdeRecordService.getHakukohdeRecords(tallennettavatVastaanotot.map(_.hakukohdeOid).distinct).right
      _ <- {
        val hakukohdeRecordsByOid = hakukohdeRecords.map(h => h.oid -> h).toMap
        val postCondition = DBIO.sequence(tallennettavatVastaanotot
          .filter(v => v.action == VastaanotaEhdollisesti || v.action == VastaanotaSitovasti)
          .map(v => hakukohdeRecordsByOid(v.hakukohdeOid) match {
            case YPSHakukohde(_, _, koulutuksenAlkamiskausi) =>
              hakijaVastaanottoRepository.findYhdenPaikanSaannonPiirissaOlevatVastaanotot(v.henkiloOid, koulutuksenAlkamiskausi)
            case _ =>
              hakijaVastaanottoRepository.findHenkilonVastaanottoHakukohteeseen(v.henkiloOid, v.hakukohdeOid)
          }))
        hakijaVastaanottoRepository.store(tallennettavatVastaanotot, postCondition)
      }.right
    } yield ()).fold(Failure(_), Success(_))
  }

  private def generateTallennettavatVastaanototList(vastaanotot: List[VastaanottoEventDto]): List[VirkailijanVastaanotto] = {
    val hakuOidit = vastaanotot.map(_.hakuOid).toSet
    logger.info(s"Ollaan tallentamassa ${vastaanotot.size} vastaanottoa, joista löytyi ${hakuOidit.size} eri hakuOidia ($hakuOidit).")
    if (hakuOidit.size > 1) {
      logger.warn("Pitäisi olla vain yksi hakuOid")
    } else if (hakuOidit.isEmpty) {
      logger.warn("Ei löytynyt yhtään hakuOidia, lopetetaan.")
      return Nil
    }

    val henkiloidenVastaanototHauissaByHakuOid: Map[HakuOid, Map[String, List[Valintatulos]]] =
      hakuOidit.map(hakuOid => (hakuOid, findValintatulokset(hakuOid))).toMap

    (for {
      ((hakukohdeOid, hakuOid), vastaanottoEventDtos) <- vastaanotot.groupBy(v => (v.hakukohdeOid, v.hakuOid))
      haunValintatulokset = henkiloidenVastaanototHauissaByHakuOid(hakuOid)
      hakukohteenValintatulokset: Map[String, Option[Valintatulos]] = haunValintatulokset.mapValues(_.find(_.getHakukohdeOid == hakukohdeOid.toString))
      vastaanottoEventDto <- vastaanottoEventDtos if isPaivitys(vastaanottoEventDto, hakukohteenValintatulokset.get(vastaanottoEventDto.henkiloOid).flatten.map(_.getTila))
    } yield {
      VirkailijanVastaanotto(vastaanottoEventDto)
    }).toList.sortWith(VirkailijanVastaanotto.tallennusJarjestys)
  }

  private def tallennaVirkailijanHakukohteenVastaanotot(hakukohdeOid: HakukohdeOid, hakuOid: HakuOid, uudetVastaanotot: List[VastaanottoEventDto]): List[VastaanottoResult] = {
    val hakuEither = hakuService.getHaku(hakuOid)
    val ohjausparametritEither = ohjausparametritService.ohjausparametrit(hakuOid)
    uudetVastaanotot.map(vastaanottoDto => {
      val henkiloOid = vastaanottoDto.henkiloOid
      val hakemusOid = vastaanottoDto.hakemusOid
      val vastaanotto = VirkailijanVastaanotto(vastaanottoDto)
      (for {
        haku <- hakuEither.right
        ohjausparametrit <- ohjausparametritEither.right
        hakemus <- hakemusRepository.findHakemus(hakemusOid).right
        hakukohteet <- hakukohdeRecordService.getHakukohdeRecords(hakemus.toiveet.map(_.oid)).right
        hakutoive <- hakijaVastaanottoRepository.runAsSerialized(10, Duration(5, TimeUnit.MILLISECONDS), s"Storing vastaanotto $vastaanottoDto",
          for {
            sijoittelunTulos <- sijoittelutulosService.tulosVirkailijana(hakuOid, hakemusOid, henkiloOid, ohjausparametrit)
            aiemmatVastaanotot <- aiemmatVastaanotot(hakukohteet, henkiloOid)
            ilmoittautumisenAikaleimat <- valinnantulosRepository.getIlmoittautumisenAikaleimat(henkiloOid)
            hakemuksenTulos = valintatulosService.julkaistavaTulos(
              sijoittelunTulos,
              haku,
              ohjausparametrit,
              checkJulkaisuAikaParametri = true,
              vastaanottoKaudella = oid => { aiemmatVastaanotot.get(oid).map(t => t._1 -> t._2.isDefined) },
              ilmoittautumisenAikaleimat,
              hasHetu = hakemus.henkilotiedot.hasHetu
            )(hakemus)
            hakutoive <- tarkistaHakutoiveenVastaanotettavuusVirkailijana(hakemuksenTulos, hakukohdeOid, vastaanottoDto, aiemmatVastaanotot.get(hakukohdeOid).flatMap(_._2)).fold(DBIO.failed, DBIO.successful)
            _ <- hakutoive.fold[DBIO[Unit]](DBIO.successful())(_ => hakijaVastaanottoRepository.storeAction(vastaanotto))
          } yield hakutoive).right
      } yield hakutoive) match {
        case Right(_) => createVastaanottoResult(200, None, vastaanotto)
        case Left(e: PriorAcceptanceException) => createVastaanottoResult(403, Some(e), vastaanotto)
        case Left(e@(_: IllegalArgumentException | _: IllegalStateException)) => createVastaanottoResult(400, Some(e), vastaanotto)
        case Left(e) => createVastaanottoResult(500, Some(e), vastaanotto)
      }
    })
  }

  private def isPaivitys(virkailijanVastaanotto: VastaanottoEventDto, valintatuloksenTila: Option[ValintatuloksenTila]): Boolean = valintatuloksenTila match {
    case Some(v) => existingTilaMustBeUpdated(v, virkailijanVastaanotto.tila)
    case None => !statesMatchingInexistentActions.contains(virkailijanVastaanotto.tila)
  }

  private def existingTilaMustBeUpdated(currentState: ValintatuloksenTila, newStateFromVirkailijanVastaanotto: Vastaanottotila): Boolean = {
    if (newStateFromVirkailijanVastaanotto == Vastaanottotila.ottanut_vastaan_toisen_paikan || Vastaanottotila.matches(newStateFromVirkailijanVastaanotto, currentState)) {
      return false
    }
    if (newStateFromVirkailijanVastaanotto == Vastaanottotila.kesken && currentState == ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN) {
      // Even if the stored state is OTTANUT_VASTAAN_TOISEN_PAIKAN, UI can see it as "KESKEN" in some cases
      return false
    }
    !Vastaanottotila.matches(newStateFromVirkailijanVastaanotto, currentState)
  }

  private def findValintatulokset(hakuOid: HakuOid): Map[String, List[Valintatulos]] = {
    valintatulosService.findValintaTuloksetForVirkailija(hakuOid).groupBy(_.getHakijaOid)
  }

  private def createVastaanottoResult(statusCode: Int, exception: Option[Throwable], vastaanottoEvent: VastaanottoEvent) = {
    VastaanottoResult(vastaanottoEvent.henkiloOid, vastaanottoEvent.hakemusOid, vastaanottoEvent.hakukohdeOid, Result(statusCode, exception.map(_.getMessage)))
  }

  @Deprecated
  def tarkistaVastaanotettavuus(vastaanotettavaHakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid): Unit = {
    findHakutoive(vastaanotettavaHakemusOid, hakukohdeOid).fold(throw _, x => x)
  }

  private def peruKaikkiVastaanotot(vastaanottoDto: HakijanVastaanottoDto): Unit = {
    hakemusRepository.findHakemus(vastaanottoDto.hakemusOid) match {
      case Right(hakemus) =>
        hakukohdeRecordService.getHakukohdeRecords(hakemus.toiveet.map(_.oid)) match {
          case Right(hakukohteet) =>
            aiemmatVastaanotot(hakukohteet, hakemus.henkiloOid).map(vastaanotot => {
              vastaanotot.map(vastaanotto => {
                hakijaVastaanottoRepository.storeAction(HakijanVastaanotto(hakemus.henkiloOid, hakemus.oid, vastaanotto._1, HakijanVastaanottoAction("Peru")))
              })
            })
        }
    }
  }


  //TODO CACHE KEHIIN!??
  def vastaanotaHakijana(vastaanottoDto: HakijanVastaanottoDto): Either[Throwable, Unit] = {
    if (vastaanottoDto.action.equals(VastaanotaSitovastiPeruAlemmat)) {
      logger.info(s"VASTAANOTA HAKIJANA ACTION ${vastaanottoDto.action.toString}")
      peruKaikkiVastaanotot(vastaanottoDto)
     throw new RuntimeException("VASTAANOTA SITOVASTI JA PERU ALEMMAT SUCCESS!")
    }

    val HakijanVastaanottoDto(hakemusOid, hakukohdeOid, action) = vastaanottoDto
    for {
      hakemus <- hakemusRepository.findHakemus(hakemusOid).right
      hakukohdes <- hakukohdeRecordService.getHakukohdeRecords(hakemus.toiveet.map(_.oid)).right
      hakukohde <- hakukohdes.find(_.oid == hakukohdeOid).toRight(new RuntimeException(s"Hakukohde $hakukohdeOid not in hakemus $hakemusOid")).right
      haku <- hakuService.getHaku(hakukohde.hakuOid).right
      ohjausparametrit <- ohjausparametritService.ohjausparametrit(haku.oid).right
      _ <- hakijaVastaanottoRepository.runAsSerialized(10, Duration(5, TimeUnit.MILLISECONDS), s"Storing vastaanotto $vastaanottoDto",
        for {
          sijoittelunTulos <- sijoittelutulosService.tulosHakijana(haku.oid, hakemusOid, hakemus.henkiloOid, ohjausparametrit)
          aiemmatVastaanotot <- aiemmatVastaanotot(hakukohdes, hakemus.henkiloOid)
          ilmoittautumisenAikaleimat <- valinnantulosRepository.getIlmoittautumisenAikaleimat(hakemus.henkiloOid)
          hakemuksenTulos = valintatulosService.julkaistavaTulos(
            sijoittelunTulos,
            haku,
            ohjausparametrit,
            checkJulkaisuAikaParametri = true,
            vastaanottoKaudella = oid => { aiemmatVastaanotot.get(oid).map(t => t._1 -> t._2.isDefined) },
            ilmoittautumisenAikaleimat,
            hasHetu = hakemus.henkilotiedot.hasHetu
          )(hakemus)
          _ <- tarkistaHakutoiveenVastaanotettavuus(hakemuksenTulos, hakukohdeOid, action).fold(DBIO.failed, DBIO.successful)
          _ <- hakijaVastaanottoRepository.storeAction(HakijanVastaanotto(hakemuksenTulos.hakijaOid, hakemusOid, hakukohdeOid, action))
        } yield ()).right
    } yield ()
  }

  private def findHakutoive(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid): Either[Throwable, Unit] = {
    for {
      hakemus <- valintatulosService.hakemuksentulos(hakemusOid).toRight(new IllegalArgumentException(s"Hakemusta $hakemusOid ei löydy")).right
      _ <- hakemus.findHakutoive(hakukohdeOid).toRight(new IllegalArgumentException(s"Hakutoivetta $hakukohdeOid ei löydy hakemukselta $hakemusOid")).right
    } yield ()
  }

  private def aiemmatVastaanotot(hakukohteet: Seq[HakukohdeRecord],
                                 henkiloOid: String): DBIO[Map[HakukohdeOid, (Kausi, Option[VastaanottoRecord])]] = {
    DBIO.sequence(
      hakukohteet
        .collect({ case YPSHakukohde(_, _, koulutuksenAlkamiskausi) => koulutuksenAlkamiskausi })
        .distinct
        .map(kausi => hakijaVastaanottoRepository.findYhdenPaikanSaannonPiirissaOlevatVastaanotot(henkiloOid, kausi).map(kausi -> _)))
      .map(_.toMap)
      .map(vastaanototByKausi =>
        hakukohteet.collect({
          case YPSHakukohde(oid, _, koulutuksenAlkamiskausi) => oid -> (koulutuksenAlkamiskausi, vastaanototByKausi(koulutuksenAlkamiskausi))
        }).toMap)
  }

  private def tarkistaHakijakohtainenDeadline(hakutoive: Hakutoiveentulos): Either[Throwable, Unit] = hakutoive.vastaanottoDeadline match {
    case Some(d) if d.after(new Date()) =>
      Left(new IllegalArgumentException(
        s"""Hakijakohtaista määräaikaa ${new SimpleDateFormat("dd-MM-yyyy").format(d)}
           |kohteella ${hakutoive.hakukohdeOid} : ${hakutoive.vastaanotettavuustila.toString} ei ole vielä ohitettu.""".stripMargin))
    case _ => Right()
  }

  private def tarkistaHakutoiveenVastaanotettavuus(hakutoive: Hakutoiveentulos, haluttuTila: VastaanottoAction): Either[Throwable, Unit] = {
    val e = new IllegalArgumentException(s"Väärä vastaanotettavuustila kohteella ${hakutoive.hakukohdeOid}: ${hakutoive.vastaanotettavuustila.toString} (yritetty muutos: $haluttuTila)")
    haluttuTila match {
      case Peru | VastaanotaSitovasti if !Vastaanotettavuustila.isVastaanotettavissa(hakutoive.vastaanotettavuustila) => Left(e)
      case VastaanotaEhdollisesti if hakutoive.vastaanotettavuustila != Vastaanotettavuustila.vastaanotettavissa_ehdollisesti => Left(e)
      case _ => Right(())
    }
  }

  private def tarkistaHakutoiveenVastaanotettavuus(hakemuksenTulos: Hakemuksentulos, hakukohdeOid: HakukohdeOid, haluttuTila: VastaanottoAction): Either[Throwable, Hakutoiveentulos] = {
    val missingHakutoive = s"Hakutoivetta $hakukohdeOid ei löydy hakemukselta ${hakemuksenTulos.hakemusOid}"
    for {
      hakutoive <- hakemuksenTulos.findHakutoive(hakukohdeOid).toRight(new IllegalArgumentException(missingHakutoive)).right
      _ <- tarkistaHakutoiveenVastaanotettavuus(hakutoive._1, haluttuTila).right
    } yield hakutoive._1
  }

  private def tarkistaHakutoiveenVastaanotettavuusVirkailijana(vastaanotto: VirkailijanVastaanotto,
                                                               hakutoive: Hakutoiveentulos,
                                                               maybeAiempiVastaanottoKaudella: Option[VastaanottoRecord]): Either[Throwable, Unit] = vastaanotto.action match {
    case VastaanotaEhdollisesti if hakutoive.vastaanotettavuustila != Vastaanotettavuustila.vastaanotettavissa_ehdollisesti =>
      Left(new IllegalArgumentException("Hakutoivetta ei voi ottaa ehdollisesti vastaan"))
    case VastaanotaSitovasti if !Valintatila.hasBeenHyväksytty(hakutoive.valintatila) =>
      logger.warn(s"Could not save $VastaanotaSitovasti because state was ${hakutoive.valintatila} in $vastaanotto")
      Left(new IllegalArgumentException(s"""Ei voi tallentaa vastaanottotietoa, koska hakijalle näytettävä tila on "${hakutoive.valintatila}""""))
    case VastaanotaSitovasti | VastaanotaEhdollisesti =>
      maybeAiempiVastaanottoKaudella match {
        case Some(aiempiVastaanotto) => Left(PriorAcceptanceException(aiempiVastaanotto))
        case None => Right(())
      }
    case MerkitseMyohastyneeksi => tarkistaHakijakohtainenDeadline(hakutoive)
    case Peru => Right(())
    case Peruuta => Right(())
    case Poista => Right(())
  }

  private def tarkistaHakutoiveenVastaanotettavuusVirkailijana(hakemuksentulos: Hakemuksentulos,
                                                               hakukohdeOid: HakukohdeOid,
                                                               vastaanottoDto: VastaanottoEventDto,
                                                               maybeAiempiVastaanottoKaudella: Option[VastaanottoRecord]): Either[Throwable, Option[(Hakutoiveentulos, Int)]] = {
    val missingHakutoive = s"Hakutoivetta $hakukohdeOid ei löydy hakemukselta ${hakemuksentulos.hakemusOid}"
    for {
      hakutoiveJaJarjestysnumero <- hakemuksentulos.findHakutoive(hakukohdeOid).toRight(new IllegalArgumentException(missingHakutoive)).right
      r <- if (isPaivitys(vastaanottoDto, Some(ValintatuloksenTila.valueOf(hakutoiveJaJarjestysnumero._1.virkailijanTilat.vastaanottotila.toString)))) {
        tarkistaHakutoiveenVastaanotettavuusVirkailijana(
          VirkailijanVastaanotto(vastaanottoDto),
          hakutoiveJaJarjestysnumero._1,
          maybeAiempiVastaanottoKaudella
        ).right.map(_ => Some(hakutoiveJaJarjestysnumero)).right
      } else {
        logger.info(s"Vastaanotto event $vastaanottoDto is not an update to hakutoive ${hakutoiveJaJarjestysnumero._1}")
        Right(None).right
      }
    } yield r
  }

}
