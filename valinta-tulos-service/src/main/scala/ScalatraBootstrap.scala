import java.util
import javax.servlet.{DispatcherType, ServletContext}

import fi.vm.sade.auditlog.{ApplicationType, Audit, Logger}
import fi.vm.sade.oppijantunnistus.OppijanTunnistusService
import fi.vm.sade.security._
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.{Dev, IT, VtsAppConfig}
import fi.vm.sade.valintatulosservice.config.{OhjausparametritAppConfig, VtsAppConfig}
import fi.vm.sade.valintatulosservice.ensikertalaisuus.EnsikertalaisuusServlet
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.kela.KelaService
import fi.vm.sade.valintatulosservice.migraatio.sijoitteluntulos.{SijoittelunTulosMigraatioScheduler, SijoittelunTulosMigraatioServlet, SijoitteluntulosMigraatioService}
import fi.vm.sade.valintatulosservice.migraatio.valinta.ValintalaskentakoostepalveluService
import fi.vm.sade.valintatulosservice.migraatio.vastaanotot.HakijaResolver
import fi.vm.sade.valintatulosservice.organisaatio.OrganisaatioService
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.sijoittelu._
import fi.vm.sade.valintatulosservice.sijoittelu.fixture.SijoitteluFixtures
import fi.vm.sade.valintatulosservice.sijoittelu.legacymongo.{SijoitteluContext, SijoitteluSpringContext, SijoittelunTulosRestClient, StreamingHakijaDtoClient}
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.YhdenPaikanSaannos
import fi.vm.sade.valintatulosservice.valintarekisteri.db.MailPollerRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu.ValintarekisteriService
import fi.vm.sade.valintatulosservice.vastaanottomeili._
import org.scalatra._
import org.slf4j.LoggerFactory

class ScalatraBootstrap extends LifeCycle with Logging {

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

    def isTrue(string:String) = null != string && "true".equalsIgnoreCase(string)

    val migrationMode = isTrue(System.getProperty("valinta-rekisteri-migration-mode"))
    val scheduledMigration = isTrue(System.getProperty("valinta-rekisteri-scheduled-migration"))
    val initMongoContext = !appConfig.settings.readFromValintarekisteri
    if(initMongoContext) {
      logger.warn("Initialisoidaan Mongo-context ja luetaan sijoittelun tulokset Mongosta!")
    } else {
      logger.warn("Luetaan sijoittelun tulokset valintarekisteristä! Ei initialisoida sijoittelun Mongo-contextia!")
    }

    if((migrationMode || scheduledMigration) && !initMongoContext) {
      throw new RuntimeException("Migraatio-moodia voi käyttää vain Sijoittelun Mongon kanssa.")
    }

    lazy val sijoitteluContext:SijoitteluContext = if (initMongoContext) {
      new SijoitteluSpringContext(appConfig, SijoitteluSpringContext.createApplicationContext(appConfig))
    } else {
      throw new NotImplementedError("Someone called Sijoittelu Mongo Context when useSijoitteluMongo=false!!!")
    }

    if (appConfig.isInstanceOf[IT] || appConfig.isInstanceOf[Dev]) {
      context.mount(new FixtureServlet(sijoitteluContext, valintarekisteriDb), "/util")
      SijoitteluFixtures(sijoitteluContext.database, valintarekisteriDb).importFixture("hyvaksytty-kesken-julkaistavissa.json")
    }
    implicit lazy val dynamicAppConfig = new OhjausparametritAppConfig(appConfig.ohjausparametritService)

    lazy val hakuService = HakuService(appConfig.hakuServiceConfig)
    lazy val oppijanTunnistusService = OppijanTunnistusService(appConfig.settings)
    lazy val organisaatioService = OrganisaatioService(appConfig)
    lazy val valintarekisteriDb = new ValintarekisteriDb(appConfig.settings.valintaRekisteriDbConfig, appConfig.isInstanceOf[IT])
    lazy val hakukohdeRecordService = new HakukohdeRecordService(hakuService, valintarekisteriDb, appConfig.settings.lenientTarjontaDataParsing)
    lazy val sijoitteluService = new SijoitteluService(valintarekisteriDb, authorizer, hakuService)

    lazy val (raportointiService, valintatulosDao, valintatulosRepository, sijoittelunTulosClient, hakijaDTOClient) = if(initMongoContext) {
      (sijoitteluContext.raportointiService, sijoitteluContext.valintatulosDao, sijoitteluContext.valintatulosRepository,
        SijoittelunTulosRestClient(sijoitteluContext, appConfig), new StreamingHakijaDtoClient(appConfig))
    } else {
      val valintarekisteriValintatulosDao = new ValintarekisteriValintatulosDaoImpl(valintarekisteriDb)
      val valintarekisteriRaportointiService = new ValintarekisteriRaportointiServiceImpl(valintarekisteriDb, valintarekisteriValintatulosDao)
      val valintarekisteriSijoittelunTulosClient = new ValintarekisteriSijoittelunTulosClientImpl(valintarekisteriDb)
      ( valintarekisteriRaportointiService,
        valintarekisteriValintatulosDao,
        new ValintarekisteriValintatulosRepositoryImpl(valintarekisteriValintatulosDao),
        valintarekisteriSijoittelunTulosClient,
        new ValintarekisteriHakijaDTOClientImpl(valintarekisteriRaportointiService, valintarekisteriSijoittelunTulosClient, valintarekisteriDb))
    }

