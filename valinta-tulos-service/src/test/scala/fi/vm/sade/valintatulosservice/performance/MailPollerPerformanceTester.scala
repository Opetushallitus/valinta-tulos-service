package fi.vm.sade.valintatulosservice.performance

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.ValintatulosService
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.config.{VtsAppConfig, VtsDynamicAppConfig}
import fi.vm.sade.valintatulosservice.sijoittelu.legacymongo.{DirectMongoSijoittelunTulosRestClient, SijoitteluSpringContext, StreamingHakijaDtoClient}
import fi.vm.sade.valintatulosservice.sijoittelu.SijoittelutulosService
import fi.vm.sade.valintatulosservice.tarjonta.{HakuFixtures, HakuService}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakuOid
import fi.vm.sade.valintatulosservice.vastaanottomeili.{HakemusMailStatus, LahetysKuittaus, MailPoller, ValintatulosMongoCollection}

object MailPollerPerformanceTester extends App with Logging {
  implicit val appConfig: VtsAppConfig = new VtsAppConfig.Dev
  implicit val dynamicAppConfig: VtsDynamicAppConfig = VtsAppConfig.MockDynamicAppConfig()
  val hakuService = HakuService(appConfig.hakuServiceConfig)
  lazy val sijoitteluContext = new SijoitteluSpringContext(appConfig, SijoitteluSpringContext.createApplicationContext(appConfig))
  lazy val sijoittelutulosService = new SijoittelutulosService(sijoitteluContext.raportointiService,
    appConfig.ohjausparametritService, null, new DirectMongoSijoittelunTulosRestClient(sijoitteluContext, appConfig))
  lazy val valintatulosService = new ValintatulosService(null, sijoittelutulosService, null, hakuService, null, null, null, new StreamingHakijaDtoClient(appConfig))
  lazy val valintatulokset = new ValintatulosMongoCollection(appConfig.settings.valintatulosMongoConfig)
  lazy val mailPoller = new MailPoller(valintatulokset, valintatulosService, null, hakuService, appConfig.ohjausparametritService, limit = 1000)

  HakuFixtures.useFixture(HakuFixtures.korkeakouluYhteishaku, List(HakuOid("1")))

  while(true) {
    logger.info("Polling for mailables...")
    val mailables: List[HakemusMailStatus] = mailPoller.pollForMailables()
    logger.info("Got " + mailables.size)
    mailables.toStream
      .map(mailable => LahetysKuittaus(mailable.hakemusOid, mailable.hakukohteet.map(_.hakukohdeOid), List("email")))
      .foreach(valintatulokset.markAsSent(_))
    logger.info("Marked as sent")
  }

}
