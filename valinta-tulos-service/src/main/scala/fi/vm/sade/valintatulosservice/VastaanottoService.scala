package fi.vm.sade.valintatulosservice

import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

import fi.vm.sade.sijoittelu.domain.{ValintatuloksenTila, Valintatulos}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.ohjausparametrit.OhjausparametritService
import fi.vm.sade.valintatulosservice.sijoittelu.{SijoittelutulosService, ValintatulosRepository}
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, VastaanottoEvent, VastaanottoRecord}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila.Vastaanottotila
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import slick.dbio.{DBIO, SuccessAction}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.{Failure, Try}

class VastaanottoService(hakuService: HakuService,
                         hakukohdeRecordService: HakukohdeRecordService,
                         vastaanotettavuusService: VastaanotettavuusService,
                         valintatulosService: ValintatulosService,
                         hakijaVastaanottoRepository: HakijaVastaanottoRepository,
                         ohjausparametritService: OhjausparametritService,
                         sijoittelutulosService: SijoittelutulosService,
                         hakemusRepository: HakemusRepository,
                         valintatulosRepository: ValintatulosRepository) extends Logging {

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
    val vastaanottosToCheckInPostCondition = tallennettavatVastaanotot.filter(v => v.action == VastaanotaEhdollisesti || v.action == VastaanotaSitovasti)
    val postCondition = DBIO.sequence(vastaanottosToCheckInPostCondition.
      map(v => vastaanotettavuusService.tarkistaAiemmatVastaanotot(v.henkiloOid, v.hakukohdeOid, aiempiVastaanotto => SuccessAction())))

    tallennettavatVastaanotot.toStream.map(vastaanotto => findHakutoive(vastaanotto.hakemusOid, vastaanotto.hakukohdeOid)).find(_.isFailure) match {
      case Some(failure) => failure.map(_ => ())
      case None => Try {
        tallennettavatVastaanotot.foreach(v => hakukohdeRecordService.getHakukohdeRecord(v.hakukohdeOid) match {
          case Right(h) => h
          case Left(e) => throw e
        })
        hakijaVastaanottoRepository.store(tallennettavatVastaanotot, postCondition)
      }
    }
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

    val henkiloidenVastaanototHauissaByHakuOid: Map[String, Map[String, List[Valintatulos]]] =
      hakuOidit.map(hakuOid => (hakuOid, findValintatulokset(hakuOid))).toMap

    (for {
      ((hakukohdeOid, hakuOid), vastaanottoEventDtos) <- vastaanotot.groupBy(v => (v.hakukohdeOid, v.hakuOid))
      haunValintatulokset = henkiloidenVastaanototHauissaByHakuOid(hakuOid)
      hakukohteenValintatulokset: Map[String, Option[Valintatulos]] = haunValintatulokset.mapValues(_.find(_.getHakukohdeOid == hakukohdeOid))
      vastaanottoEventDto <- vastaanottoEventDtos if isPaivitys(vastaanottoEventDto, hakukohteenValintatulokset.get(vastaanottoEventDto.henkiloOid).flatten.map(_.getTila))
    } yield {
      VirkailijanVastaanotto(vastaanottoEventDto)
    }).toList.sortWith(VirkailijanVastaanotto.tallennusJarjestys)
  }

  private def tallennaVirkailijanHakukohteenVastaanotot(hakukohdeOid: String, hakuOid: String, uudetVastaanotot: List[VastaanottoEventDto]): List[VastaanottoResult] = {
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
        hakukohdes <- hakukohdeRecordService.getHakukohdeRecords(hakemus.toiveet.map(_.oid).toSeq).right

        hakutoive <- hakijaVastaanottoRepository.runAsSerialized(10, Duration(5, TimeUnit.MILLISECONDS), s"Storing vastaanotto $vastaanottoDto",
          for {
            sijoittelunTulos <- sijoittelutulosService.latestSijoittelunTulosVirkailijana(hakuOid, henkiloOid, hakemusOid, ohjausparametrit.flatMap(_.vastaanottoaikataulu))
            maybeAiempiVastaanottoKaudella <- aiempiVastaanottoKaudella(hakukohdes.find(_.oid == hakukohdeOid).get, henkiloOid)
            hakemuksenTulos = valintatulosService.julkaistavaTulos(sijoittelunTulos, haku, ohjausparametrit, true,
              vastaanottoKaudella = hakukohdeOid => {
                hakukohdes.find(_.oid == hakukohdeOid).filter(_.yhdenPaikanSaantoVoimassa).flatMap(hakukohde => Some(hakukohde.koulutuksenAlkamiskausi, maybeAiempiVastaanottoKaudella.flatten.isDefined))
              })(hakemus)
            hakutoive <- tarkistaHakutoiveenVastaanotettavuusVirkailijana(hakemuksenTulos, hakukohdeOid, vastaanottoDto, maybeAiempiVastaanottoKaudella).fold(DBIO.failed, DBIO.successful)
            _ <- hakutoive.fold[DBIO[Unit]](DBIO.successful())(_ => hakijaVastaanottoRepository.storeAction(vastaanotto))
          } yield hakutoive).right
        _ <- hakutoive.fold[Either[Throwable, Unit]](Right(())) {
          case (hakutoive, hakutoiveenJarjestysnumero) =>
            valintatulosRepository.createIfMissingAndModifyValintatulos(
              hakukohdeOid,
              vastaanotto.valintatapajonoOid,
              vastaanotto.hakemusOid,
              vastaanotto.henkiloOid,
              vastaanotto.hakuOid,
              hakutoiveenJarjestysnumero,
              valintatulos =>
                valintatulos.setTila(
                  ValintatuloksenTila.valueOf(hakutoive.vastaanottotila.toString),
                  vastaanotto.action.valintatuloksenTila,
                  vastaanotto.selite,
                  vastaanotto.ilmoittaja
                )
            )
        }.right
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

  private def findValintatulokset(hakuOid: String): Map[String, List[Valintatulos]] = {
    valintatulosService.findValintaTuloksetForVirkailija(hakuOid).asScala.toList.groupBy(_.getHakijaOid)
  }

  private def createVastaanottoResult(statusCode: Int, exception: Option[Throwable], vastaanottoEvent: VastaanottoEvent) = {
    VastaanottoResult(vastaanottoEvent.henkiloOid, vastaanottoEvent.hakemusOid, vastaanottoEvent.hakukohdeOid, Result(statusCode, exception.map(_.getMessage)))
  }

  @Deprecated
  def tarkistaVastaanotettavuus(vastaanotettavaHakemusOid: String, hakukohdeOid: String): Unit = {
    findHakutoive(vastaanotettavaHakemusOid, hakukohdeOid).get
  }
  def vastaanotaHakijana(vastaanotto: VastaanottoEvent): Either[Throwable, Unit] = {
    val VastaanottoEvent(henkiloOid, hakemusOid, hakukohdeOid, _, _, _) = vastaanotto
    for {
      hakemus <- hakemusRepository.findHakemus(hakemusOid).right
      hakukohdes <- hakukohdeRecordService.getHakukohdeRecords(hakemus.toiveet.map(_.oid)).right
      hakukohde <- (hakukohdes.find(_.oid == hakukohdeOid) match { case Some(hk) => Right(hk) case None => Left(new RuntimeException(s"Hakukohde $hakukohdeOid not in hakemus $hakemusOid"))}).right
      haku <- hakuService.getHaku(hakukohde.hakuOid).right
      ohjausparametrit <- ohjausparametritService.ohjausparametrit(haku.oid).right
      hakutoive <- hakijaVastaanottoRepository.runAsSerialized(10, Duration(5, TimeUnit.MILLISECONDS), s"Storing vastaanotto $vastaanotto",
        for {
          sijoittelunTulos <- sijoittelutulosService.latestSijoittelunTulos(haku.oid, henkiloOid, hakemusOid, ohjausparametrit.flatMap(_.vastaanottoaikataulu))
          maybeAiempiVastaanottoKaudella <- aiempiVastaanottoKaudella(hakukohde, henkiloOid)
          hakemuksenTulos = valintatulosService.julkaistavaTulos(sijoittelunTulos, haku, ohjausparametrit, true,
            vastaanottoKaudella = hakukohdeOid => {
              hakukohdes.find(_.oid == hakukohdeOid).filter(_.yhdenPaikanSaantoVoimassa).flatMap(hakukohde => Some(hakukohde.koulutuksenAlkamiskausi, maybeAiempiVastaanottoKaudella.flatten.isDefined))
            })(hakemus)
          hakutoive <- tarkistaHakutoiveenVastaanotettavuus(hakemuksenTulos, hakukohdeOid, vastaanotto.action).fold(DBIO.failed, DBIO.successful)
          _ <- hakijaVastaanottoRepository.storeAction(vastaanotto)
        } yield hakutoive).right
      _ <- valintatulosRepository.modifyValintatulos(
        hakukohdeOid,
        hakutoive.valintatapajonoOid,
        hakemusOid,
        valintatulos =>
          valintatulos.setTila(
            ValintatuloksenTila.valueOf(hakutoive.vastaanottotila.toString),
            vastaanotto.action.valintatuloksenTila,
            vastaanotto.selite,
            vastaanotto.ilmoittaja
          )
      ).right
    } yield ()
  }

  private def findHakutoive(hakemusOid: String, hakukohdeOid: String): Try[(Hakutoiveentulos, Int)] = {
    Try {
      val hakemuksenTulos = valintatulosService.hakemuksentulos(hakemusOid).getOrElse(throw new IllegalArgumentException("Hakemusta ei löydy"))
      hakemuksenTulos.findHakutoive(hakukohdeOid).getOrElse(throw new IllegalArgumentException("Hakutoivetta ei löydy"))
    }
  }
  private def aiempiVastaanottoKausilla(hakukohdes: Seq[HakukohdeRecord],
                                        henkiloOid: String): Map[Kausi, DBIO[Option[VastaanottoRecord]]] = {
    hakukohdes.filter(_.yhdenPaikanSaantoVoimassa).map(_.koulutuksenAlkamiskausi).distinct.map(kausi =>
      kausi -> hakijaVastaanottoRepository.findYhdenPaikanSaannonPiirissaOlevatVastaanotot(henkiloOid, kausi)).toMap
  }
  private def aiempiVastaanottoKaudella(hakukohde: HakukohdeRecord,
                                        henkiloOid: String): DBIO[Option[Option[VastaanottoRecord]]] = {
    if (hakukohde.yhdenPaikanSaantoVoimassa) {
      hakijaVastaanottoRepository.findYhdenPaikanSaannonPiirissaOlevatVastaanotot(henkiloOid, hakukohde.koulutuksenAlkamiskausi).map(Some(_))
    } else {
      DBIO.successful(None)
    }
  }

  private def tarkistaHakijakohtainenDeadline(hakutoive: Hakutoiveentulos): Either[Throwable, Unit] = {
    val vastaanottoDeadline = hakutoive.vastaanottoDeadline
    if (vastaanottoDeadline.isDefined && vastaanottoDeadline.get.after(new Date())) {
      Left(new IllegalArgumentException(
        s"""Hakijakohtaista määräaikaa ${new SimpleDateFormat("dd-MM-yyyy").format(vastaanottoDeadline)}
            |kohteella ${hakutoive.hakukohdeOid} : ${hakutoive.vastaanotettavuustila.toString} ei ole vielä ohitettu.""".stripMargin))
    } else {
      Right(())
    }
  }

  private def tarkistaHakutoiveenVastaanotettavuus(hakutoive: Hakutoiveentulos, haluttuTila: VastaanottoAction): Either[Throwable, Unit] = {
    val e = new IllegalArgumentException(s"Väärä vastaanotettavuustila kohteella ${hakutoive.hakukohdeOid}: ${hakutoive.vastaanotettavuustila.toString} (yritetty muutos: $haluttuTila)")
    haluttuTila match {
      case Peru | VastaanotaSitovasti if !Vastaanotettavuustila.isVastaanotettavissa(hakutoive.vastaanotettavuustila) => Left(e)
      case VastaanotaEhdollisesti if hakutoive.vastaanotettavuustila != Vastaanotettavuustila.vastaanotettavissa_ehdollisesti => Left(e)
      case _ => Right(())
    }
  }

  private def tarkistaHakutoiveenVastaanotettavuus(hakemuksenTulos: Hakemuksentulos, hakukohdeOid: String, haluttuTila: VastaanottoAction): Either[Throwable, Hakutoiveentulos] = {
    val missingHakutoive = s"Hakutoivetta $hakukohdeOid ei löydy hakemukselta ${hakemuksenTulos.hakemusOid}"
    for {
      hakutoive <- hakemuksenTulos.findHakutoive(hakukohdeOid).toRight(new IllegalArgumentException(missingHakutoive)).right
      _ <- tarkistaHakutoiveenVastaanotettavuus(hakutoive._1, haluttuTila).right
    } yield hakutoive._1
  }

  private def tarkistaHakutoiveenVastaanotettavuusVirkailijana(vastaanotto: VirkailijanVastaanotto,
                                                               hakutoive: Hakutoiveentulos,
                                                               maybeAiempiVastaanottoKaudella: Option[Option[VastaanottoRecord]]): Either[Throwable, Unit] = vastaanotto.action match {
    case VastaanotaEhdollisesti if hakutoive.vastaanotettavuustila != Vastaanotettavuustila.vastaanotettavissa_ehdollisesti =>
      Left(new IllegalArgumentException("Hakutoivetta ei voi ottaa ehdollisesti vastaan"))
    case VastaanotaSitovasti if !Valintatila.hasBeenHyväksytty(hakutoive.valintatila) =>
      logger.warn(s"Could not save $VastaanotaSitovasti because state was ${hakutoive.valintatila} in $vastaanotto")
      Left(new IllegalArgumentException(s"""Ei voi tallentaa vastaanottotietoa, koska hakijalle näytettävä tila on "${hakutoive.valintatila}""""))
    case VastaanotaSitovasti | VastaanotaEhdollisesti =>
      maybeAiempiVastaanottoKaudella match {
        case Some(Some(aiempiVastaanotto)) => Left(PriorAcceptanceException(aiempiVastaanotto))
        case _ => Right(())
      }
    case MerkitseMyohastyneeksi => tarkistaHakijakohtainenDeadline(hakutoive)
    case Peru => Right(())
    case Peruuta => Right(())
    case Poista => Right(())
  }

  private def tarkistaHakutoiveenVastaanotettavuusVirkailijana(hakemuksentulos: Hakemuksentulos, hakukohdeOid: String,
                                                               vastaanottoDto: VastaanottoEventDto,
                                                               maybeAiempiVastaanottoKaudella: Option[Option[VastaanottoRecord]]): Either[Throwable, Option[(Hakutoiveentulos, Int)]] = {
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
