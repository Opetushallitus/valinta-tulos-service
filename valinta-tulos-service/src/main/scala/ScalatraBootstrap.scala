import java.util
import javax.servlet.{DispatcherType, ServletContext}

import fi.vm.sade.auditlog.{ApplicationType, Audit, Logger}
import fi.vm.sade.security._
import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.{Dev, IT, VtsAppConfig}
import fi.vm.sade.valintatulosservice.config.{OhjausparametritAppConfig, VtsAppConfig}
import fi.vm.sade.valintatulosservice.ensikertalaisuus.EnsikertalaisuusServlet
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.kela.KelaService
import fi.vm.sade.valintatulosservice.migraatio.sijoitteluntulos.SijoittelunTulosMigraatioServlet
import fi.vm.sade.valintatulosservice.migraatio.valinta.ValintalaskentakoostepalveluService
import fi.vm.sade.oppijantunnistus.OppijanTunnistusService
import fi.vm.sade.valintatulosservice.migraatio.vastaanotot.HakijaResolver
import fi.vm.sade.valintatulosservice.organisaatio.OrganisaatioService
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.sijoittelu.{SijoitteluFixtures, SijoittelunTulosRestClient, SijoittelutulosService}
import fi.vm.sade.valintatulosservice.tarjonta.{HakuService, TarjontaHakuService}
import fi.vm.sade.valintatulosservice.valintarekisteri.YhdenPaikanSaannos
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu.ValintarekisteriService
import fi.vm.sade.valintatulosservice.vastaanottomeili.{MailDecorator, MailPoller, ValintatulosMongoCollection}
import org.scalatra._
import org.slf4j.LoggerFactory

class ScalatraBootstrap extends LifeCycle {

  implicit val swagger = new ValintatulosSwagger

  var globalConfig: Option[VtsAppConfig] = None

