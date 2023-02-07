package fi.vm.sade.valintatulosservice.vastaanottomeili

import java.util.concurrent.TimeUnit.MINUTES
import fi.vm.sade.groupemailer.{EmailData, EmailMessage, EmailRecipient, GroupEmailComponent, Recipient}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.EmailerConfigComponent
import fi.vm.sade.valintatulosservice.ryhmasahkoposti.VTRecipient
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, ValintatapajonoOid, Vastaanottotila}
import fi.vm.sade.valintatulosservice.vastaanottomeili.LahetysSyy.LahetysSyy

import java.util.Date
import scala.annotation.tailrec
import scala.collection.immutable
import scala.concurrent.duration.Duration
import scala.language.implicitConversions

trait MailerComponent {
  this: GroupEmailComponent with EmailerConfigComponent =>

  def mailer: Mailer

  def mailPoller: MailPoller

  def mailDecorator: MailDecorator

  class MailerImpl extends Mailer with Logging {
    val mailablesLimit: Int = settings.recipientBatchSize
    val timeLimit = Duration(settings.recipientBatchLimitMinutes, MINUTES)

    private val helper: MailerHelper = new MailerHelper

    def sendMailFor(query: MailerQuery): List[String] = {
      logger.info(s"Start mail sending for query $query")
      forceResendIfAppropriate(query)
      collectAndSend(query, 0, List.empty)
    }

    private def forceResendIfAppropriate(query: MailerQuery): Unit = {
      query match {
        case HakukohdeQuery(hakukohdeOid) =>
          mailPoller.deleteMailEntries(hakukohdeOid)
        case ValintatapajonoQuery(hakukohdeOid, jonoOid) =>
          mailPoller.deleteMailEntries(hakukohdeOid)
        case HakemusQuery(hakemusOid) =>
          mailPoller.deleteMailEntries(hakemusOid)
        case _ =>
          ()
      }
    }

    @tailrec
    private def collectAndSend(query: MailerQuery, batchNr: Int, ids: List[String]): List[String] = {
      def sendAndConfirm(currentBatch: List[Ilmoitus]): List[String] = {
        helper.splitAndGroupIlmoitus(currentBatch).flatMap { case ((language, syy), ilmoitukset) =>
          sendBatch(ilmoitukset, language.toLowerCase(), syy)
        }.toList
      }

      logger.info(s"Polling for recipients for batch number $batchNr")
      val newPollResult: PollResult = fetchRecipientBatch(query)
      val newBatch = newPollResult.mailables
      logger.info(s"Found ${newBatch.size} to send. " +
        s"isPollingComplete == ${newPollResult.isPollingComplete}, " +
        s"candidatesProcessed == ${newPollResult.candidatesProcessed}, " +
        s"last poll started == ${newPollResult.started}")
      newBatch.foreach(ilmoitus => logger.info("Found " + ilmoitus.toString))

      if (newPollResult.isPollingComplete && newBatch.isEmpty) {
        logger.info("Last batch fetched, stopping")
        ids
      } else {
        val idsToReturn = if (newBatch.isEmpty) {
          logger.warn("Time limit for single batch retrieval exceeded and not found anything to send. Finding more mailables.")
          ids
        } else {
          if (!newPollResult.isPollingComplete) {
            logger.warn("Time limit for single batch retrieval exceeded. Sending mails and finding more mailables.")
          }
          ids ::: sendAndConfirm(newBatch)
        }
        collectAndSend(query, batchNr + 1, idsToReturn)
      }
    }

    private def asEmailData(subject: String, templateName: String, ilmoitus: Ilmoitus, lahetysSyy: LahetysSyy): EmailData = {
      val body = VelocityEmailTemplate.render(templateName, EmailStructure(ilmoitus, lahetysSyy))
      EmailData(
        EmailMessage("omattiedot",
          subject, body, html = true),
        List(EmailRecipient(ilmoitus.email)))
    }

