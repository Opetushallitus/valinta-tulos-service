package fi.vm.sade.valintatulosservice.performance

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.generatedfixtures.{GeneratedFixture, RandomizedGeneratedHakuFixture}
import fi.vm.sade.valintatulosservice.tarjonta.HakuService

object PerformanceTestFixtureCreator extends App with Logging {
  implicit val appConfig: VtsAppConfig = new VtsAppConfig.Dev
  val hakuService = HakuService(appConfig.hakuServiceConfig)
  appConfig.start

  private val randomData: RandomizedGeneratedHakuFixture = new RandomizedGeneratedHakuFixture(100, 100000, kohteitaPerHakemus = 5)

  new GeneratedFixture(randomData).apply(appConfig)

  logger.info("fixture applied")
}
