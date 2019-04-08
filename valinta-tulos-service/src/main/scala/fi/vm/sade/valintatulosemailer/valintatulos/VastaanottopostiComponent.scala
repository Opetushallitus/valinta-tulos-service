package fi.vm.sade.valintatulosemailer.valintatulos

import java.util.Date
import java.util.concurrent.TimeUnit.MINUTES

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosemailer.config.EmailerConfigComponent
import fi.vm.sade.valintatulosemailer.json.JsonFormats
import fi.vm.sade.valintatulosemailer.util.RandomDataGenerator._
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, Vastaanottotila}
import fi.vm.sade.valintatulosservice.vastaanottomeili._

import scalaj.http.HttpOptions

import scala.concurrent.duration.Duration
import scala.util.Random

trait VastaanottopostiComponent {
  this: EmailerConfigComponent =>

  val vastaanottopostiService: VastaanottopostiService
  val mailPoller: MailPollerAdapter
  val mailDecorator: MailDecorator
  type ResponseWithHeaders = (Int, Map[String, String], String)
  private val responseHasOkStatus: ResponseWithHeaders => Boolean = { case (status, _, _) => status >= 200 && status < 300 }

  class RemoteVastaanottopostiService extends VastaanottopostiService with JsonFormats with Logging {
    private val httpOptions = Seq(HttpOptions.connTimeout(10 * 1000), HttpOptions.readTimeout(8 * 60 * 60 * 1000))
    private val retryCounter = 1

    def fetchRecipientBatch: PollResult = {
      //contentType = formats("json")
      val mailablesLimit: Int = settings.recipientBatchSize
      val timeLimit = Duration(settings.recipientBatchLimitMinutes, MINUTES)
      mailPoller.pollForMailables(mailDecorator, mailablesLimit, timeLimit)
    }

    def sendConfirmation(recipients: List[Ilmoitus]): Unit = {
      if (recipients.isEmpty) {
        throw new IllegalArgumentException("got confirmation of 0 applications")
      }
      logger.info("got confirmation for " + recipients.size + " applications: " + recipients.map(_.hakemusOid).mkString(","))
      mailPoller.markAsSent(recipients)
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

    def sendConfirmation(recipients: List[Ilmoitus]): Unit = {
      _confirmAmount += recipients.size
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

  def sendConfirmation(recipients: List[Ilmoitus]): Unit
}