    lazy val sijoittelutulosService = new SijoittelutulosService(raportointiService,
      appConfig.ohjausparametritService, valintarekisteriDb, sijoittelunTulosClient)
    lazy val vastaanotettavuusService = new VastaanotettavuusService(hakukohdeRecordService, valintarekisteriDb)
    lazy val valintatulosService = new ValintatulosService(vastaanotettavuusService, sijoittelutulosService, valintarekisteriDb, hakuService, valintarekisteriDb, hakukohdeRecordService, valintatulosDao, hakijaDTOClient)(appConfig,dynamicAppConfig)
    lazy val vastaanottoService = new VastaanottoService(hakuService, hakukohdeRecordService, vastaanotettavuusService, valintatulosService, valintarekisteriDb,
      appConfig.ohjausparametritService, sijoittelutulosService, new HakemusRepository(), valintatulosRepository)
    lazy val ilmoittautumisService = new IlmoittautumisService(valintatulosService,
      valintatulosRepository, valintarekisteriDb, valintarekisteriDb)
    lazy val valintatulosCollection = new ValintatulosMongoCollection(appConfig.settings.valintatulosMongoConfig)
    lazy val valintarekisteriService = new ValintarekisteriService(valintarekisteriDb, valintarekisteriDb, hakukohdeRecordService)
    lazy val authorizer = new OrganizationHierarchyAuthorizer(appConfig)
    lazy val yhdenPaikanSaannos = new YhdenPaikanSaannos(hakuService, valintarekisteriDb)
    lazy val valinnantulosService = new ValinnantulosService(
      valintarekisteriDb,
      authorizer,
      hakuService,
      appConfig.ohjausparametritService,
      hakukohdeRecordService,
      vastaanottoService,
      yhdenPaikanSaannos,
      appConfig,
      audit)
    lazy val valintalaskentakoostepalveluService = new ValintalaskentakoostepalveluService(appConfig)
    lazy val ldapUserService = new LdapUserService(appConfig.securityContext.directoryClient)
    lazy val hyvaksymiskirjeService = new HyvaksymiskirjeService(valintarekisteriDb, hakuService, audit, authorizer)
    lazy val migraatioService = new SijoitteluntulosMigraatioService(sijoittelunTulosClient, appConfig,
      valintarekisteriDb, hakukohdeRecordService, hakuService, valintalaskentakoostepalveluService, sijoitteluContext)
    lazy val sijoitteluntulosMigraatioScheduler = new SijoittelunTulosMigraatioScheduler(migraatioService, appConfig)
    lazy val lukuvuosimaksuService = new LukuvuosimaksuService(valintarekisteriDb, audit)
    val featureEnabledPostgres = false
    val mailPollerRepository: MailPollerRepository = if (featureEnabledPostgres) {
      valintarekisteriDb
    } else {
      valintatulosCollection
    }
    lazy val mailPoller: MailPollerAdapter =
      new MailPollerAdapter(mailPollerRepository, valintatulosService, valintarekisteriDb, hakuService, appConfig.ohjausparametritService, limit = 100)


    if (scheduledMigration) {
      sijoitteluntulosMigraatioScheduler.startMigrationScheduler()
    }

    if(migrationMode) {
      context.mount(new SijoittelunTulosMigraatioServlet(migraatioService), "/sijoittelun-tulos-migraatio")
      val forceRunningBasicVtsWithMigration = System.getProperty("valinta-rekisteri-force-basic-vts-with-migration")
      if (forceRunningBasicVtsWithMigration != null && "true".equals(forceRunningBasicVtsWithMigration)) {
        mountBasicVts()
      }
    } else {
      mountBasicVts()
    }
    context.mount(new HakukohdeRefreshServlet(valintarekisteriDb, hakukohdeRecordService), "/virkistys")

    context.mount(new SwaggerServlet, "/swagger/*")

    def mountBasicVts(): Unit = {
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
      context.mount(new MuutoshistoriaServlet(valinnantulosService, valintarekisteriDb, skipAuditForServiceCall = true), "/muutoshistoria")
      context.mount(new PrivateValintatulosServlet(valintatulosService, vastaanottoService, ilmoittautumisService), "/haku")
      context.mount(new EmailStatusServlet(mailPoller, new MailDecorator(new HakemusRepository(), mailPollerRepository, hakuService, oppijanTunnistusService)), "/vastaanottoposti")
      context.mount(new EnsikertalaisuusServlet(valintarekisteriDb, appConfig.settings.valintaRekisteriEnsikertalaisuusMaxPersonOids), "/ensikertalaisuus")
      context.mount(new HakijanVastaanottoServlet(vastaanottoService), "/vastaanotto")
      context.mount(new ErillishakuServlet(valinnantulosService, hyvaksymiskirjeService, ldapUserService), "/erillishaku/valinnan-tulos")
      context.mount(new NoAuthSijoitteluServlet(sijoitteluService), "/sijoittelu")

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
      context.mount(new SijoitteluServlet(sijoitteluService, valintarekisteriDb), "/auth/sijoittelu")
      context.mount(new HyvaksymiskirjeServlet(hyvaksymiskirjeService, valintarekisteriDb), "/auth/hyvaksymiskirje")
      context.mount(new LukuvuosimaksuServletWithCAS(lukuvuosimaksuService, valintarekisteriDb, hakuService, authorizer), "/auth/lukuvuosimaksu")
      context.mount(new MuutoshistoriaServlet(valinnantulosService, valintarekisteriDb), "/auth/muutoshistoria")
      context.mount(new ValintaesitysServlet(new ValintaesitysService(hakuService, authorizer, valintarekisteriDb, valintarekisteriDb, audit), valintarekisteriDb), "/auth/valintaesitys")
    }
  }

  def createCasLdapFilter(casSessionService: CasSessionService, roles: Set[Role]): CasLdapFilter =
    new CasLdapFilter(casSessionService, roles)


  override def destroy(context: ServletContext) = {
    super.destroy(context)
  }
}