  override def init(context: ServletContext) {
    val auditLogger = new Logger {
      private val logger = LoggerFactory.getLogger(classOf[Audit])
      override def log(msg: String): Unit = logger.info(msg)
    }
    val audit = new Audit(auditLogger, "valinta-tulos-service", ApplicationType.BACKEND)
    implicit val appConfig: VtsAppConfig = VtsAppConfig.fromOptionalString(Option(context.getAttribute("valintatulos.profile").asInstanceOf[String]))
    globalConfig = Some(appConfig)
    appConfig.start
    if (appConfig.isInstanceOf[IT] || appConfig.isInstanceOf[Dev]) {
      context.mount(new FixtureServlet(valintarekisteriDb), "/util")
      SijoitteluFixtures(appConfig.sijoitteluContext.database, valintarekisteriDb).importFixture("hyvaksytty-kesken-julkaistavissa.json")
    }
    implicit lazy val dynamicAppConfig = new OhjausparametritAppConfig(appConfig.ohjausparametritService)
    lazy val hakuService = HakuService(appConfig.hakuServiceConfig)
    lazy val oppijanTunnistusService = OppijanTunnistusService(appConfig.settings)
    lazy val organisaatioService = OrganisaatioService(appConfig)
    lazy val valintarekisteriDb = new ValintarekisteriDb(appConfig.settings.valintaRekisteriDbConfig, appConfig.isInstanceOf[IT])
    lazy val hakukohdeRecordService = new HakukohdeRecordService(hakuService, valintarekisteriDb, appConfig.settings.lenientTarjontaDataParsing)
    val sijoittelunTulosRestClient = SijoittelunTulosRestClient(appConfig)
    lazy val sijoittelutulosService = new SijoittelutulosService(appConfig.sijoitteluContext.raportointiService,
      appConfig.ohjausparametritService, valintarekisteriDb, sijoittelunTulosRestClient)
    lazy val vastaanotettavuusService = new VastaanotettavuusService(hakukohdeRecordService, valintarekisteriDb)
    lazy val valintatulosService = new ValintatulosService(vastaanotettavuusService, sijoittelutulosService, valintarekisteriDb, hakuService, valintarekisteriDb, hakukohdeRecordService)(appConfig,dynamicAppConfig)
    lazy val vastaanottoService = new VastaanottoService(hakuService, hakukohdeRecordService, vastaanotettavuusService, valintatulosService, valintarekisteriDb,
      appConfig.ohjausparametritService, sijoittelutulosService, new HakemusRepository(),
      appConfig.sijoitteluContext.valintatulosRepository)
    lazy val ilmoittautumisService = new IlmoittautumisService(valintatulosService,
      appConfig.sijoitteluContext.valintatulosRepository, valintarekisteriDb, valintarekisteriDb)
    lazy val valintatulosCollection = new ValintatulosMongoCollection(appConfig.settings.valintatulosMongoConfig)
    lazy val mailPoller = new MailPoller(valintatulosCollection, valintatulosService, valintarekisteriDb, hakuService, appConfig.ohjausparametritService, limit = 100)
    lazy val valintarekisteriService = new ValintarekisteriService(valintarekisteriDb, hakukohdeRecordService)
    lazy val authorizer = new OrganizationHierarchyAuthorizer(appConfig)
    lazy val sijoitteluService = new SijoitteluService(valintarekisteriDb, authorizer, hakuService)
    lazy val yhdenPaikanSaannos = new YhdenPaikanSaannos(hakuService, valintarekisteriDb)
    lazy val valinnantulosService = new ValinnantulosService(valintarekisteriDb, authorizer, hakuService, appConfig.ohjausparametritService, hakukohdeRecordService, yhdenPaikanSaannos, appConfig, audit)
    lazy val tarjontaHakuService = new TarjontaHakuService(appConfig.hakuServiceConfig)
    lazy val valintalaskentakoostepalveluService = new ValintalaskentakoostepalveluService(appConfig)
    lazy val ldapUserService = new LdapUserService(appConfig.securityContext.directoryClient)
    lazy val lukuvuosimaksuService = new LukuvuosimaksuService(valintarekisteriDb, audit)

    val migrationMode = System.getProperty("valinta-rekisteri-migration-mode")

    if(null != migrationMode && "true".equalsIgnoreCase(migrationMode)) {
      context.mount(new SijoittelunTulosMigraatioServlet(valintarekisteriDb, valintarekisteriDb, hakukohdeRecordService, tarjontaHakuService, valintalaskentakoostepalveluService), "/sijoittelun-tulos-migraatio")
    } else {
      context.mount(new BuildInfoServlet, "/")
      context.mount(new CasLogin(
        appConfig.settings.securitySettings.casUrl,
        new CasSessionService(
          appConfig.securityContext.casClient,
          appConfig.securityContext.casServiceIdentifier + "/auth/login",
          ldapUserService,
          valintarekisteriDb
        )
      ), "/auth/login")

      context.mount(new VirkailijanVastaanottoServlet(valintatulosService, vastaanottoService), "/virkailija")
      context.mount(new LukuvuosimaksuServletWithoutCAS(lukuvuosimaksuService), "/lukuvuosimaksu")
      context.mount(new PrivateValintatulosServlet(valintatulosService, vastaanottoService, ilmoittautumisService), "/haku")
      context.mount(new EmailStatusServlet(mailPoller, valintatulosCollection, new MailDecorator(new HakemusRepository(), valintatulosCollection, hakuService, oppijanTunnistusService)), "/vastaanottoposti")
      context.mount(new EnsikertalaisuusServlet(valintarekisteriDb, appConfig.settings.valintaRekisteriEnsikertalaisuusMaxPersonOids), "/ensikertalaisuus")
      context.mount(new HakijanVastaanottoServlet(vastaanottoService), "/vastaanotto")
      context.mount(new ErillishakuServlet(valinnantulosService, ldapUserService), "/erillishaku/valinnan-tulos")

      val casSessionService = new CasSessionService(
        appConfig.securityContext.casClient,
        appConfig.securityContext.casServiceIdentifier,
        ldapUserService,
        valintarekisteriDb
      )

      context.addFilter("cas", createCasLdapFilter(casSessionService, appConfig.securityContext.requiredLdapRoles))
        .addMappingForUrlPatterns(util.EnumSet.allOf(classOf[DispatcherType]), true, "/cas/haku/*")
      context.addFilter("kelaCas", createCasLdapFilter(casSessionService, Set.empty))
        .addMappingForUrlPatterns(util.EnumSet.allOf(classOf[DispatcherType]), true, "/cas/kela/*")
        context.mount(new PublicValintatulosServlet(valintatulosService, vastaanottoService, ilmoittautumisService), "/cas/haku")
      context.mount(new KelaServlet(audit, new KelaService(HakijaResolver(appConfig), hakuService, organisaatioService, valintarekisteriDb), valintarekisteriDb), "/cas/kela")

      context.mount(new ValinnantulosServlet(valinnantulosService, valintarekisteriDb), "/auth/valinnan-tulos")
      context.mount(new SijoitteluServlet(sijoitteluService, valintarekisteriService, valintarekisteriDb), "/auth/sijoittelu")
      context.mount(new HyvaksymiskirjeServlet(valintarekisteriDb, hakuService, valintarekisteriDb, authorizer, audit), "/auth/hyvaksymiskirje")
      context.mount(new LukuvuosimaksuServletWithCAS(lukuvuosimaksuService, valintarekisteriDb, hakuService, authorizer), "/auth/lukuvuosimaksu")
    }
    context.mount(new HakukohdeRefreshServlet(valintarekisteriDb, hakukohdeRecordService), "/virkistys")

    context.mount(new SwaggerServlet, "/swagger/*")
  }

  def createCasLdapFilter(casSessionService: CasSessionService, roles: Set[Role]): CasLdapFilter =
    new CasLdapFilter(casSessionService, roles)


  override def destroy(context: ServletContext) = {
    super.destroy(context)
  }
}
