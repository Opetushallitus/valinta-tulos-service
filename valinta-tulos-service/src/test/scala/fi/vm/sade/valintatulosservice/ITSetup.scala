package fi.vm.sade.valintatulosservice

import com.typesafe.config.ConfigValueFactory
import fi.vm.sade.valintatulosservice.config.{VtsDynamicAppConfig, VtsAppConfig}
import fi.vm.sade.valintatulosservice.hakemus.HakemusFixtures
import fi.vm.sade.valintatulosservice.ohjausparametrit.OhjausparametritFixtures
import fi.vm.sade.valintatulosservice.sijoittelu.SijoitteluFixtures
import fi.vm.sade.valintatulosservice.tarjonta.HakuFixtures
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValintarekisteriDb

trait ITSetup {
  implicit val appConfig = new VtsAppConfig.IT
  implicit val dynamicAppConfig: VtsDynamicAppConfig = VtsAppConfig.MockDynamicAppConfig(näytetäänSiirryKelaanURL= false)
  val dbConfig = appConfig.settings.valintaRekisteriDbConfig

  lazy val singleConnectionValintarekisteriDb = new ValintarekisteriDb(
    dbConfig.withValue("connectionPool", ConfigValueFactory.fromAnyRef("disabled")))

  lazy val valintarekisteriDbWithPool = new ValintarekisteriDb(dbConfig)

  lazy val hakemusFixtureImporter = HakemusFixtures()(appConfig)

  lazy val sijoitteluFixtures = SijoitteluFixtures(appConfig.sijoitteluContext.database, singleConnectionValintarekisteriDb)

  def useFixture(fixtureName: String,
                 extraFixtureNames: List[String] = List(),
                 ohjausparametritFixture: String = OhjausparametritFixtures.vastaanottoLoppuu2100,
                 hakemusFixtures: List[String] = HakemusFixtures.defaultFixtures,
                 hakuFixture: String = HakuFixtures.korkeakouluYhteishaku,
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
