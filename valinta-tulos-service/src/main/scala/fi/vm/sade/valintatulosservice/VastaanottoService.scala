package fi.vm.sade.valintatulosservice

import java.text.SimpleDateFormat
import java.util.Date

import fi.vm.sade.sijoittelu.domain.{HakemuksenTila, ValintatuloksenTila, Valintatulos}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.sijoittelu.ValintatulosRepository
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri._
import slick.dbio.{DBIO, SuccessAction}

import scala.collection.JavaConverters._
import scala.util.{Success, Try}


class VastaanottoService(hakuService: HakuService,
                         hakukohdeRecordService: HakukohdeRecordService,
                         vastaanotettavuusService: VastaanotettavuusService,
                         valintatulosService: ValintatulosService,
                         hakijaVastaanottoRepository: HakijaVastaanottoRepository,
                         virkailijaVastaanottoRepository: VirkailijaVastaanottoRepository,
                         valintatulosRepository: ValintatulosRepository) extends Logging {

  private val statesMatchingInexistentActions = Set(
    Vastaanottotila.kesken,
    Vastaanottotila.ei_vastaanotettu_määräaikana,
    Vastaanottotila.ottanut_vastaan_toisen_paikan
  )

  def vastaanotaVirkailijana(vastaanotot: List[VastaanottoEventDto]): Iterable[VastaanottoResult] = {
    vastaanotot.groupBy(v => (v.hakukohdeOid, v.hakuOid)).flatMap {
      case ((hakukohdeOid, hakuOid), vastaanottoEventDtos) => tallennaHakukohteenVastaanotot(hakukohdeOid, hakuOid, vastaanottoEventDtos)
    }
  }

  def vastaanotaVirkailijanaInTransaction(vastaanotot: List[VastaanottoEventDto]): Try[Unit] = {
    val tallennettavatVastaanotot = generateTallennettavatVastaanototList(vastaanotot)
    val vastaanottosToCheckInPostCondition = tallennettavatVastaanotot.filter(v => v.action == VastaanotaEhdollisesti || v.action == VastaanotaSitovasti)
    val postCondition = DBIO.sequence(vastaanottosToCheckInPostCondition.
      map(v => vastaanotettavuusService.tarkistaAiemmatVastaanotot(v.henkiloOid, v.hakukohdeOid, aiempiVastaanotto => SuccessAction())))

    tallennettavatVastaanotot.toStream.map(checkVastaanotettavuusVirkailijana(tarkistaAiemmatVastaanotot = false)).find(_.isFailure) match {
      case Some(failure) => failure.map(_ => ())
      case None => Try {
        tallennettavatVastaanotot.foreach(v => hakukohdeRecordService.getHakukohdeRecord(v.hakukohdeOid))
        hakijaVastaanottoRepository.store(tallennettavatVastaanotot, postCondition)
      }
    }
  }

  private def generateTallennettavatVastaanototList(vastaanotot: List[VastaanottoEventDto]): List[VirkailijanVastaanotto] = {
    (for {
      ((hakukohdeOid, hakuOid), vastaanottoEventDtos) <- vastaanotot.groupBy(v => (v.hakukohdeOid, v.hakuOid))
      hakukohteenValintatulokset = findValintatulokset(hakuOid, hakukohdeOid)
      vastaanottoEventDto <- vastaanottoEventDtos if isPaivitys(vastaanottoEventDto, hakukohteenValintatulokset.get(vastaanottoEventDto.henkiloOid))
    } yield {
      VirkailijanVastaanotto(vastaanottoEventDto)
    }).toList
  }

  private def checkVastaanotettavuusVirkailijana(tarkistaAiemmatVastaanotot: Boolean = true)(vastaanotto: VirkailijanVastaanotto): Try[(Hakutoiveentulos, Int)] = {
    for {
      hakutoive <- findHakutoive(vastaanotto.hakemusOid, vastaanotto.hakukohdeOid)
      _ <- vastaanotto.action match {
        case VastaanotaSitovasti | VastaanotaEhdollisesti if tarkistaAiemmatVastaanotot =>
          Try { hakijaVastaanottoRepository.runBlocking(vastaanotettavuusService.tarkistaAiemmatVastaanotot(vastaanotto.henkiloOid, vastaanotto.hakukohdeOid)) }
        case MerkitseMyohastyneeksi => tarkistaHakijakohtainenDeadline(hakutoive._1)
        case Peru => Success(())
        case VastaanotaSitovasti | VastaanotaEhdollisesti => Success(())
        case Peruuta => Success(())
        case Poista => Success(())
      }
    } yield hakutoive
  }

  private def tallennaHakukohteenVastaanotot(hakukohdeOid: String, hakuOid: String, uudetVastaanotot: List[VastaanottoEventDto]): List[VastaanottoResult] = {
    val hakukohteenValintatulokset = findValintatulokset(hakuOid, hakukohdeOid)
    uudetVastaanotot.map(vastaanottoDto => {
      if (isPaivitys(vastaanottoDto, hakukohteenValintatulokset.get(vastaanottoDto.henkiloOid))) {
        tallenna(VirkailijanVastaanotto(vastaanottoDto)).get
      } else {
        VastaanottoResult(vastaanottoDto.henkiloOid, vastaanottoDto.hakemusOid, vastaanottoDto.hakukohdeOid, Result(200, None))
      }
    })
  }

  private def isPaivitys(virkailijanVastaanotto: VastaanottoEventDto, valintatulos: Option[Valintatulos]): Boolean = valintatulos match {
    case Some(v) => !Vastaanottotila.matches(virkailijanVastaanotto.tila, v.getTila) && virkailijanVastaanotto.tila != Vastaanottotila.ottanut_vastaan_toisen_paikan
    case None => !statesMatchingInexistentActions.contains(virkailijanVastaanotto.tila)
  }

  private def findValintatulokset(hakuOid: String, hakukohdeOid: String): Map[String, Valintatulos] = {
    valintatulosService.findValintaTuloksetForVirkailija(hakuOid, hakukohdeOid).asScala.toList.groupBy(_.getHakijaOid).mapValues(_.head)
  }

  private def tallenna(vastaanotto: VirkailijanVastaanotto): Try[VastaanottoResult] = {
    (for {
      hakutoiveJaJarjestysNumero <- checkVastaanotettavuusVirkailijana(tarkistaAiemmatVastaanotot = true)(vastaanotto)
      _ <- Try {
        hakukohdeRecordService.getHakukohdeRecord(vastaanotto.hakukohdeOid)
        hakijaVastaanottoRepository.store(vastaanotto)
      }
      _ <- Try {
        val hakutoiveenJarjestysNumero = hakutoiveJaJarjestysNumero._2

        val createMissingValintatulos: Unit => Valintatulos = Unit => new Valintatulos(vastaanotto.valintatapajonoOid,
          vastaanotto.hakemusOid, vastaanotto.hakukohdeOid, vastaanotto.henkiloOid, vastaanotto.hakuOid, hakutoiveenJarjestysNumero)

        valintatulosRepository.modifyValintatulos(vastaanotto.hakukohdeOid, vastaanotto.valintatapajonoOid, vastaanotto.hakemusOid, createMissingValintatulos) { valintatulos =>
          valintatulos.setTila(ValintatuloksenTila.valueOf(hakutoiveJaJarjestysNumero._1.vastaanottotila.toString), vastaanotto.action.valintatuloksenTila, vastaanotto.selite, vastaanotto.ilmoittaja)
        }
      }
    } yield {
      createVastaanottoResult(200, None, vastaanotto)
    }).recover {
      case e: PriorAcceptanceException => createVastaanottoResult(403, Some(e), vastaanotto)
      case e @ (_: IllegalArgumentException | _: IllegalStateException) => createVastaanottoResult(400, Some(e), vastaanotto)
      case e: Exception => createVastaanottoResult(500, Some(e), vastaanotto)
    }
  }

  private def createVastaanottoResult(statusCode: Int, exception: Option[Throwable], vastaanottoEvent: VastaanottoEvent) = {
    VastaanottoResult(vastaanottoEvent.henkiloOid, vastaanottoEvent.hakemusOid, vastaanottoEvent.hakukohdeOid, Result(statusCode, exception.map(_.getMessage)))
  }

  @Deprecated
  def tarkistaVastaanotettavuus(vastaanotettavaHakemusOid: String, hakukohdeOid: String): Unit = {
    findHakutoive(vastaanotettavaHakemusOid, hakukohdeOid).get
  }

  def vastaanotaHakijana(vastaanotto: VastaanottoEvent): Try[Unit] = {
    for {
      hakutoive <- findHakutoive(vastaanotto.hakemusOid, vastaanotto.hakukohdeOid).map(_._1)
      _ <- tarkistaHakutoiveenVastaanotettavuus(hakutoive, vastaanotto.action)
    } yield {
      hakukohdeRecordService.getHakukohdeRecord(vastaanotto.hakukohdeOid)
      hakijaVastaanottoRepository.store(vastaanotto)
      valintatulosRepository.modifyValintatulos(vastaanotto.hakukohdeOid,hakutoive.valintatapajonoOid,vastaanotto.hakemusOid,(Unit) => throw new IllegalArgumentException("Valintatulosta ei löydy")) { valintatulos =>
        valintatulos.setTila(ValintatuloksenTila.valueOf(hakutoive.vastaanottotila.toString), vastaanotto.action.valintatuloksenTila, vastaanotto.selite, vastaanotto.ilmoittaja)
      }
    }
  }

  private def findHakutoive(hakemusOid: String, hakukohdeOid: String): Try[(Hakutoiveentulos, Int)] = {
    Try {
      val hakuOid = hakuService.getHakukohde(hakukohdeOid).getOrElse(throw new IllegalArgumentException(s"Tuntematon hakukohde $hakukohdeOid")).hakuOid
      val hakemuksenTulos = valintatulosService.hakemuksentulos(hakuOid, hakemusOid).getOrElse(throw new IllegalArgumentException("Hakemusta ei löydy"))
      hakemuksenTulos.findHakutoive(hakukohdeOid).getOrElse(throw new IllegalArgumentException("Hakutoivetta ei löydy"))
    }
  }

  private def tarkistaHakijakohtainenDeadline(hakutoive: Hakutoiveentulos): Try[Unit] = {
    val vastaanottoDeadline = hakutoive.vastaanottoDeadline
    Try {
      if(vastaanottoDeadline.isDefined && vastaanottoDeadline.get.after(new Date())) {
        throw new IllegalArgumentException(
          s"""Hakijakohtaista määräaikaa ${new SimpleDateFormat("dd-MM-yyyy").format(vastaanottoDeadline)}
             |kohteella ${hakutoive.hakukohdeOid} : ${hakutoive.vastaanotettavuustila.toString} ei ole vielä ohitettu.""".stripMargin)
      }
    }
  }

  private def tarkistaHakutoiveenVastaanotettavuus(hakutoive: Hakutoiveentulos, haluttuTila: VastaanottoAction): Try[Unit] = {
    Try {
      if (List(Peru, VastaanotaSitovasti).contains(haluttuTila) && !List(Vastaanotettavuustila.vastaanotettavissa_ehdollisesti, Vastaanotettavuustila.vastaanotettavissa_sitovasti).contains(hakutoive.vastaanotettavuustila)) {
        throw new IllegalArgumentException("Väärä vastaanotettavuustila kohteella " + hakutoive.hakukohdeOid + ": " + hakutoive.vastaanotettavuustila.toString + " (yritetty muutos: " + haluttuTila + ")")
      }
      if (haluttuTila == VastaanotaEhdollisesti && hakutoive.vastaanotettavuustila != Vastaanotettavuustila.vastaanotettavissa_ehdollisesti) {
        throw new IllegalArgumentException("Väärä vastaanotettavuustila kohteella " + hakutoive.hakukohdeOid + ": " + hakutoive.vastaanotettavuustila.toString + " (yritetty muutos: " + haluttuTila + ")")
      }
    }
  }
}

case class PriorAcceptanceException(aiempiVastaanotto: VastaanottoRecord)
  extends IllegalArgumentException(s"Löytyi aiempi vastaanotto $aiempiVastaanotto")

case class ConflictingAcceptancesException(personOid: String, conflictingVastaanottos: Seq[VastaanottoRecord], conflictDescription: String)
  extends IllegalStateException(s"Hakijalla $personOid useita vastaanottoja $conflictDescription: $conflictingVastaanottos")
