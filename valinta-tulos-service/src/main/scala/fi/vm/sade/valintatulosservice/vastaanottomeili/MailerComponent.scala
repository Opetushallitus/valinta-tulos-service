package fi.vm.sade.valintatulosservice.vastaanottomeili

import fi.oph.viestinvalitys.ViestinvalitysClient
import fi.oph.viestinvalitys.vastaanotto.model.{Viesti, ViestinvalitysBuilder}
import fi.vm.sade.valintatulosservice.logging.Logging
import fi.vm.sade.valintatulosservice.config.EmailerConfigComponent
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, ValintatapajonoOid}
import fi.vm.sade.valintatulosservice.vastaanottomeili.LahetysSyy.LahetysSyy

import java.util.concurrent.TimeUnit.MINUTES
import java.util.{Optional, UUID}
import scala.annotation.tailrec
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.language.implicitConversions
import scala.util.control.NonFatal

trait MailerComponent {
  this: EmailerConfigComponent =>

  def viestinvalitysClient: ViestinvalitysClient

  def mailer: Mailer

  def mailPoller: MailPoller

  def mailDecorator: MailDecorator

  class MailerImpl extends Mailer with Logging {
    val mailablesLimit: Int = settings.recipientBatchSize
    val timeLimit: FiniteDuration = Duration(settings.recipientBatchLimitMinutes, MINUTES)

    private val helper: MailerHelper = new MailerHelper

    def sendMailFor(query: MailerQuery): List[String] = {
      logger.info(s"Start mail sending for query $query")
      forceResendIfAppropriate(query)

      val lahetys = ViestinvalitysBuilder.lahetysBuilder()
        .withOtsikko(query.toString)
        .withLahettavaPalvelu("valinta-tulos-service")
        .withLahettaja(Optional.empty(), "noreply@opintopolku.fi")
        .withNormaaliPrioriteetti()
        .withSailytysaika(2000) // About 5 and a half years
        .build()
      val lahetysTunniste: UUID = viestinvalitysClient.luoLahetys(lahetys).getLahetysTunniste

      collectAndSend(query, 0, List.empty, lahetysTunniste)
    }

    private def forceResendIfAppropriate(query: MailerQuery): Unit = {
      query match {
        case HakukohdeQuery(hakukohdeOid) =>
          mailPoller.deleteMailEntries(hakukohdeOid)
        case ValintatapajonoQuery(hakukohdeOid, _) =>
          mailPoller.deleteMailEntries(hakukohdeOid)
        case HakemusQuery(hakemusOid) =>
          mailPoller.deleteMailEntries(hakemusOid)
        case _ =>
          ()
      }
    }

