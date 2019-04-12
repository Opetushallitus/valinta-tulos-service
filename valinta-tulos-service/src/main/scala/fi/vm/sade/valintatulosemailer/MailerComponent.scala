package fi.vm.sade.valintatulosemailer

import java.util.concurrent.TimeUnit.MINUTES

import fi.vm.sade.groupemailer.{EmailInfo, GroupEmail, GroupEmailComponent, Recipient}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosemailer.config.EmailerConfigComponent
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakuOid, HakukohdeOid}
import fi.vm.sade.valintatulosservice.vastaanottomeili.LahetysSyy.LahetysSyy
import fi.vm.sade.valintatulosservice.vastaanottomeili._

import scala.collection.immutable.HashMap
import scala.concurrent.duration.Duration

trait MailerComponent {
  this: GroupEmailComponent with EmailerConfigComponent =>

  val mailer: Mailer
  val mailPoller: MailPollerAdapter
  val mailDecorator: MailDecorator

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


    def sendMailForAll(): List[String] = {
      collectAndSend(None, 0, List.empty, List.empty)
    }

    def sendMailForHaku(hakuOid: HakuOid): List[String] = {
      collectAndSend(Some(Left(hakuOid)), 0, List.empty, List.empty)
    }

    def sendMailForHakukohde(hakukohdeOid: HakukohdeOid): List[String] = {
      collectAndSend(Some(Right(hakukohdeOid)), 0, List.empty, List.empty)
    }

    private def collectAndSend(query: Option[Either[HakuOid,HakukohdeOid]], batchNr: Int, ids: List[String], batch: List[Ilmoitus]): List[String] = {
      def sendAndConfirm(currentBatch: List[Ilmoitus]): List[String] = {
        val groupedlmoituses = helper.splitAndGroupIlmoitus(currentBatch)

        val sentIds: List[String] = groupedlmoituses.flatMap { case ((language, syy), ilmoitukset) =>
          sendBatch(ilmoitukset, language, syy)
        }.toList
        ids ++ sentIds
      }

      logger.info("Fetching recipients from valinta-tulos-service")
      val newPollResult: PollResult = fetchRecipientBatch(query)
      val newBatch = newPollResult.mailables
      logger.info(s"Found ${newBatch.size} to send. " +
        s"complete == ${newPollResult.complete}, " +
        s"candidatesProcessed == ${newPollResult.candidatesProcessed}, " +
        s"last poll started == ${newPollResult.started}")
      newBatch.foreach(ilmoitus => logger.info("Found " + ilmoitus.toString))

      if (newBatch.nonEmpty) {
        val currentBatch = batch ::: newBatch
        val currentBatchSize: Int = currentBatch.size
        val sendBatchSize: Int = settings.emailBatchSize
        if (currentBatchSize >= sendBatchSize) {
          logger.info(s"Email batch size exceeded. Sending batch nr. $batchNr")
          val batchIds: List[String] = sendAndConfirm(currentBatch)
          collectAndSend(query, batchNr + 1, batchIds, List.empty)
        } else if (!newPollResult.complete) {
          logger.info(s"Time limit for single batch retrieval exceeded. Sending batch nr. $batchNr")
          val batchIds: List[String] = sendAndConfirm(currentBatch)
          collectAndSend(query, batchNr + 1, batchIds, List.empty)
        } else {
          logger.info(s"Email batch size not exceeded. ($currentBatchSize < $sendBatchSize)")
          collectAndSend(query, batchNr, ids, currentBatch)
        }
      } else {
        if (batch.nonEmpty && newPollResult.complete) {
          logger.info("Last batch fetched")
          sendAndConfirm(batch)
        } else if (newPollResult.complete) {
          logger.info("Polling complete and all batches processed, stopping")
          ids
        } else {
          logger.info("Continuing to poll")
          collectAndSend(query, batchNr, ids, batch)
        }
      }
    }

    private def sendBatch(batch: List[Ilmoitus], language: String, lahetysSyy: LahetysSyy): Option[String] = {
      val recipients: List[Recipient] = batch.map(ryhmasahkoposti.VTRecipient(_, language))

      logger.info(s"Starting to send batch. Language $language. LahetysSyy $lahetysSyy Batch size ${recipients.size}")
      try {
        groupEmailService.send(GroupEmail(recipients, EmailInfo("omattiedot", letterTemplateNameFor(lahetysSyy), language))) match {
          case Some(id) =>
            sendConfirmation(batch)
            logger.info(s"Succesfully confirmed batch id: $id")
            Some(id)
          case _ =>
            None
        }
      } catch {
        case e: Exception =>
          logger.error("Group email sending error " + e)
          None
      }
    }

    private def fetchRecipientBatch(query: Option[Either[HakuOid,HakukohdeOid]]): PollResult = {
      query match {
        case None =>
          mailPoller.pollForAllMailables(mailDecorator, mailablesLimit, timeLimit)
        case Some(Left(hakuOid)) =>
          mailPoller.pollForMailablesForHaku(hakuOid, mailDecorator, mailablesLimit, timeLimit)
        case Some(Right(hakukohdeOid)) =>
          mailPoller.pollForMailablesForHakukohde(hakukohdeOid, mailDecorator, mailablesLimit, timeLimit)
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
  def sendMailForAll(): List[String]
  def sendMailForHaku(hakuOid: HakuOid): List[String]
  def sendMailForHakukohde(hakukohdeOid: HakukohdeOid): List[String]
}
