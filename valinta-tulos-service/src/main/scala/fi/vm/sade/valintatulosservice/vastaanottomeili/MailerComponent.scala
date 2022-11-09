package fi.vm.sade.valintatulosservice.vastaanottomeili

import com.google.common.base.Charsets
import com.google.common.io.Resources
import com.google.common.io.Resources.getResource
import com.hubspot.jinjava.Jinjava

import java.util.concurrent.TimeUnit.MINUTES
import fi.vm.sade.groupemailer.{EmailData, EmailMessage, EmailRecipient, GroupEmailComponent, Recipient}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.EmailerConfigComponent
import fi.vm.sade.valintatulosservice.json.JsonFormats.jsonFormats
import fi.vm.sade.valintatulosservice.ryhmasahkoposti.VTRecipient
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, ValintatapajonoOid}
import fi.vm.sade.valintatulosservice.vastaanottomeili.LahetysSyy.{LahetysSyy, ehdollisen_periytymisen_ilmoitus, sitovan_vastaanoton_ilmoitus, vastaanottoilmoitus2aste, vastaanottoilmoitusKk}
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse
import org.json4s.jackson.Serialization.write
import scalaz.Memo

import scala.annotation.tailrec
import scala.collection.Iterable
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
          sendBatch(ilmoitukset, language, syy)
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

    private val jinjava = new Jinjava()

    private def asEmailData(subject: String, template: String, ilmoitus: Ilmoitus): EmailData = {
      def jsonStrToMap(jsonStr: String): Map[String, Any] = {
        implicit val formats: DefaultFormats.type = org.json4s.DefaultFormats

        parse(jsonStr).snakizeKeys
          .extract[Map[String, Any]]
      }

      def toJava(m: Any): Any = {
        import java.util
        import scala.collection.JavaConverters._
        m match {
          case sm: Map[_, _] => sm.map(kv => (kv._1, toJava(kv._2))).asJava
          case sl: Iterable[_] => new util.ArrayList(sl.map(toJava).asJava.asInstanceOf[util.Collection[_]])
          case _ => m
        }
      }

      val data = jsonStrToMap(write(ilmoitus)(jsonFormats))
      val body = jinjava.render(template, toJava(data).asInstanceOf[java.util.Map[String, Any]])
      EmailData(
        EmailMessage("valinta-tulos-service",
          subject, body, html = true),
        List(EmailRecipient(ilmoitus.email)))
    }

    private lazy val templates: String => String = Memo.mutableHashMapMemo(name =>
      Resources.toString(getResource(classOf[MailerImpl],name), Charsets.UTF_8))

    private def sendBatch(batch: List[Ilmoitus], language: String, lahetysSyy: LahetysSyy): List[String] = {
      val recipients: List[Recipient] = batch.map(VTRecipient(_, language))

      val batchLogString: Int => String = index =>
        s"(Language=$language LahetysSyy=$lahetysSyy BatchSize=$index/${recipients.size})"

      logger.info(s"Starting to send batch. ${batchLogString(0)}")
      try {
        batch.zipWithIndex.flatMap {
          case (ilmoitus, index) =>
            val hakemusOid = ilmoitus.hakemusOid

            val (templateName, subjectFi, subjectSv, subjectEn) = lahetysSyy match {
              case LahetysSyy.vastaanottoilmoitusKk =>
                ("omattiedot_email",
                  s"Vastaanottoilmoitus (fi) $hakemusOid",
                  s"Vastaanottoilmoitus (sv) $hakemusOid",
                  s"Vastaanottoilmoitus (en) $hakemusOid")
              case LahetysSyy.vastaanottoilmoitus2aste =>
                ("omattiedot_email_2aste",
                  s"Vastaanottoilmoitus 2.aste (fi) $hakemusOid",
                  s"Vastaanottoilmoitus 2.aste (sv) $hakemusOid",
                  s"Vastaanottoilmoitus 2.aste (en) $hakemusOid")
              case LahetysSyy.ehdollisen_periytymisen_ilmoitus =>
                ("ehdollisen_periytyminen_email",
                  s"Ehdollinen periytyminen (fi) $hakemusOid",
                  s"Ehdollinen periytyminen (sv) $hakemusOid",
                  s"Ehdollinen periytyminen (en) $hakemusOid")
              case LahetysSyy.sitovan_vastaanoton_ilmoitus =>
                ("sitova_vastaanotto_email",
                  s"Sitova vastaanotto (fi) $hakemusOid",
                  s"Sitova vastaanotto (sv) $hakemusOid",
                  s"Sitova vastaanotto (en) $hakemusOid")
            }
            val template = templates(s"email-templates/${templateName}_template_$language.html")
            groupEmailService.sendMailWithoutTemplate(asEmailData(
              language match {
                case "en" => subjectEn
                case "sv" => subjectSv
                case _ => subjectFi
              },
              template,
              ilmoitus)) match {
              case Some(id) =>
                logger.info(s"Successful response from group email service for batch sending ${batchLogString(index)}.")
                sendConfirmation(batch)
                logger.info(s"Succesfully confirmed batch id: $id")
                Some(id)
              case None =>
                logger.error(s"Empty response from group email service for batch sending ${batchLogString(index)}.")
                None
            }
        }

      } catch {
        case e: Exception =>
          logger.error(s"Error response from group email service for batch sending ${batchLogString(0)}, exception: " + e, e)
          batch.foreach(i => mailPoller.deleteMailEntries(i.hakemusOid))
          List()
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
