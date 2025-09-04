package fi.vm.sade.valintatulosservice.vastaanottomeili

import fi.vm.sade.oppijantunnistus.OppijanTunnistusService
import fi.vm.sade.valintatulosservice.tcp.PortChecker
import fi.vm.sade.valintatulosservice.ValintatulosService
import fi.vm.sade.valintatulosservice.config.EmailerRegistry.{EmailerRegistry, IT}
import fi.vm.sade.valintatulosservice.config.{EmailerRegistry, VtsApplicationSettings}
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.logging.Logging
import fi.vm.sade.valintatulosservice.ohjausparametrit.OhjausparametritService
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.MailPollerRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, Vastaanottotila}
import org.apache.log4j._
import org.apache.log4j.spi.LoggingEvent
import org.junit.runner.RunWith
import org.scalatra.test.HttpComponentsClient
import org.specs2.mock.Mockito
import org.specs2.mutable._
import org.specs2.runner.JUnitRunner

import scala.concurrent.duration.Duration

@RunWith(classOf[JUnitRunner])
class SmokeTest extends Specification with HttpComponentsClient with Mockito with Logging {
  val hakuService: HakuService = mock[HakuService]
  val oppijanTunnistusService: OppijanTunnistusService = mock[OppijanTunnistusService]
  val mailPollerRepository: MailPollerRepository = mock[MailPollerRepository]
  val valintatulosService: ValintatulosService = mock[ValintatulosService]
  val hakemusRepository: HakemusRepository = mock[HakemusRepository]
  val ohjausparametritService: OhjausparametritService = mock[OhjausparametritService]
  val vtsApplicationSettings: VtsApplicationSettings = mock[VtsApplicationSettings]

  vtsApplicationSettings.mailPollerConcurrency returns 2

  private val mailDecorator = new MailDecorator(
    hakuService,
    oppijanTunnistusService,
    ohjausparametritService
  )
  private val mailPoller = mock[MailPoller]

  mailPollerRepository.findHakukohdeOidsCheckedRecently(any[Duration]) returns Set.empty

  private val hakukohde = Hakukohde(
    HakukohdeOid("hakukohde_oid"),
    LahetysSyy.vastaanottoilmoitus2aste,
    Vastaanottotila.kesken,
    ehdollisestiHyvaksyttavissa = false,
    hakukohteenNimet = Map("fi" -> "hakukohteen_nimi"),
    tarjoajaNimet = Map("fi" -> "tarjoajan_nimi")
  )

  private val ilmoitus = Ilmoitus(
    hakemusOid = HakemusOid("hakemus_oid"),
    hakijaOid = "hakija_oid",
    secureLink = None,
    asiointikieli = "fi",
    etunimi = "Testi",
    email = "a@a.a",
    deadline = None,
    hakukohteet = List(hakukohde),
    haku = Haku(HakuOid("haku_oid"), nimi = Map("fi" -> "haun_nimi"), toinenAste = false)
  )
  mailPoller.pollForAllMailables(any, any, any).returns(PollResult(mailables = List(ilmoitus)), PollResult(isPollingComplete = true, mailables = Nil))

  lazy val registry: EmailerRegistry = EmailerRegistry.fromString(Option(System.getProperty("valintatulos.profile")).getOrElse("it"))(mailPoller, mailDecorator)

  private val valintatulosPort: Int = sys.props.getOrElse("valintatulos.port", PortChecker.findFreeLocalPort.toString).toInt
  override def baseUrl: String = "http://localhost:" + valintatulosPort + "/valinta-tulos-service"

  "Fetch, send and confirm batch" in {
    val appender: TestAppender = new TestAppender
    Logger.getRootLogger.addAppender(appender)
    registry.mailer.sendMailFor(AllQuery)
    registry.asInstanceOf[IT].lastEmailSize mustEqual 1
    appender.errors mustEqual List()
  }
}

class TestAppender extends AppenderSkeleton {
  private var events: List[LoggingEvent] = Nil

  def errors = events.filter { event => List(Level.ERROR, Level.FATAL).contains(event.getLevel)}.map(_.getMessage)

  override def append(event: LoggingEvent): Unit = {
    events = events ++ List(event)
  }

  override def requiresLayout() = false

  override def close() {}
}