    private def sendBatch(batch: List[Ilmoitus], language: String, lahetysSyy: LahetysSyy): List[String] = {
      val recipients: List[Recipient] = batch.map(VTRecipient(_, language))

      val batchLogString: Int => String = index =>
        s"(Language=$language LahetysSyy=$lahetysSyy BatchSize=$index/${recipients.size})"

      logger.info(s"Starting to send batch. ${batchLogString(0)}")

      val results: Seq[Either[HakemusOid, String]] = batch.zipWithIndex.map {
        case (ilmoitus, index) =>
          sendIlmoitus(language, lahetysSyy, batchLogString, ilmoitus, index)
      }

      val (fails, ids) = results.foldLeft(List.empty[HakemusOid], List.empty[String]) {
        (r, ret) =>
          val (fails: List[HakemusOid], ids: List[String]) = r
          ret match {
            case Right(id) =>
              (fails, id :: ids)
            case Left(oid) =>
              (oid :: fails, ids)
          }
      }
      if (fails.nonEmpty) {
        logger.error(s"Error response from group email service for batch sending ${batchLogString(0)}. Failed hakemukses: $fails")
      }
      ids
    }

    private def sendWithRetry(data: EmailData, retriesLeft: Int = 1): Option[String] = {
      try {
        groupEmailService.sendMailWithoutTemplate(data)
      } catch {
        case e: Exception if retriesLeft > 0 =>
          logger.warn(s"(Will retry) Exception when sending email ${data.email.subject}:", e)
          sendWithRetry(data, retriesLeft -1)
        case e =>
          logger.error(s"(No more retries) Exception when sending email ${data.email.subject}:", e)
          None
      }
    }

    private def sendIlmoitus(language: String, lahetysSyy: LahetysSyy, batchLogString: Int => String, ilmoitus: Ilmoitus, index: Int): Either[HakemusOid, String] = {
      try {
        val hakemusOid = ilmoitus.hakemusOid
        val applicationPostfix = language match {
          case "fi" => s"(Hakemusnumero: $hakemusOid)"
          case "sv" => s"(Ansökningsnummer: $hakemusOid)"
          case "en" => s"(Application number: $hakemusOid)"
          case _ => ""
        }

        val (templateName, subjectFi, subjectSv, subjectEn) = lahetysSyy match {
          case LahetysSyy.vastaanottoilmoitusMuut =>
            ("opiskelupaikka_vastaanotettavissa_email_muut",
              s"Opiskelupaikka vastaanotettavissa Opintopolussa $applicationPostfix",
              s"Studieplatsen kan tas emot i Studieinfo $applicationPostfix",
              s"Offer of admission in Studyinfo $applicationPostfix")
          case LahetysSyy.vastaanottoilmoitusKk =>
            ("opiskelupaikka_vastaanotettavissa_email_kk",
              s"Opiskelupaikka vastaanotettavissa Opintopolussa $applicationPostfix",
              s"Studieplatsen kan tas emot i Studieinfo $applicationPostfix",
              s"Offer of admission in Studyinfo $applicationPostfix")
          case LahetysSyy.vastaanottoilmoitusKkTutkintoonJohtamaton =>
            ("opiskelupaikka_vastaanotettavissa_email_kk_tutkintoon_johtamaton",
              s"Opiskelupaikka vastaanotettavissa Opintopolussa $applicationPostfix",
              s"Studieplatsen kan tas emot i Studieinfo $applicationPostfix",
              s"Offer of admission in Studyinfo $applicationPostfix")
          case LahetysSyy.vastaanottoilmoitus2aste =>
            ("opiskelupaikka_vastaanotettavissa_email_2aste",
              s"Opiskelupaikka vastaanotettavissa Opintopolussa $applicationPostfix",
              s"Studieplatsen kan tas emot i Studieinfo $applicationPostfix",
              s"Offer of admission in Studyinfo $applicationPostfix")
          case LahetysSyy.vastaanottoilmoitus2asteEiYhteishaku =>
            ("opiskelupaikka_vastaanotettavissa_email_2aste_ei_yhteishaku",
              s"Opiskelupaikka vastaanotettavissa Opintopolussa $applicationPostfix",
              s"Studieplatsen kan tas emot i Studieinfo $applicationPostfix",
              s"Offer of admission in Studyinfo $applicationPostfix")
          case LahetysSyy.ehdollisen_periytymisen_ilmoitus =>
            ("ehdollisen_periytyminen_email",
              s"Opintopolku: Tilannetietoa jonottamisesta $applicationPostfix",
              s"Studieinfo: Information om köande $applicationPostfix",
              s"Studyinfo: Information about the wait list $applicationPostfix")
          case LahetysSyy.sitovan_vastaanoton_ilmoitus =>
            ("sitova_vastaanotto_email",
              s"Opintopolku: Paikan vastaanotto vahvistettu - ilmoittaudu opintoihin $applicationPostfix",
              s"Studieinfo: Mottagandet av studieplatsen har bekräftats - anmäl dig till studierna $applicationPostfix",
              s"Your study place has been confirmed - register for studies $applicationPostfix")
        }

        val data = asEmailData(
          language match {
            case "en" => subjectEn
            case "sv" => subjectSv
            case _ => subjectFi
          },
          s"email-templates/${templateName}_template_$language.html",
          ilmoitus,
          lahetysSyy
        )

        sendWithRetry(data) match {
          case Some(id) =>
            logger.info(s"Successful response from group email service for batch sending ${batchLogString(index)}.")
            sendConfirmation(List(ilmoitus))
            logger.info(s"Succesfully confirmed batch id: $id")
            Right(id)
          case None =>
            logger.error(s"Empty response from group email service for batch sending ${batchLogString(index)}.")
            Left(ilmoitus.hakemusOid)
        }
      } catch {
        case e: Exception =>
          logger.error(s"Error sending ${batchLogString(index)}.", e)
          Left(ilmoitus.hakemusOid)
      }
    }

