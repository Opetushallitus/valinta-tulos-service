package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.config.VtsAppConfig
import fi.vm.sade.valintatulosservice.hakemus.{AtaruHakemus, HakemusFixtures}
import fi.vm.sade.valintatulosservice.ohjausparametrit.OhjausparametritFixtures
import fi.vm.sade.valintatulosservice.oppijanumerorekisteri.Henkilo
import fi.vm.sade.valintatulosservice.sijoittelu.fixture.SijoitteluFixtures
import fi.vm.sade.valintatulosservice.tarjonta.HakuFixtures
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakuOid

trait ITSetup {
  implicit val appConfig = new VtsAppConfig.IT
  val dbConfig = appConfig.settings.valintaRekisteriDbConfig

  lazy val singleConnectionValintarekisteriDb = new ValintarekisteriDb(
    dbConfig.copy(maxConnections = Some(1), minConnections = Some(1)))

  lazy val valintarekisteriDbWithPool = new ValintarekisteriDb(dbConfig)

//  lazy val valintaPerusteetService = new ValintaPerusteetServiceMock

  lazy val hakemusFixtureImporter = HakemusFixtures()(appConfig.settings)

  lazy val sijoitteluFixtures = SijoitteluFixtures(singleConnectionValintarekisteriDb)

  def useFixture(fixtureName: String,
                 extraFixtureNames: List[String] = List(),
                 ohjausparametritFixture: String = OhjausparametritFixtures.vastaanottoLoppuu2030,
                 hakemusFixtures: List[String] = HakemusFixtures.defaultFixtures,
                 hakuFixture: HakuOid = HakuFixtures.korkeakouluYhteishaku,
                 yhdenPaikanSaantoVoimassa: Boolean = false,
                 kktutkintoonJohtava: Boolean = false,
                 clearFixturesInitially: Boolean = true,
                 ataruHakemusFixture: List[AtaruHakemus] = List.empty,
                 ataruHenkiloFixture: List[Henkilo] = List.empty) {

    sijoitteluFixtures.importFixture(fixtureName, clear = clearFixturesInitially, yhdenPaikanSaantoVoimassa = yhdenPaikanSaantoVoimassa, kktutkintoonJohtava = kktutkintoonJohtava)
    extraFixtureNames.map(fixtureName =>
      sijoitteluFixtures.importFixture(fixtureName, clear = false, yhdenPaikanSaantoVoimassa = yhdenPaikanSaantoVoimassa, kktutkintoonJohtava = kktutkintoonJohtava)
    )

    OhjausparametritFixtures.activeFixture = ohjausparametritFixture
    HakuFixtures.useFixture(hakuFixture)
    hakemusFixtureImporter.clear
    hakemusFixtures.foreach(hakemusFixtureImporter.importFixture)

    AtaruApplicationsFixture.fixture = ataruHakemusFixture
    HenkilotFixture.fixture = ataruHenkiloFixture
  }
}
