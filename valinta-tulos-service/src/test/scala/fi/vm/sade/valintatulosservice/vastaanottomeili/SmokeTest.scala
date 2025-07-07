package fi.vm.sade.valintatulosservice.vastaanottomeili

import fi.oph.viestinvalitys.vastaanotto.model.{Lahetys, Viesti}
import fi.vm.sade.oppijantunnistus.OppijanTunnistusService
import fi.vm.sade.valintatulosservice.ValintatulosService
import fi.vm.sade.valintatulosservice.config.EmailerRegistry.EmailerRegistry
import fi.vm.sade.valintatulosservice.config.{EmailerRegistry, PortChecker, VtsApplicationSettings}
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

import java.util.{Optional, UUID}
import scala.collection.JavaConverters._
import scala.collection.immutable
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

  mailPollerRepository.findHakukohdeOidsCheckedRecently(any[Duration]) returns Set.empty

  private val defaultHakukohde = Hakukohde(
    HakukohdeOid("hakukohde_oid"),
    LahetysSyy.vastaanottoilmoitus2aste,
    Vastaanottotila.kesken,
    ehdollisestiHyvaksyttavissa = false,
    hakukohteenNimet = Map("fi" -> "hakukohteen_nimi"),
    tarjoajaNimet = Map("fi" -> "tarjoajan_nimi"),
    organisaatioOiditAuktorisointiin = Set()
  )

  private val defaultIlmoitus = Ilmoitus(
    hakemusOid = HakemusOid("hakemus_oid"),
    hakijaOid = "hakija_oid",
    secureLink = Some("http://secure-link"),
    asiointikieli = "fi",
    etunimi = "Testi",
    email = "a@a.a",
    deadline = None,
    hakukohteet = List(defaultHakukohde),
    haku = Haku(HakuOid("haku_oid"), nimi = Map("fi" -> "haun_nimi"), toinenAste = false)
  )

  private val valintatulosPort: Int = sys.props.getOrElse("valintatulos.port", PortChecker.findFreeLocalPort.toString).toInt

  override def baseUrl: String = "http://localhost:" + valintatulosPort + "/valinta-tulos-service"

  private def mockMailPollingWithOneIlmoitus(ilmoitus: Ilmoitus = defaultIlmoitus): EmailerRegistry.IT = {
    val mailPoller = mock[MailPoller]
    mailPoller.pollForAllMailables(any, any, any)
      .returns(PollResult(mailables = List(ilmoitus)), PollResult(isPollingComplete = true, mailables = Nil))
    new EmailerRegistry.IT(mailPoller, mailDecorator)
  }

  "Sends one lahetys with one viesti without any errors when mail query returns one ilmoitus" in {
    val appender: TestAppender = new TestAppender
    Logger.getRootLogger.addAppender(appender)
    val registry = mockMailPollingWithOneIlmoitus()

    registry.mailer.sendMailFor(AllQuery)

    registry.lahetysCount mustEqual 1
    registry.viestiCount mustEqual 1
    appender.errors mustEqual List()
  }

  "Sets the correct parameters for the lähetys" in {
    val registry = mockMailPollingWithOneIlmoitus()

    registry.mailer.sendMailFor(AllQuery)

    val lahetys = registry.getLahetykset.head
    lahetys.getSailytysaika mustEqual Optional.of(2000)
    lahetys.getLahettaja.get().getNimi mustEqual Optional.empty()
    lahetys.getLahettaja.get().getSahkopostiOsoite mustEqual Optional.of("noreply@opintopolku.fi")
  }

  "Sets the correct parameters for the viesti" in {
    val registry = mockMailPollingWithOneIlmoitus()

    registry.mailer.sendMailFor(AllQuery)

    val lahetystunnus = registry.getLahetystunnukset.head
    val viesti = registry.getViestit(lahetystunnus).head
    viesti.getOtsikko mustEqual Optional.of(s"Opiskelupaikka vastaanotettavissa Opintopolussa (Hakemusnumero: ${defaultIlmoitus.hakemusOid})")
    viesti.getSisalto.get must contain("Sinulle on myönnetty opiskelupaikka, onneksi olkoon.")
    val vastaanottajat = viesti.getVastaanottajat.get()
    vastaanottajat.size mustEqual 1
    vastaanottajat.getFirst.getNimi mustEqual Optional.empty()
    vastaanottajat.getFirst.getSahkopostiOsoite mustEqual Optional.of(defaultIlmoitus.email)
  }

  "Includes a maski when there's a secure link" in {
    val ilmoitusWithLink = defaultIlmoitus.copy(secureLink = Some("http://secure-link"))
    val registry = mockMailPollingWithOneIlmoitus(ilmoitusWithLink)

    registry.mailer.sendMailFor(AllQuery)

    registry.viestiCount mustEqual 1
    val viesti = registry.getViestit.head
    val maski = viesti.getMaskit.get().getFirst
    maski.getSalaisuus mustEqual Optional.of("http://secure-link")
    maski.getMaski mustEqual Optional.of("***secure-link***")
  }

  "Doesn't include a maski when there's no secure link" in {
    val ilmoitusWithoutLink = defaultIlmoitus.copy(secureLink = None)
    val registry = mockMailPollingWithOneIlmoitus(ilmoitusWithoutLink)

    registry.mailer.sendMailFor(AllQuery)

    registry.viestiCount mustEqual 1
    val viesti = registry.getViestit.head
    viesti.getMaskit.get().size() mustEqual 0
  }

  "Sets the correct kayttooikeudet for every organization" in {
    val hakukohde = defaultHakukohde.copy(organisaatioOiditAuktorisointiin = Set("1.2.246.562.10.1", "1.2.246.562.10.2"))
    val ilmoitus = defaultIlmoitus.copy(hakukohteet = List(hakukohde))
    val registry = mockMailPollingWithOneIlmoitus(ilmoitus)

    registry.mailer.sendMailFor(AllQuery)

    registry.viestiCount mustEqual 1
    val viesti = registry.getViestit.head
    viesti.getKayttooikeusRajoitukset.isPresent must beTrue
    val allOikeudet = viesti.getKayttooikeusRajoitukset
      .get()
      .asScala
      .map(k => (k.getOikeus.get(), k.getOrganisaatio.get()))
    // Viestit pystyy näkemään käyttäjät, joilla on johonkin organisaatioon valintojen toteuttamisen
    // tai kk-valintojen toteuttamisen CRUD tai luku- ja päivitysoikeudet.
    // Lisäksi viestinvälitykset pääkäyttäjillä (OPH) on oikeudet nähdä kaikki viestit.
    allOikeudet must contain(exactly(
      ("APP_VALINTOJENTOTEUTTAMINEN_CRUD", "1.2.246.562.10.1"),
      ("APP_VALINTOJENTOTEUTTAMINEN_READ_UPDATE", "1.2.246.562.10.1"),
      ("APP_VALINTOJENTOTEUTTAMINENKK_CRUD", "1.2.246.562.10.1"),
      ("APP_VALINTOJENTOTEUTTAMINENKK_READ_UPDATE", "1.2.246.562.10.1"),
      ("APP_VALINTOJENTOTEUTTAMINEN_CRUD", "1.2.246.562.10.2"),
      ("APP_VALINTOJENTOTEUTTAMINEN_READ_UPDATE", "1.2.246.562.10.2"),
      ("APP_VALINTOJENTOTEUTTAMINENKK_CRUD", "1.2.246.562.10.2"),
      ("APP_VALINTOJENTOTEUTTAMINENKK_READ_UPDATE", "1.2.246.562.10.2"),
      ("APP_VIESTINVALITYS_OPH_PAAKAYTTAJA", "1.2.246.562.10.00000000001")))
  }

  "Uses the correct kayttooikeus when there are several hakukohde" in {
    val hakukohde1 = defaultHakukohde.copy(organisaatioOiditAuktorisointiin = Set("1.2.246.562.10.1", "1.2.246.562.10.2"))
    val hakukohde2 = defaultHakukohde.copy(organisaatioOiditAuktorisointiin = Set("1.2.246.562.10.1", "1.2.246.562.10.3"))
    val ilmoitus = defaultIlmoitus.copy(hakukohteet = List(hakukohde1, hakukohde2))
    val registry = mockMailPollingWithOneIlmoitus(ilmoitus)

    registry.mailer.sendMailFor(AllQuery)

    registry.viestiCount mustEqual 1
    val viesti = registry.getViestit.head
    viesti.getKayttooikeusRajoitukset.isPresent must beTrue
    // Koska oikeudet on kaikilla organisaatioilla samat, tarkastellaan vain CRUD-oikeuksia
    val crudOikeudet = viesti.getKayttooikeusRajoitukset
      .get()
      .asScala
      .map(k => (k.getOikeus.get(), k.getOrganisaatio.get()))
      .filter(_._1 == "APP_VALINTOJENTOTEUTTAMINEN_CRUD")
    // Kayttöoikeudet asetetaan kaikille hakukohteissa oleville organisaatiolle, mutta jokaiselle vain kerran.
    crudOikeudet must contain(exactly(
      ("APP_VALINTOJENTOTEUTTAMINEN_CRUD", "1.2.246.562.10.1"),
      ("APP_VALINTOJENTOTEUTTAMINEN_CRUD", "1.2.246.562.10.2"),
      ("APP_VALINTOJENTOTEUTTAMINEN_CRUD", "1.2.246.562.10.3")))
  }

  implicit class RegistryOps(registry: EmailerRegistry) {

    def getLahetykset: Iterable[Lahetys] = withFakeClient(_.getLahetykset).values

    def getLahetystunnukset: Iterable[UUID] = withFakeClient(_.getLahetykset.keys)

    def lahetysCount: Int = getLahetykset.size

    def getViestit(lahetystunnus: UUID): Iterable[Viesti] = withFakeClient(_.getViestit(lahetystunnus))

    def getViestit: Iterable[Viesti] = withFakeClient(_.getViestit)

    def viestiCount(): Int = withFakeClient(_.getViestit.size)

    private def withFakeClient[T](f: FakeViestinvalitysClient => T): T =
      f(registry.viestinvalitysClient.asInstanceOf[FakeViestinvalitysClient])
  }
}

class TestAppender extends AppenderSkeleton {
  private var events: List[LoggingEvent] = Nil

  def errors: immutable.Seq[AnyRef] = events.filter { event => List(Level.ERROR, Level.FATAL).contains(event.getLevel) }.map(_.getMessage)

  override def append(event: LoggingEvent): Unit = {
    events = events ++ List(event)
  }

  override def requiresLayout() = false

  override def close() {}
}
