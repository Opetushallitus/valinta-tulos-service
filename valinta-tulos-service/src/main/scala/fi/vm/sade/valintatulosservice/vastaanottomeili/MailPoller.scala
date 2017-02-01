package fi.vm.sade.valintatulosservice.vastaanottomeili

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.ValintatulosService
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.json.JsonFormats._
import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, OhjausparametritService}
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, VastaanottoRecord}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila.{Vastaanottotila => _}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{VastaanotaEhdollisesti, VastaanotaSitovasti}

class MailPoller(valintatulosCollection: ValintatulosMongoCollection, valintatulosService: ValintatulosService, hakijaVastaanottoRepository: HakijaVastaanottoRepository, hakuService: HakuService, ohjausparameteritService: OhjausparametritService, val limit: Integer) extends Logging {

  // TODO Change from Mongo to Postgres!

  def etsiHaut: List[String] = {
    val found = (hakuService.kaikkiJulkaistutHaut match {
      case Right(haut) => haut
      case Left(e) => throw e
    }).filter { haku => haku.korkeakoulu }
      .filter { haku =>
        val include = haku.hakuAjat.isEmpty || haku.hakuAjat.exists(hakuaika => hakuaika.hasStarted)
        if (!include) logger.info("Pudotetaan haku " + haku.oid + " koska hakuaika ei alkanut")
        include
      }
      .filter { haku =>
        ohjausparameteritService.ohjausparametrit(haku.oid) match {
          case Right(Some(Ohjausparametrit(_, _, _, Some(hakukierrosPaattyy), _, _, _))) if hakukierrosPaattyy.isBeforeNow =>
            logger.info("Pudotetaan haku " + haku.oid + " koska hakukierros päättynyt " + hakukierrosPaattyy)
            false
          case Right(Some(Ohjausparametrit(_, _, _, _, Some(tulostenJulkistusAlkaa), _, _))) if tulostenJulkistusAlkaa.isAfterNow =>
            logger.info("Pudotetaan haku " + haku.oid + " koska tulosten julkistus alkaa " + tulostenJulkistusAlkaa)
            false
          case Right(None) =>
            logger.warn("Pudotetaan haku " + haku.oid + " koska ei saatu haettua ohjausparametreja")
            false
          case Left(e) =>
            logger.warn("Pudotetaan haku " + haku.oid + " koska ei saatu haettua ohjausparametreja", e)
            false
          case x =>
            true
        }
      }
      .map(_.oid)


    logger.info("haut {}", formatJson(found))
    found
  }

  def searchMailsToSend(limit: Int = this.limit, mailDecorator: MailDecorator): List[Ilmoitus] = {
    val mailCandidates: List[HakemusMailStatus] = pollForMailables(limit = limit)
    val sendableMails: List[Ilmoitus] = mailCandidates.flatMap(mailDecorator.statusToMail)
    logger.info("{} statuses converted to {} mails", mailCandidates.size, sendableMails.size)

    if (sendableMails.nonEmpty || mailCandidates.isEmpty) {
      sendableMails
    } else {
      searchMailsToSend(limit, mailDecorator)
    }
  }


  def pollForMailables(hakuOids: List[String] = etsiHaut, limit: Int = this.limit, excludeHakemusOids: Set[String] = Set.empty): List[HakemusMailStatus] = {
    val candidates: Set[HakemusIdentifier] = valintatulosCollection.pollForCandidates(hakuOids, limit, excludeHakemusOids = excludeHakemusOids)
    logger.info("candidates found {}", formatJson(candidates))

    // onlyIfHasKeskenVastaanottotiloja : if hakemuksenTulos.hakutoiveet.exists(hk => hk.vastaanottotila == Vastaanottotila.kesken)
    val statii: Set[HakemusMailStatus] = for {
      candidateId <- candidates
      hakemuksenTulos <- fetchHakemuksentulos(candidateId)
    } yield {
      val (hakijaOid, hakuOid) = (hakemuksenTulos.hakijaOid, hakemuksenTulos.hakuOid)
      val vastaanotot = hakijaVastaanottoRepository.findVastaanottoHistoryHaussa(hakijaOid, hakuOid)
      val uudetVastaanotot: Set[VastaanottoRecord] = candidateId.lastSent match {
        case Some(lastCheck) => vastaanotot.filter(_.timestamp.compareTo(lastCheck) >= 0)
        case None => vastaanotot
      }
      val vanhatVastaanotot: Set[VastaanottoRecord] = candidateId.lastSent match {
        case Some(lastCheck) => vastaanotot.filter(_.timestamp.before(lastCheck))
        case None => Set()
      }

      mailStatusFor(hakemuksenTulos, uudetVastaanotot, vanhatVastaanotot)
    }
    val mailables = statii.filter(_.anyMailToBeSent).toList
    logger.info("found {} mailables from {} candidates", mailables.size, candidates.size)

    saveMessages(statii)

    if (candidates.nonEmpty && mailables.size < limit) {
      logger.debug("fetching more mailables")
      mailables ++ pollForMailables(hakuOids, limit = limit - mailables.size, excludeHakemusOids = excludeHakemusOids ++ mailables.map(_.hakemusOid).toSet)
    } else {
      mailables
    }
  }

  def saveMessages(statii: Set[HakemusMailStatus]): Unit = {
    for {hakemus <- statii
         hakukohde <- hakemus.hakukohteet} {
      hakukohde.status match {
        case MailStatus.MAILED =>
        // already mailed. why here?
        case MailStatus.NEVER_MAIL =>
          valintatulosCollection.markAsNonMailable(hakemus.hakemusOid, hakukohde.hakukohdeOid, hakukohde.message)
        case _ =>
          valintatulosCollection.addMessage(hakemus, hakukohde, hakukohde.message)
      }
    }
  }