    @tailrec
    private def collectAndSend(query: MailerQuery, batchNr: Int, ids: List[String], lahetysTunniste: UUID): List[String] = {
      def sendAndConfirm(currentBatch: List[Ilmoitus]): List[String] = {
        helper.splitAndGroupIlmoitus(currentBatch).flatMap {
          case ((language, syy), ilmoitukset) => sendBatch(ilmoitukset, language.toLowerCase(), syy, lahetysTunniste)
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
        collectAndSend(query, batchNr + 1, idsToReturn, lahetysTunniste)
      }
    }

    private def kayttooikeudet(ilmoitus: Ilmoitus) = {
      val builder = ViestinvalitysBuilder.kayttooikeusrajoituksetBuilder()
        .withKayttooikeus("APP_VIESTINVALITYS_OPH_PAAKAYTTAJA", "1.2.246.562.10.00000000001")

      val kayttooikeudet = Set(
        "APP_VALINTOJENTOTEUTTAMINEN_CRUD",
        "APP_VALINTOJENTOTEUTTAMINEN_READ_UPDATE",
        "APP_VALINTOJENTOTEUTTAMINENKK_CRUD",
        "APP_VALINTOJENTOTEUTTAMINENKK_READ_UPDATE")

      ilmoitus.hakukohteet
        .flatMap(_.organisaatioOiditAuktorisointiin)
        .flatMap(oid => kayttooikeudet.map((_, oid)))
        .distinct
        .foreach { case (kayttooikeus, oid) => builder.withKayttooikeus(kayttooikeus, oid) }
      builder.build()
    }

    private def asViesti(subject: String, templatePath: String, ilmoitus: Ilmoitus, lahetysSyy: LahetysSyy, lahetysTunniste: UUID): Viesti =
      ViestinvalitysBuilder.viestiBuilder()
        .withOtsikko(subject)
        .withHtmlSisalto(VelocityEmailTemplate.render(templatePath, EmailStructure(ilmoitus, lahetysSyy)))
        .withKielet(ilmoitus.asiointikieli)
        .withVastaanottajat(ViestinvalitysBuilder.vastaanottajatBuilder().withVastaanottaja(Optional.empty(), ilmoitus.email).build())
        .withKayttooikeusRajoitukset(kayttooikeudet(ilmoitus))
        .withMaskit(ilmoitus.secureLink.map { link =>
          ViestinvalitysBuilder.maskitBuilder().withMaski(link, "***secure-link***").build()
        }.getOrElse(ViestinvalitysBuilder.maskitBuilder().build()))
        .withLahetysTunniste(lahetysTunniste.toString)
        .build()

    private def sendBatch(batch: List[Ilmoitus], language: String, lahetysSyy: LahetysSyy, lahetysTunniste: UUID): List[String] = {
      val batchLogString: Int => String = index =>
        s"(Language=$language LahetysSyy=$lahetysSyy BatchSize=$index/${batch.size})"

      logger.info(s"Starting to send batch. ${batchLogString(0)}")

      val results: Seq[Either[HakemusOid, UUID]] = batch.zipWithIndex.map {
        case (ilmoitus, index) => sendIlmoitus(language, lahetysSyy, batchLogString, ilmoitus, index, lahetysTunniste)
      }

      val (fails, ids) = results.foldLeft(List.empty[HakemusOid], List.empty[String]) {
        (r, ret) =>
          val (fails: List[HakemusOid], ids: List[String]) = r
          ret match {
            case Right(id) => (fails, id.toString :: ids)
            case Left(oid) =>
              (oid :: fails, ids)
          }
      }
      if (fails.nonEmpty) {
        logger.error(s"Error response from group email service for batch sending ${batchLogString(0)}. Failed hakemukses: $fails")
      }
      ids
    }

    private def sendWithRetry(viesti: Viesti, retriesLeft: Int = 1): Option[UUID] = {
      try {
        Some(viestinvalitysClient.luoViesti(viesti).getViestiTunniste)
      } catch {
        case e: Exception if retriesLeft > 0 =>
          logger.warn(s"(Will retry) Exception when sending email ${viesti.getOtsikko}:", e)
          sendWithRetry(viesti, retriesLeft - 1)
        case NonFatal(e) =>
          logger.error(s"(No more retries) Exception when sending email ${viesti.getOtsikko}:", e)
          None
      }
    }

    private def sendIlmoitus(language: String, lahetysSyy: LahetysSyy, batchLogString: Int => String, ilmoitus: Ilmoitus, index: Int, lahetysTunniste: UUID): Either[HakemusOid, UUID] = {
      try {
        val hakemusOid = ilmoitus.hakemusOid
        val applicationPostfix = language match {
          case "fi" => s"(Hakemusnumero: $hakemusOid)"
          case "sv" => s"(Ansökningsnummer: $hakemusOid)"
          case "en" => s"(Application number: $hakemusOid)"
          case _ => ""
        }

        val templateName = lahetysSyy match {
          case LahetysSyy.vastaanottoilmoitusMuut => "opiskelupaikka_vastaanotettavissa_email_muut"
          case LahetysSyy.vastaanottoilmoitusKk => "opiskelupaikka_vastaanotettavissa_email_kk"
          case LahetysSyy.vastaanottoilmoitusKkTutkintoonJohtamaton => "opiskelupaikka_vastaanotettavissa_email_kk_tutkintoon_johtamaton"
          case LahetysSyy.vastaanottoilmoitus2aste => "opiskelupaikka_vastaanotettavissa_email_2aste"
          case LahetysSyy.vastaanottoilmoitus2asteEiYhteishaku => "opiskelupaikka_vastaanotettavissa_email_2aste_ei_yhteishaku"
          case LahetysSyy.ehdollisen_periytymisen_ilmoitus => "ehdollisen_periytyminen_email"
          case LahetysSyy.sitovan_vastaanoton_ilmoitus => "sitova_vastaanotto_email"
        }

        val subject = (lahetysSyy, language) match {
          case (LahetysSyy.ehdollisen_periytymisen_ilmoitus, "en") => s"Studyinfo: Information about the wait list $applicationPostfix"
          case (LahetysSyy.ehdollisen_periytymisen_ilmoitus, "sv") => s"Studieinfo: Information om köande $applicationPostfix"
          case (LahetysSyy.ehdollisen_periytymisen_ilmoitus, _) => s"Opintopolku: Tilannetietoa jonottamisesta $applicationPostfix"
          case (LahetysSyy.sitovan_vastaanoton_ilmoitus, "en") => s"Your study place has been confirmed - register for studies $applicationPostfix"
          case (LahetysSyy.sitovan_vastaanoton_ilmoitus, "sv") => s"Studieinfo: Mottagandet av studieplatsen har bekräftats - anmäl dig till studierna $applicationPostfix"
          case (LahetysSyy.sitovan_vastaanoton_ilmoitus, _) => s"Opintopolku: Paikan vastaanotto vahvistettu - ilmoittaudu opintoihin $applicationPostfix"
          case (_, "en") => s"Offer of admission in Studyinfo $applicationPostfix"
          case (_, "sv") => s"Studieplatsen kan tas emot i Studieinfo $applicationPostfix"
          case (_, _) => s"Opiskelupaikka vastaanotettavissa Opintopolussa $applicationPostfix"
        }

        val viesti = asViesti(subject, s"email-templates/${templateName}_template_$language.html", ilmoitus, lahetysSyy, lahetysTunniste)

        sendWithRetry(viesti) match {
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
