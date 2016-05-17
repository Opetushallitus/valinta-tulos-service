import java.util
import javax.servlet.{DispatcherType, ServletContext}

import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.config.AppConfig
import fi.vm.sade.valintatulosservice.config.AppConfig._
import fi.vm.sade.valintatulosservice.ensikertalaisuus.EnsikertalaisuusServlet
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.koodisto.KoodistoService
import fi.vm.sade.valintatulosservice.migraatio.MigraatioServlet
import fi.vm.sade.valintatulosservice.sijoittelu.{SijoitteluFixtures, SijoittelutulosService}
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.{HakukohdeRecordService, ValintarekisteriDb}
import fi.vm.sade.valintatulosservice.vastaanottomeili.{MailDecorator, MailPoller, ValintatulosMongoCollection}
import org.scalatra._

class ScalatraBootstrap extends LifeCycle {

  implicit val swagger = new ValintatulosSwagger

  var globalConfig: Option[AppConfig] = None

  override def init(context: ServletContext) {
    implicit val appConfig: AppConfig = AppConfig.fromOptionalString(Option(context.getAttribute("valintatulos.profile").asInstanceOf[String]))
    globalConfig = Some(appConfig)
    appConfig.start
    if (appConfig.isInstanceOf[IT] || appConfig.isInstanceOf[Dev]) {
      context.mount(new FixtureServlet(valintarekisteriDb), "/util")
      SijoitteluFixtures(appConfig.sijoitteluContext.database, valintarekisteriDb).importFixture("hyvaksytty-kesken-julkaistavissa.json")
    }
    lazy val koodistoService = new KoodistoService(appConfig)
    lazy val hakuService = HakuService(koodistoService, appConfig)
    lazy val valintarekisteriDb = new ValintarekisteriDb(appConfig.settings.valintaRekisteriDbConfig)
    lazy val hakukohdeRecordService = new HakukohdeRecordService(hakuService, valintarekisteriDb, appConfig.settings.lenientTarjontaDataParsing)
    lazy val sijoittelutulosService = new SijoittelutulosService(appConfig.sijoitteluContext.raportointiService, appConfig.ohjausparametritService, valintarekisteriDb)
    lazy val vastaanotettavuusService = new VastaanotettavuusService(hakukohdeRecordService, valintarekisteriDb)
    lazy val valintatulosService = new ValintatulosService(vastaanotettavuusService, sijoittelutulosService, valintarekisteriDb, hakuService, hakukohdeRecordService)(appConfig)
    lazy val vastaanottoService = new VastaanottoService(hakuService, hakukohdeRecordService, vastaanotettavuusService, valintatulosService, valintarekisteriDb, valintarekisteriDb,
      appConfig.sijoitteluContext.valintatulosRepository)
    lazy val ilmoittautumisService = new IlmoittautumisService(valintatulosService,
      appConfig.sijoitteluContext.valintatulosRepository, valintarekisteriDb)
    lazy val valintatulosCollection = new ValintatulosMongoCollection(appConfig.settings.valintatulosMongoConfig)
    lazy val mailPoller = new MailPoller(valintatulosCollection, valintatulosService, hakuService, appConfig.ohjausparametritService, limit = 100)



    val migrationMode = System.getProperty("valinta-rekisteri-migration-mode")

    if(null != migrationMode && "true".equalsIgnoreCase(migrationMode)) {
      context.mount(new MigraatioServlet(hakukohdeRecordService, valintarekisteriDb, new HakemusRepository(), appConfig.sijoitteluContext.raportointiService, hakuService), "/migraatio")

    } else {
      context.mount(new BuildInfoServlet, "/")

      context.mount(new VirkailijanVastaanottoServlet(valintatulosService, vastaanottoService), "/virkailija")
      context.mount(new PrivateValintatulosServlet(valintatulosService, vastaanottoService, ilmoittautumisService), "/haku")
      context.mount(new EmailStatusServlet(mailPoller, valintatulosCollection, new MailDecorator(new HakemusRepository(), valintatulosCollection, hakuService)), "/vastaanottoposti")
      context.mount(new EnsikertalaisuusServlet(valintarekisteriDb, appConfig.settings.valintaRekisteriEnsikertalaisuusMaxPersonOids), "/ensikertalaisuus")
      context.mount(new HakijanVastaanottoServlet(vastaanottoService), "/vastaanotto")

      val securityFilter = appConfig.securityContext.securityFilter
      context.addFilter("cas", securityFilter)
        .addMappingForUrlPatterns(util.EnumSet.allOf(classOf[DispatcherType]), true, "/cas/*")
      context.mount(new PublicValintatulosServlet(valintatulosService, vastaanottoService, ilmoittautumisService), "/cas/haku")
    }
    context.mount(new HakukohdeRefreshServlet(valintarekisteriDb, hakukohdeRecordService), "/virkistys")

    context.mount(new SwaggerServlet, "/swagger/*")
  }

  override def destroy(context: ServletContext) = {
    super.destroy(context)
  }
}
