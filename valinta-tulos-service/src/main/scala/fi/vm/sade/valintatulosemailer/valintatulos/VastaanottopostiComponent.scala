package fi.vm.sade.valintatulosemailer.valintatulos

import java.util.Date

import fi.vm.sade.utils.http.DefaultHttpClient
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosemailer.config.EmailerConfigComponent
import fi.vm.sade.valintatulosemailer.json.JsonFormats
import fi.vm.sade.valintatulosemailer.util.RandomDataGenerator._
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, Vastaanottotila}
import fi.vm.sade.valintatulosservice.vastaanottomeili._
import org.json4s.jackson.JsonMethods.parse
import org.json4s.jackson.Serialization
import scalaj.http.HttpOptions

import scala.annotation.tailrec
import scala.util.{Failure, Random, Success, Try}

trait VastaanottopostiComponent {
  this: EmailerConfigComponent =>

  val vastaanottopostiService: VastaanottopostiService
  type ResponseWithHeaders = (Int, Map[String, String], String)
  private val responseHasOkStatus: ResponseWithHeaders => Boolean = { case (status, _, _) => status >= 200 && status < 300 }

  class RemoteVastaanottopostiService extends VastaanottopostiService with JsonFormats with Logging {
    private val httpOptions = Seq(HttpOptions.connTimeout(10 * 1000), HttpOptions.readTimeout(8 * 60 * 60 * 1000))
    private val retryCounter = 1

    def fetchRecipientBatch: PollResult = {
      val operation: Unit => ResponseWithHeaders = _ => DefaultHttpClient.httpGet(settings.vastaanottopostiUrl, httpOptions: _*)
        .param("limit", settings.recipientBatchSize.toString)
        .param("durationLimitMinutes", settings.recipientBatchLimitMinutes.toString)
        .responseWithHeaders()

      runWithRetry(operation, responseHasOkStatus, 0) match {
        case (status, _, body) if status >= 200 && status < 300 =>
          logger.info(s"Received from valinta-tulos-service: $body")
          parse(body).extract[PollResult]
        case (status, _, body) =>
          logger.error(s"Couldn't not connect to: ${settings.vastaanottopostiUrl}")
          logger.error(s"Fetching recipient batch failed with status: $status and body: $body")
          PollResult(complete = false, -1, new Date(), Nil)
      }
    }

    def sendConfirmation(recipients: List[Ilmoitus]): Boolean = {
      val receipts: List[LahetysKuittaus] = recipients.map(i => LahetysKuittaus(i.hakemusOid, i.hakukohteet.map(_.oid), List()))
      val operation: Unit => ResponseWithHeaders = _ => DefaultHttpClient.httpPost(
        settings.vastaanottopostiUrl,
        Some(Serialization.write(receipts))).header("Content-type", "application/json").responseWithHeaders()

      runWithRetry(operation, responseHasOkStatus, 0) match {
        case (status, _, _) if status >= 200 && status < 300 =>
          true
        case (status, _, body) =>
          logger.error(s"Sending confirmation failed with status: $status and body: $body")
          false
      }
    }

    @tailrec
    private def runWithRetry[R](operation: Unit => R, success: R => Boolean, retryCount: Int): R = {
      val msToSleep = settings.sendConfirmationSleep.toMillis * retryCount
      Try(operation.apply()) match {
        case Success(result) if success(result) =>
          result
        case Success(result) if retryCount >= settings.sendConfirmationRetries =>
          result
        case Success(result) =>
          logger.warn(s"Sleeping for $msToSleep ms before running retry " +
            s"$retryCount/${settings.sendConfirmationRetries}, " +
            s"since HTTP request failed failed with $result...")
          Thread.sleep(msToSleep)
          logger.warn("Retrying now.")
          runWithRetry(operation, success, retryCount + 1)
        case Failure(e) if retryCount >= settings.sendConfirmationRetries =>
          throw e
        case Failure(e) =>
          logger.warn(s"Sleeping for $msToSleep ms before running retry " +
            s"$retryCount/${settings.sendConfirmationRetries}, " +
            s"since HTTP request failed failed with exception...", e)
          Thread.sleep(msToSleep)
          logger.warn("Retrying now.")
          runWithRetry(operation, success, retryCount + 1)
      }
    }
  }

  class FakeVastaanottopostiService extends VastaanottopostiService {
    private[this] var _confirmAmount: Int = 0
    private var sentAmount = 0
    val maxResults: Int = settings.emailBatchSize + 1
    val recipients = List.fill(maxResults)(randomIlmoitus)

    def fetchRecipientBatch: PollResult = {
      if (sentAmount < maxResults) {
        val guys = recipients.slice(sentAmount, sentAmount + settings.recipientBatchSize)
        sentAmount += guys.size
        PollResult(complete = true, sentAmount, new Date(), guys)
      } else {
        PollResult(complete = true, sentAmount, new Date(), Nil)
      }
    }

    def sendConfirmation(recipients: List[Ilmoitus]): Boolean = {
      _confirmAmount += recipients.size
      true
    }

    def randomIlmoitus = {
      Ilmoitus(
        HakemusOid(randomOid),
        randomOid,
        None,
        randomLang,
        randomFirstName,
        randomEmailAddress,
        Some(randomDateAfterNow),
        randomHakukohdeList,
        Haku(HakuOid(randomOid),
          Map("kieli_fi" -> "Testihaku"), false))
    }

    def randomHakukohdeList: List[Hakukohde] = List.fill(Random.nextInt(10) + 1)(randomOid).map(oid =>
      Hakukohde(
        HakukohdeOid(oid),
        LahetysSyy.vastaanottoilmoitusKk,
        Vastaanottotila.vastaanottanut,
        false,
        Map("kieli_fi" -> "Testihakukohde"),
        Map("fi" -> "Testitarjoaja")
      )
    )

    def confirmAmount: Int = _confirmAmount
  }

}

trait VastaanottopostiService {
  def fetchRecipientBatch: PollResult

  def sendConfirmation(recipients: List[Ilmoitus]): Boolean
}
