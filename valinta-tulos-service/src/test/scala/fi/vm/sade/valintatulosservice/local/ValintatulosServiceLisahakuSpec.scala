package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.valintatulosservice.domain.Valintatila._
import fi.vm.sade.valintatulosservice.domain.Vastaanotettavuustila.Vastaanotettavuustila
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.sijoittelu.{DirectMongoSijoittelunTulosRestClient, SijoittelutulosService}
import fi.vm.sade.valintatulosservice.tarjonta.{HakuFixtures, HakuService}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila.Vastaanottotila
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import fi.vm.sade.valintatulosservice.{ITSpecification, TimeWarp, ValintatulosService, VastaanotettavuusService}
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ValintatulosServiceLisahakuSpec extends ITSpecification with TimeWarp {

  "ValintaTulosService" should {

    "yhteishaun lisähaku korkeakouluihin" in {
      val hakuFixture = HakuFixtures.korkeakouluLisahaku1
      val hakemusFixtures = List("00000878230")
      "ei tuloksia, ei julkaistavaa" in {
        useFixture("ei-tuloksia.json", hakuFixture = hakuFixture, hakemusFixtures = hakemusFixtures)
        checkHakutoiveState(getHakutoive("1.2.246.562.14.2013120515524070995659"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
        checkHakutoiveState(getHakutoive("1.2.246.562.14.2014022408541751568934"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
      }
      "molemmat vastaanotettavissa" in {
        useFixture("lisahaku-vastaanotettavissa.json", hakuFixture = hakuFixture, hakemusFixtures = hakemusFixtures)
        checkHakutoiveState(getHakutoive("1.2.246.562.14.2013120515524070995659"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.14.2014022408541751568934"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
      }
      "ensimmäinen vastaanotettavissa, toinen ei hyväksytty" in {
        useFixture("lisahaku-vastaanotettavissa-2-ensimmainen.json", hakuFixture = hakuFixture, hakemusFixtures = hakemusFixtures)
        checkHakutoiveState(getHakutoive("1.2.246.562.14.2013120515524070995659"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.14.2014022408541751568934"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
      }
      "toinen vastaanotettavissa, ensimmäinen ei hyväksytty" in {
        useFixture("lisahaku-vastaanotettavissa-2-toinen.json", hakuFixture = hakuFixture, hakemusFixtures = hakemusFixtures)
        checkHakutoiveState(getHakutoive("1.2.246.562.14.2013120515524070995659"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
        checkHakutoiveState(getHakutoive("1.2.246.562.14.2014022408541751568934"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
      }
      "toinen vastaanotettu, ensimmäistä ei voi vastaanottaa" in {
        useFixture("lisahaku-vastaanottanut.json", hakuFixture = hakuFixture, hakemusFixtures = hakemusFixtures, yhdenPaikanSaantoVoimassa = true)
        checkHakutoiveState(getHakutoive("1.2.246.562.14.2013120515524070995659"), Valintatila.peruuntunut, Vastaanottotila.ottanut_vastaan_toisen_paikan, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.14.2014022408541751568934"), Valintatila.hyväksytty, Vastaanottotila.vastaanottanut, Vastaanotettavuustila.ei_vastaanotettavissa, true)
      }
    }
  }

  step(valintarekisteriDb.db.shutdown)

  lazy val hakuService = HakuService(appConfig)
  lazy val valintarekisteriDb = new ValintarekisteriDb(appConfig.settings.valintaRekisteriDbConfig)
  lazy val sijoittelutulosService = new SijoittelutulosService(appConfig.sijoitteluContext.raportointiService, appConfig.ohjausparametritService,
    valintarekisteriDb, new DirectMongoSijoittelunTulosRestClient(appConfig))
  lazy val hakukohdeRecordService = new HakukohdeRecordService(hakuService, valintarekisteriDb, true)
  lazy val vastaanotettavuusService = new VastaanotettavuusService(hakukohdeRecordService, valintarekisteriDb)
  lazy val valintatulosService = new ValintatulosService(vastaanotettavuusService, sijoittelutulosService, valintarekisteriDb, hakuService, valintarekisteriDb, hakukohdeRecordService)

  val hakuOid: String = "korkeakoulu-lisahaku1"
  val hakemusOid: String = "1.2.246.562.11.00000878230"

  def getHakutoive(hakukohdeOidSuffix: String) = hakemuksenTulos.hakutoiveet.find{_.hakukohdeOid.endsWith(hakukohdeOidSuffix)}.get

  def hakemuksenTulos = {
    valintatulosService.hakemuksentulos(hakemusOid).get
  }

  def checkHakutoiveState(hakuToive: Hakutoiveentulos, expectedTila: Valintatila, vastaanottoTila: Vastaanottotila, vastaanotettavuustila: Vastaanotettavuustila, julkaistavissa: Boolean) = {
    (hakuToive.valintatila,hakuToive.vastaanottotila, hakuToive.vastaanotettavuustila, hakuToive.julkaistavissa) must_== (expectedTila, vastaanottoTila, vastaanotettavuustila, julkaistavissa)
  }
}

