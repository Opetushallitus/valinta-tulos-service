package fi.vm.sade.valintatulosservice.performance

import fi.vm.sade.utils.Timer
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.config.{VtsAppConfig, VtsDynamicAppConfig}
import fi.vm.sade.valintatulosservice.sijoittelu.SijoittelutulosService
import fi.vm.sade.valintatulosservice.sijoittelu.legacymongo.{DirectMongoSijoittelunTulosRestClient, SijoitteluSpringContext, StreamingHakijaDtoClient}
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakemusMailStatus
import fi.vm.sade.valintatulosservice.vastaanottomeili.{MailPollerAdapter, ValintatulosMongoCollection}
import fi.vm.sade.valintatulosservice.{TimeWarp, ValintatulosService}

object PollerTester extends App with Logging with TimeWarp {
  implicit val appConfig: VtsAppConfig = VtsAppConfig.fromSystemProperty
  implicit val dynamicAppConfig: VtsDynamicAppConfig = VtsAppConfig.MockDynamicAppConfig()
  appConfig.start
  lazy val sijoitteluContext = new SijoitteluSpringContext(appConfig, SijoitteluSpringContext.createApplicationContext(appConfig))

  val hakuService = HakuService(appConfig.hakuServiceConfig)
  lazy val sijoittelutulosService = new SijoittelutulosService(sijoitteluContext.raportointiService,
    appConfig.ohjausparametritService, null, new DirectMongoSijoittelunTulosRestClient(sijoitteluContext, appConfig))
  lazy val valintatulosService = new ValintatulosService(null, sijoittelutulosService, null, hakuService, null, null, null, new StreamingHakijaDtoClient(appConfig))
  lazy val valintatulokset = new ValintatulosMongoCollection(appConfig.settings.valintatulosMongoConfig)
  val poller = new MailPollerAdapter(valintatulokset, valintatulosService, null, hakuService, appConfig.ohjausparametritService, limit = 100)

  logger.info("Polling...")
  var total = 0
  var added = 0

  //withFixedDateTime("22.11.2014 15:00") {
    do {
      val mailables: List[HakemusMailStatus] = Timer.timed("pollForMailables") {
        poller.pollForMailables()
      }
      added = mailables.size
      total = total + added
      mailables.foreach { mailable =>
        println(mailable)
      }
      logger.info("Got mailables: " + added + ", total so far "+ total)

    } while (added > 0)
  //}
}
