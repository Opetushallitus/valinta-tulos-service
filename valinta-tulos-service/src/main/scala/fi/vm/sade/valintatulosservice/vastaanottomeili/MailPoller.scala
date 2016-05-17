package fi.vm.sade.valintatulosservice.vastaanottomeili

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.ValintatulosService
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.json.JsonFormats._
import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, OhjausparametritService}
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import org.joda.time.DateTime

class MailPoller(valintatulosCollection: ValintatulosMongoCollection, valintatulosService: ValintatulosService, hakuService: HakuService, ohjausparameteritService: OhjausparametritService, val limit: Integer) extends Logging {
  def etsiHaut: List[String] = {
    val found = hakuService.kaikkiJulkaistutHaut
      .filter{haku => haku.korkeakoulu}
      .filter{haku =>
        val include = haku.hakuAjat.isEmpty || haku.hakuAjat.exists(hakuaika => hakuaika.hasStarted)
        if (!include) logger.debug("Pudotetaan haku " + haku.oid + " koska hakuaika ei alkanut")
        include
       }
      .filter{haku =>
        ohjausparameteritService.ohjausparametrit(haku.oid) match {
          case Some(Ohjausparametrit(_, _, _, Some(hakuKierrosPaattyy))) =>
            val include = new DateTime().isBefore(hakuKierrosPaattyy)
            if (!include) logger.debug("Pudotetaan haku " + haku.oid + " koska hakukierros päättyy " + hakuKierrosPaattyy)
            include
          case x =>
            true
        }
      }
      .map(_.oid)


    logger.info("haut {}", formatJson(found))
    found
  }

  def searchMailsToSend(limit: Int = this.limit, mailDecorator: MailDecorator): List[VastaanotettavuusIlmoitus] = {
    val mailCandidates: List[HakemusMailStatus] = pollForMailables(limit = limit)
    val sendableMails: List[VastaanotettavuusIlmoitus] = mailCandidates.flatMap(mailDecorator.statusToMail)
    logger.info("{} statuses converted to {} mails", mailCandidates.size, sendableMails.size)

    if (sendableMails.size > 0 || mailCandidates.isEmpty) {
      sendableMails
    } else {
      searchMailsToSend(limit, mailDecorator)
    }
  }



  def pollForMailables(hakuOids: List[String] = etsiHaut, limit: Int = this.limit, excludeHakemusOids: Set[String] = Set.empty): List[HakemusMailStatus] = {
    val candidates: Set[HakemusIdentifier] = valintatulosCollection.pollForCandidates(hakuOids, limit, excludeHakemusOids = excludeHakemusOids)
    logger.info("candidates found {}", formatJson(candidates))

    val statii: Set[HakemusMailStatus] = for {
      candidateId <- candidates
      hakemuksenTulos <- fetchHakemuksentulos(candidateId) if hakemuksenTulos.hakutoiveet.exists(hk => hk.vastaanottotila == Vastaanottotila.kesken)
    } yield {
      mailStatusFor(hakemuksenTulos)
    }
    val mailables = statii.filter(_.anyMailToBeSent).toList
    logger.info("found {} mailables from {} candidates", mailables.size, candidates.size)

    saveMessages(statii)

    if (candidates.size > 0 && mailables.size < limit) {
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
          valintatulosCollection.markAsNonMailable(hakemus, hakukohde, hakukohde.message)
        case _ =>
          valintatulosCollection.addMessage(hakemus, hakukohde, hakukohde.message)
      }
    }
  }

  private def mailStatusFor(hakemuksenTulos: Hakemuksentulos): HakemusMailStatus = {
    val mailables = hakemuksenTulos.hakutoiveet.map { (hakutoive: Hakutoiveentulos) =>
      val (status, message) = if (valintatulosCollection.alreadyMailed(hakemuksenTulos, hakutoive)) {
        (MailStatus.MAILED, "Already mailed")
      } else if (Vastaanotettavuustila.isVastaanotettavissa(hakutoive.vastaanotettavuustila)) {
        (MailStatus.SHOULD_MAIL, "Vastaanotettavissa (" + hakutoive.valintatila + ")")
      } else if (!Valintatila.isHyväksytty(hakutoive.valintatila) && Valintatila.isFinal(hakutoive.valintatila)) {
        (MailStatus.NEVER_MAIL, "Ei hyväksytty (" + hakutoive.valintatila + ")")
      } else {
        (MailStatus.NOT_MAILED, "Ei vastaanotettavissa (" + hakutoive.valintatila + ")")
      }
      HakukohdeMailStatus(hakutoive.hakukohdeOid, hakutoive.valintatapajonoOid, status,
        hakutoive.vastaanottoDeadline, message, hakutoive.ehdollisestiHyvaksyttavissa)
    }
    HakemusMailStatus(hakemuksenTulos.hakemusOid, mailables, hakemuksenTulos.hakuOid)
  }

  private def fetchHakemuksentulos(id: HakemusIdentifier): Option[Hakemuksentulos] = {
    try {
      valintatulosService.hakemuksentulos(id.hakuOid, id.hakemusOid)
    } catch {
      case e: Exception =>
        logger.error("Error fetching data for email polling. Candidate identifier=" + id, e)
        None
    }
  }
}



