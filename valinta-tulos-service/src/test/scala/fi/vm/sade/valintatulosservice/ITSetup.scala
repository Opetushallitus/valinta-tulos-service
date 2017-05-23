package fi.vm.sade.valintatulosservice

import com.typesafe.config.ConfigValueFactory
import fi.vm.sade.valintatulosservice.config.{VtsAppConfig, VtsDynamicAppConfig}
import fi.vm.sade.valintatulosservice.hakemus.HakemusFixtures
import fi.vm.sade.valintatulosservice.ohjausparametrit.OhjausparametritFixtures
import fi.vm.sade.valintatulosservice.sijoittelu.fixture.SijoitteluFixtures
import fi.vm.sade.valintatulosservice.sijoittelu.legacymongo.SijoitteluSpringContext
import fi.vm.sade.valintatulosservice.tarjonta.HakuFixtures
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakuOid

trait ITSetup {
  implicit val appConfig = new VtsAppConfig.IT
  implicit val dynamicAppConfig: VtsDynamicAppConfig = VtsAppConfig.MockDynamicAppConfig(näytetäänSiirryKelaanURL= true)
  val dbConfig = appConfig.settings.valintaRekisteriDbConfig
  lazy val sijoitteluContext = new SijoitteluSpringContext(appConfig, SijoitteluSpringContext.createApplicationContext(appConfig)) // TODO: don't use sijoitteluContext in MailPollerSpec

  lazy val singleConnectionValintarekisteriDb = new ValintarekisteriDb(
    dbConfig.copy(maxConnections = Some(1), minConnections = Some(1)))

  lazy val valintarekisteriDbWithPool = new ValintarekisteriDb(dbConfig)

  lazy val hakemusFixtureImporter = HakemusFixtures()(appConfig)

  lazy val sijoitteluFixtures = SijoitteluFixtures(singleConnectionValintarekisteriDb)

  def useFixture(fixtureName: String,
                 extraFixtureNames: List[String] = List(),
                 ohjausparametritFixture: String = OhjausparametritFixtures.vastaanottoLoppuu2100,
                 hakemusFixtures: List[String] = HakemusFixtures.defaultFixtures,
                 hakuFixture: HakuOid = HakuFixtures.korkeakouluYhteishaku,
                 yhdenPaikanSaantoVoimassa: Boolean = false,
                 kktutkintoonJohtava: Boolean = false,
                 clearFixturesInitially: Boolean = true
                ) {

    sijoitteluFixtures.importFixture(fixtureName, clear = clearFixturesInitially, yhdenPaikanSaantoVoimassa = yhdenPaikanSaantoVoimassa, kktutkintoonJohtava = kktutkintoonJohtava)
    extraFixtureNames.map(fixtureName =>
      sijoitteluFixtures.importFixture(fixtureName, clear = false, yhdenPaikanSaantoVoimassa = yhdenPaikanSaantoVoimassa, kktutkintoonJohtava = kktutkintoonJohtava)
    )

    OhjausparametritFixtures.activeFixture = ohjausparametritFixture
    HakuFixtures.useFixture(hakuFixture)
    hakemusFixtureImporter.clear
    hakemusFixtures.foreach(hakemusFixtureImporter.importFixture)
  }
}