  private def hakukohdeMailStatusFor(hakemusOid: String, hakutoive: Hakutoiveentulos, uudetVastaanotot: Set[VastaanottoRecord], vanhatVastaanotot: Set[VastaanottoRecord]) = {
    val alreadySentVastaanottoilmoitus = valintatulosCollection.alreadyMailed(hakemusOid, hakutoive.hakukohdeOid).isDefined
    val (status, reason, message) =
      if (Vastaanotettavuustila.isVastaanotettavissa(hakutoive.vastaanotettavuustila) && !alreadySentVastaanottoilmoitus) {
        (MailStatus.SHOULD_MAIL, Some(MailReason.VASTAANOTTOILMOITUS), "Vastaanotettavissa (" + hakutoive.valintatila + ")")
      } else if (!Valintatila.isHyväksytty(hakutoive.valintatila) && Valintatila.isFinal(hakutoive.valintatila)) {
        (MailStatus.NEVER_MAIL, None, "Ei hyväksytty (" + hakutoive.valintatila + ")")
      } else {
        val newestSitovaIsNotVastaanotettuByHakija =
          sitovaVastaanottoInHakukohdeThatIsNotVastaanotettuByHakija(hakutoive, uudetVastaanotot)
        if (newestSitovaIsNotVastaanotettuByHakija && vastaanottoMuuttuuEhdollisestaSitovaksi(hakutoive, vanhatVastaanotot, uudetVastaanotot)) {
          (MailStatus.SHOULD_MAIL, Some(MailReason.SITOVAN_VASTAANOTON_ILMOITUS), "Sitova vastaanotto")
        } else {
          val newestEhdollinenNotVastaanotettuByHakija =
            ehdollinenVastaanottoInHakukohdeThatIsNotVastaanotettuByHakija(hakutoive, uudetVastaanotot)

          val atLeastOneOtherEhdollinenVastaanottoCanBeFound =
            (uudetVastaanotot ++ vanhatVastaanotot).filter(_.hakukohdeOid != hakutoive.hakukohdeOid)
              .exists(_.action == VastaanotaEhdollisesti)

          if (newestEhdollinenNotVastaanotettuByHakija && atLeastOneOtherEhdollinenVastaanottoCanBeFound) {
            (MailStatus.SHOULD_MAIL, Some(MailReason.EHDOLLISEN_PERIYTYMISEN_ILMOITUS), "Ehdollinen vastaanotto periytynyt")
          } else {
            if (alreadySentVastaanottoilmoitus) {
              (MailStatus.MAILED, None, "Already mailed")
            } else {
              (MailStatus.NOT_MAILED, None, "Ei vastaanotettavissa (" + hakutoive.valintatila + ")")
            }
          }
        }
      }

    HakukohdeMailStatus(hakutoive.hakukohdeOid, hakutoive.valintatapajonoOid, status,
      reason,
      hakutoive.vastaanottoDeadline, message,
      hakutoive.valintatila,
      hakutoive.vastaanottotila,
      hakutoive.ehdollisestiHyvaksyttavissa)
  }

  def ehdollinenVastaanottoInHakukohdeThatIsNotVastaanotettuByHakija(hakutoive: Hakutoiveentulos, uudetVastaanotot: Set[VastaanottoRecord]): Boolean = {
    uudetVastaanotot.toList.sortBy(_.timestamp).headOption
      .filter(_.hakukohdeOid == hakutoive.hakukohdeOid)
      .filter(vastaanotto => vastaanotto.henkiloOid != vastaanotto.ilmoittaja)
      .exists(_.action == VastaanotaEhdollisesti)
  }

  def vastaanottoMuuttuuEhdollisestaSitovaksi(hakutoive: Hakutoiveentulos, vanhatVastaanotot: Set[VastaanottoRecord], uudetVastaanotot: Set[VastaanottoRecord]) = {
    (vanhatVastaanotot ++ uudetVastaanotot).toList
      .filter(_.hakukohdeOid == hakutoive.hakukohdeOid)
      .sortBy(_.timestamp).reverse
      .tail.headOption
      .exists(_.action == VastaanotaEhdollisesti)
  }

  def sitovaVastaanottoInHakukohdeThatIsNotVastaanotettuByHakija(hakutoive: Hakutoiveentulos, uudetVastaanotot: Set[VastaanottoRecord]): Boolean = {
    uudetVastaanotot.toList
      .filter(_.hakukohdeOid == hakutoive.hakukohdeOid)
      .sortBy(_.timestamp).reverse.headOption
      .filter(vastaanotto => vastaanotto.henkiloOid != vastaanotto.ilmoittaja)
      .exists(_.action == VastaanotaSitovasti)
  }

  private def mailStatusFor(hakemuksenTulos: Hakemuksentulos, uudetVastaanotot: Set[VastaanottoRecord], vanhatVastaanotot: Set[VastaanottoRecord]): HakemusMailStatus = {
    val hakutoiveet = hakemuksenTulos.hakutoiveet
    val mailables = hakutoiveet.map { (hakutoive: Hakutoiveentulos) =>
      hakukohdeMailStatusFor(
        hakemuksenTulos.hakemusOid,
        hakutoive,
        uudetVastaanotot,
        vanhatVastaanotot)
    }
    HakemusMailStatus(hakemuksenTulos.hakijaOid, hakemuksenTulos.hakemusOid, mailables, hakemuksenTulos.hakuOid)
  }

  private def fetchHakemuksentulos(id: HakemusIdentifier): Option[Hakemuksentulos] = {
    try {
      valintatulosService.hakemuksentulos(id.hakemusOid)
    } catch {
      case e: Exception =>
        logger.error("Error fetching data for email polling. Candidate identifier=" + id, e)
        None
    }
  }
}



