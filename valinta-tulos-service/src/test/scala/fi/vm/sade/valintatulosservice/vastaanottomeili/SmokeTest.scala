package fi.vm.sade.valintatulosservice.vastaanottomeili

import fi.oph.viestinvalitys.vastaanotto.model.Viesti
import fi.vm.sade.oppijantunnistus.OppijanTunnistusService
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.utils.tcp.PortChecker
import fi.vm.sade.valintatulosservice.ValintatulosService
import fi.vm.sade.valintatulosservice.config.{EmailerRegistry, VtsApplicationSettings}
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
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

import java.util.{Optional, UUID}
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
    secureLink = Some("http://secure-link"),
    asiointikieli = "fi",
    etunimi = "Testi",
    email = "a@a.a",
    deadline = None,
    hakukohteet = List(hakukohde),
    haku = Haku(HakuOid("haku_oid"), nimi = Map("fi" -> "haun_nimi"), toinenAste = false)
  )

  lazy val registry: EmailerRegistry.IT = new EmailerRegistry.IT(mailPoller, mailDecorator)

  private val valintatulosPort: Int = sys.props.getOrElse("valintatulos.port", PortChecker.findFreeLocalPort.toString).toInt
  override def baseUrl: String = "http://localhost:" + valintatulosPort + "/valinta-tulos-service"

  "Fetch, send and confirm batch" in {
    mailPoller.pollForAllMailables(any, any, any)
      .returns(PollResult(mailables = List(ilmoitus)), PollResult(isPollingComplete = true, mailables = Nil))
    val appender: TestAppender = new TestAppender
    Logger.getRootLogger.addAppender(appender)

    registry.mailer.sendMailFor(AllQuery)

    lahetysCount mustEqual 1
    viestiCount mustEqual 1
    appender.errors mustEqual List()

    val lahetystunnus = getLahetystunnukset.head
    val viesti = getViestit(lahetystunnus).head
    viesti.getOtsikko mustEqual Optional.of(s"Opiskelupaikka vastaanotettavissa Opintopolussa (Hakemusnumero: ${ilmoitus.hakemusOid})")
    viesti.getSisalto.get must contain("Sinulle on myönnetty opiskelupaikka, onneksi olkoon.")
    val vastaanottajat = viesti.getVastaanottajat.get()
    vastaanottajat.size mustEqual 1
    vastaanottajat.getFirst.getNimi mustEqual Optional.empty()
    vastaanottajat.getFirst.getSahkopostiOsoite mustEqual Optional.of(ilmoitus.email)
    val maski = viesti.getMaskit.get().getFirst
    maski.getSalaisuus mustEqual Optional.of("http://secure-link")
    maski.getMaski mustEqual Optional.of("***secure-link***")
  }

  private def getLahetystunnukset: Iterable[UUID] = withFakeClient(_.getLahetykset.keys)

  private def lahetysCount(): Int = withFakeClient(_.getLahetykset.size)

  private def getViestit(lahetystunnus: UUID): Iterable[Viesti] = withFakeClient(_.getViestit(lahetystunnus))

  private def viestiCount(): Int = withFakeClient(_.getViestit.size)

  private def withFakeClient[T](f: FakeViestinvalitysClient => T): T =
    f(registry.viestinvalitysClient.asInstanceOf[FakeViestinvalitysClient])
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
