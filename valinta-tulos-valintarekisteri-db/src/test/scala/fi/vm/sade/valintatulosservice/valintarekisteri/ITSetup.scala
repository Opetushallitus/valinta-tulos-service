package fi.vm.sade.valintatulosservice.valintarekisteri

import com.typesafe.config.ConfigValueFactory
import fi.vm.sade.valintatulosservice.config.ValintarekisteriAppConfig
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService

trait ITSetup {
  implicit val appConfig = new ValintarekisteriAppConfig.IT
  val dbConfig = appConfig.settings.valintaRekisteriDbConfig

  lazy val singleConnectionValintarekisteriDb = new ValintarekisteriDb(
    dbConfig.withValue("connectionPool", ConfigValueFactory.fromAnyRef("disabled")))

  lazy val valintarekisteriDbWithPool = new ValintarekisteriDb(dbConfig, true)

  lazy private val hakuService = HakuService(appConfig.hakuServiceConfig)
  lazy val hakukohdeRecordService = new HakukohdeRecordService(hakuService, singleConnectionValintarekisteriDb, appConfig.settings.lenientTarjontaDataParsing)
}