    private def fetchRecipientBatch(query: MailerQuery): PollResult = {
      query match {
        case AllQuery =>
          mailPoller.pollForAllMailables(mailDecorator, mailablesLimit, timeLimit)
        case HakuQuery(hakuOid) =>
          mailPoller.pollForMailablesForHaku(hakuOid, mailDecorator, mailablesLimit, timeLimit)
        case HakukohdeQuery(hakukohdeOid) =>
          mailPoller.pollForMailablesForHakukohde(hakukohdeOid, mailDecorator, mailablesLimit, timeLimit)
        case ValintatapajonoQuery(hakukohdeOid, jonoOid) =>
          mailPoller.pollForMailablesForValintatapajono(hakukohdeOid, jonoOid, mailDecorator, mailablesLimit, timeLimit)
        case HakemusQuery(hakemusOid) =>
          mailPoller.pollForMailablesForHakemus(hakemusOid, mailDecorator)
      }
    }

    private def sendConfirmation(recipients: List[Ilmoitus]): Unit = {
      if (recipients.isEmpty) {
        throw new IllegalArgumentException("got confirmation of 0 applications")
      }
      logger.info("got confirmation for " + recipients.size + " applications: " + recipients.map(_.hakemusOid).mkString(","))
      mailPoller.markAsSent(recipients)
    }

    def cleanup(): Unit = {
      mailPoller.deleteIncompleteMailEntries()
    }
  }
}

trait Mailer {
  def sendMailFor(query: MailerQuery): List[String]

  def cleanup(): Unit
}

sealed trait MailerQuery

case object AllQuery extends MailerQuery

case class HakuQuery(hakuOid: HakuOid) extends MailerQuery

case class HakukohdeQuery(hakukohdeOid: HakukohdeOid) extends MailerQuery

case class HakemusQuery(hakemusOid: HakemusOid) extends MailerQuery

case class ValintatapajonoQuery(hakukohdeOid: HakukohdeOid, jonoOid: ValintatapajonoOid) extends MailerQuery
