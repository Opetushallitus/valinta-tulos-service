package fi.vm.sade.valintatulosemailer

import fi.vm.sade.groupemailer.{EmailInfo, GroupEmail, GroupEmailComponent, Recipient}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosemailer.config.EmailerConfigComponent
import fi.vm.sade.valintatulosemailer.valintatulos.VastaanottopostiComponent
import fi.vm.sade.valintatulosservice.vastaanottomeili.LahetysSyy.LahetysSyy
import fi.vm.sade.valintatulosservice.vastaanottomeili.{Ilmoitus, LahetysSyy, PollResult}

import scala.collection.immutable.HashMap

trait MailerComponent {
  this: GroupEmailComponent with VastaanottopostiComponent with EmailerConfigComponent =>

  val mailer: Mailer

  class MailerImpl extends Mailer with Logging {
    private val helper: MailerHelper = new MailerHelper
    private val letterTemplateNameFor = HashMap[LahetysSyy, String](
      LahetysSyy.vastaanottoilmoitusKk -> "omattiedot_email",
      LahetysSyy.vastaanottoilmoitus2aste -> "omattiedot_email_2aste",
      LahetysSyy.ehdollisen_periytymisen_ilmoitus -> "ehdollisen_periytyminen_email",
      LahetysSyy.sitovan_vastaanoton_ilmoitus -> "sitova_vastaanotto_email"
    )

    def sendMail: List[String] = {
      collectAndSend(0, List.empty, List.empty)
    }

    private def collectAndSend(batchNr: Int, ids: List[String], batch: List[Ilmoitus]): List[String] = {
      def sendAndConfirm(currentBatch: List[Ilmoitus]): List[String] = {
        val groupedlmoituses = helper.splitAndGroupIlmoitus(currentBatch)

        val sentIds: List[String] = groupedlmoituses.flatMap { case ((language, syy), ilmoitukset) =>
          sendBatch(ilmoitukset, language, syy)
        }.toList
        ids ++ sentIds
      }

      logger.info("Fetching recipients from valinta-tulos-service")
      val newPollResult: PollResult = vastaanottopostiService.fetchRecipientBatch
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
          collectAndSend(batchNr + 1, batchIds, List.empty)
        } else if (!newPollResult.complete) {
          logger.info(s"Time limit for single batch retrieval exceeded. Sending batch nr. $batchNr")
          val batchIds: List[String] = sendAndConfirm(currentBatch)
          collectAndSend(batchNr + 1, batchIds, List.empty)
        } else {
          logger.info(s"Email batch size not exceeded. ($currentBatchSize < $sendBatchSize)")
          collectAndSend(batchNr, ids, currentBatch)
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
          collectAndSend(batchNr, ids, batch)
        }
      }
    }

    private def sendBatch(batch: List[Ilmoitus], language: String, lahetysSyy: LahetysSyy): Option[String] = {
      val recipients: List[Recipient] = batch.map(ryhmasahkoposti.VTRecipient(_, language))

      logger.info(s"Starting to send batch. Language $language. LahetysSyy $lahetysSyy Batch size ${recipients.size}")
      try {
        groupEmailService.send(GroupEmail(recipients, EmailInfo("omattiedot", letterTemplateNameFor(lahetysSyy), language))) match {
          case Some(id) =>
            vastaanottopostiService.sendConfirmation(batch)
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
  }

}

trait Mailer {
  def sendMail: List[String]
}
