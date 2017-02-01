import java.util
import javax.servlet.{DispatcherType, ServletContext}

import fi.vm.sade.security.{CasLogin, OrganisationHierarchyAuthorizer, OrganizationHierarchyAuthorizer}
import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.config.VtsAppConfig
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.{Dev, IT, VtsAppConfig}
import fi.vm.sade.valintatulosservice.ensikertalaisuus.EnsikertalaisuusServlet
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.migraatio.vastaanotot.MigraatioServlet
import fi.vm.sade.valintatulosservice.sijoittelu.{SijoitteluFixtures, SijoittelunTulosRestClient, SijoittelutulosService}
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu.ValintarekisteriService
import fi.vm.sade.valintatulosservice.vastaanottomeili.{MailDecorator, MailPoller, ValintatulosMongoCollection}
import org.scalatra._

class ScalatraBootstrap extends LifeCycle {

  implicit val swagger = new ValintatulosSwagger

  var globalConfig: Option[VtsAppConfig] = None

  override def init(context: ServletContext) {
    implicit val appConfig: VtsAppConfig = VtsAppConfig.fromOptionalString(Option(context.getAttribute("valintatulos.profile").asInstanceOf[String]))
    globalConfig = Some(appConfig)
    appConfig.start
    if (appConfig.isInstanceOf[IT] || appConfig.isInstanceOf[Dev]) {
      context.mount(new FixtureServlet(valintarekisteriDb), "/util")
      SijoitteluFixtures(appConfig.sijoitteluContext.database, valintarekisteriDb).importFixture("hyvaksytty-kesken-julkaistavissa.json")
    }
    lazy val hakuService = HakuService(appConfig)
    lazy val valintarekisteriDb = new ValintarekisteriDb(appConfig.settings.valintaRekisteriDbConfig, appConfig.isInstanceOf[IT])
    lazy val hakukohdeRecordService = new HakukohdeRecordService(hakuService, valintarekisteriDb, appConfig.settings.lenientTarjontaDataParsing)
    val sijoittelunTulosRestClient = SijoittelunTulosRestClient(appConfig)
    lazy val sijoittelutulosService = new SijoittelutulosService(appConfig.sijoitteluContext.raportointiService,
      appConfig.ohjausparametritService, valintarekisteriDb, sijoittelunTulosRestClient)
    lazy val vastaanotettavuusService = new VastaanotettavuusService(hakukohdeRecordService, valintarekisteriDb)
    lazy val valintatulosService = new ValintatulosService(vastaanotettavuusService, sijoittelutulosService, valintarekisteriDb, hakuService, valintarekisteriDb, hakukohdeRecordService)(appConfig)
    lazy val vastaanottoService = new VastaanottoService(hakuService, hakukohdeRecordService, vastaanotettavuusService, valintatulosService, valintarekisteriDb,
      appConfig.ohjausparametritService, sijoittelutulosService, new HakemusRepository(),
      appConfig.sijoitteluContext.valintatulosRepository)
    lazy val ilmoittautumisService = new IlmoittautumisService(valintatulosService,
      appConfig.sijoitteluContext.valintatulosRepository, valintarekisteriDb, valintarekisteriDb)
    lazy val valintatulosCollection = new ValintatulosMongoCollection(appConfig.settings.valintatulosMongoConfig)
    lazy val mailPoller = new MailPoller(valintatulosCollection, valintatulosService, valintarekisteriDb, hakuService, appConfig.ohjausparametritService, limit = 100)
    lazy val sijoitteluService = new ValintarekisteriService(valintarekisteriDb, hakukohdeRecordService)
    lazy val valinnantulosService = new ValinnantulosService(valintarekisteriDb, new OrganizationHierarchyAuthorizer(appConfig), hakuService, appConfig.ohjausparametritService, appConfig)


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
      context.mount(new SijoitteluServlet(sijoitteluService), "/sijoittelu")

      val securityFilter = appConfig.securityContext.securityFilter
      context.addFilter("cas", securityFilter)
        .addMappingForUrlPatterns(util.EnumSet.allOf(classOf[DispatcherType]), true, "/cas/*")
      context.mount(new PublicValintatulosServlet(valintatulosService, vastaanottoService, ilmoittautumisService), "/cas/haku")

      val loginUrl = s"${appConfig.settings.securitySettings.casUrl}/login?service=${appConfig.securityContext.casServiceIdentifier}"
      context.mount(new CasLogin(
        appConfig.securityContext.casClient,
        appConfig.securityContext.casServiceIdentifier,
        appConfig.securityContext.directoryClient,
        loginUrl,
        valintarekisteriDb
      ), "/auth/login")
      context.mount(new ValinnantulosServlet(valintarekisteriDb, valinnantulosService, ilmoittautumisService, valintarekisteriDb), "/auth/valinnan-tulos")
    }
    context.mount(new HakukohdeRefreshServlet(valintarekisteriDb, hakukohdeRecordService), "/virkistys")

    context.mount(new SwaggerServlet, "/swagger/*")
  }

  override def destroy(context: ServletContext) = {
    super.destroy(context)
  }
}
