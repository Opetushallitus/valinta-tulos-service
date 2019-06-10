package fi.vm.sade.valintatulosservice.vastaanottomeili

import java.util.concurrent.TimeUnit.MINUTES

import fi.vm.sade.groupemailer.{EmailInfo, GroupEmail, GroupEmailComponent, Recipient}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.EmailerConfigComponent
import fi.vm.sade.valintatulosservice.ryhmasahkoposti.VTRecipient
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, ValintatapajonoOid}
import fi.vm.sade.valintatulosservice.vastaanottomeili.LahetysSyy.LahetysSyy

import scala.collection.immutable.HashMap
import scala.concurrent.duration.Duration

trait MailerComponent {
  this: GroupEmailComponent with EmailerConfigComponent =>

  def mailer: Mailer
  def mailPoller: MailPoller
  def mailDecorator: MailDecorator

  class MailerImpl extends Mailer with Logging {
    val mailablesLimit: Int = settings.recipientBatchSize
    val timeLimit = Duration(settings.recipientBatchLimitMinutes, MINUTES)

    private val helper: MailerHelper = new MailerHelper
    private val letterTemplateNameFor = HashMap[LahetysSyy, String](
      LahetysSyy.vastaanottoilmoitusKk -> "omattiedot_email",
      LahetysSyy.vastaanottoilmoitus2aste -> "omattiedot_email_2aste",
      LahetysSyy.ehdollisen_periytymisen_ilmoitus -> "ehdollisen_periytyminen_email",
      LahetysSyy.sitovan_vastaanoton_ilmoitus -> "sitova_vastaanotto_email"
    )

    def sendMailFor(query: MailerQuery): List[String] = {
      logger.info(s"Start mail sending for query $query")
      forceResendIfAppropriate(query)
      collectAndSend(query, 0, List.empty, List.empty)
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

    private def collectAndSend(query: MailerQuery, batchNr: Int, ids: List[String], batch: List[Ilmoitus]): List[String] = {
      def handleBatchAndContinue(currentBatch: List[Ilmoitus], isPollingComplete: Boolean): List[String] = {
        if (currentBatch.size >= settings.emailBatchSize) {
          logger.info(s"Email batch size exceeded. Sending batch nr. $batchNr")
          val batchIds: List[String] = sendAndConfirm(currentBatch)
          collectAndSend(query, batchNr + 1, batchIds, List.empty)
        } else if (!isPollingComplete) {
          logger.info(s"Time limit for single batch retrieval exceeded. Sending batch nr. $batchNr")
          val batchIds: List[String] = sendAndConfirm(currentBatch)
          collectAndSend(query, batchNr + 1, batchIds, List.empty)
        } else {
          logger.info(s"Email batch size not exceeded. (${currentBatch.size} < ${settings.emailBatchSize})")
          collectAndSend(query, batchNr, ids, currentBatch)
        }
      }

      def sendAndConfirm(currentBatch: List[Ilmoitus]): List[String] = {
        val groupedlmoituses = helper.splitAndGroupIlmoitus(currentBatch)

        val sentIds: List[String] = groupedlmoituses.flatMap { case ((language, syy), ilmoitukset) =>
          sendBatch(ilmoitukset, language, syy)
        }.toList
        ids ++ sentIds
      }

      logger.info("Polling for recipients")
      val newPollResult: PollResult = fetchRecipientBatch(query)
      val newBatch = newPollResult.mailables
      logger.info(s"Found ${newBatch.size} to send. " +
        s"isPollingComplete == ${newPollResult.isPollingComplete}, " +
        s"candidatesProcessed == ${newPollResult.candidatesProcessed}, " +
        s"last poll started == ${newPollResult.started}")
      newBatch.foreach(ilmoitus => logger.info("Found " + ilmoitus.toString))

      if (newBatch.nonEmpty) {
        handleBatchAndContinue(batch ::: newBatch, newPollResult.isPollingComplete)
      } else {
        if (batch.nonEmpty && newPollResult.isPollingComplete) {
          logger.info("Last batch fetched")
          sendAndConfirm(batch)
        } else if (newPollResult.isPollingComplete) {
          logger.info("Polling complete and all batches processed, stopping")
          ids
        } else {
          logger.info("Continuing to poll")
          collectAndSend(query, batchNr, ids, batch)
        }
      }
    }

    private def sendBatch(batch: List[Ilmoitus], language: String, lahetysSyy: LahetysSyy): Option[String] = {
      val recipients: List[Recipient] = batch.map(VTRecipient(_, language))

      val batchLogString = s"(Language=$language LahetysSyy=$lahetysSyy BatchSize=${recipients.size})"

      logger.info(s"Starting to send batch. $batchLogString")
      try {
        groupEmailService.send(GroupEmail(recipients, EmailInfo("omattiedot", letterTemplateNameFor(lahetysSyy), language))) match {
          case Some(id) =>
            logger.info(s"Successful response from group email service for batch sending $batchLogString.")
            sendConfirmation(batch)
            logger.info(s"Succesfully confirmed batch id: $id")
            Some(id)
          case None =>
            logger.error(s"Empty response from group email service for batch sending $batchLogString.")
            None
        }
      } catch {
        case e: Exception =>
          logger.error(s"Error response from group email service for batch sending $batchLogString, exception: " + e, e)
          batch.foreach(i => mailPoller.deleteMailEntries(i.hakemusOid))
          None
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
  }

}

trait Mailer {
  def sendMailFor(query: MailerQuery): List[String]
}

sealed trait MailerQuery
case object AllQuery extends MailerQuery
case class HakuQuery(hakuOid: HakuOid) extends MailerQuery
case class HakukohdeQuery(hakukohdeOid: HakukohdeOid) extends MailerQuery
case class HakemusQuery(hakemusOid: HakemusOid) extends MailerQuery
case class ValintatapajonoQuery(hakukohdeOid: HakukohdeOid, jonoOid: ValintatapajonoOid) extends MailerQuery