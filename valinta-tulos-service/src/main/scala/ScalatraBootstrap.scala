import fi.vm.sade.auditlog.{ApplicationType, Audit, Logger}
import fi.vm.sade.openapi.OpenAPIServlet
import fi.vm.sade.oppijantunnistus.OppijanTunnistusService
import fi.vm.sade.security._
import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.config.EmailerRegistry.EmailerRegistry
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.{Dev, IT, VtsAppConfig}
import fi.vm.sade.valintatulosservice.config.{EmailerRegistry, StubbedExternalDeps, VtsAppConfig}
import fi.vm.sade.valintatulosservice.ensikertalaisuus.EnsikertalaisuusServlet
import fi.vm.sade.valintatulosservice.hakemus.{AtaruHakemusEnricher, AtaruHakemusRepository, HakemusRepository, HakuAppRepository}
import fi.vm.sade.valintatulosservice.hakukohderyhmat.HakukohderyhmaService
import fi.vm.sade.valintatulosservice.kayttooikeus.KayttooikeusUserDetailsService
import fi.vm.sade.valintatulosservice.kela.KelaService
import fi.vm.sade.valintatulosservice.koodisto.{CachedKoodistoService, RemoteKoodistoService, StubbedKoodistoService}
import fi.vm.sade.valintatulosservice.logging.Logging
import fi.vm.sade.valintatulosservice.migraatio.vastaanotot.HakijaResolver
import fi.vm.sade.valintatulosservice.migri.MigriService
import fi.vm.sade.valintatulosservice.ohjausparametrit.{CachedOhjausparametritService, RemoteOhjausparametritService, StubbedOhjausparametritService}
import fi.vm.sade.valintatulosservice.oppijanumerorekisteri.OppijanumerorekisteriService
import fi.vm.sade.valintatulosservice.organisaatio.OrganisaatioService
import fi.vm.sade.valintatulosservice.ovara.{SiirtotiedostoPalveluClient, SiirtotiedostoService, SiirtotiedostoServlet}
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.sijoittelu._
import fi.vm.sade.valintatulosservice.sijoittelu.fixture.SijoitteluFixtures
import fi.vm.sade.valintatulosservice.streamingresults.{HakemustenTulosHakuLock, StreamingValintatulosService}
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.tulostenmetsastaja.PuuttuvienTulostenMetsastajaServlet
import fi.vm.sade.valintatulosservice.valintarekisteri.YhdenPaikanSaannos
import fi.vm.sade.valintatulosservice.valintarekisteri.db.MailPollerRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import fi.vm.sade.valintatulosservice.vastaanottomeili._
import org.apache.log4j.LogManager
import org.scalatra._

import java.util
import javax.servlet.{DispatcherType, ServletContext}

class ScalatraBootstrap extends LifeCycle with Logging {

  implicit val swagger: ValintatulosSwagger = new ValintatulosSwagger

  var globalConfig: Option[VtsAppConfig] = None

  override def init(context: ServletContext) {
    val auditLogger = new Logger {
      private val logger = LogManager.getLogger(classOf[Audit])
      override def log(msg: String): Unit = logger.info(msg)
    }
    val audit = new Audit(auditLogger, "valinta-tulos-service", ApplicationType.BACKEND)
    implicit val appConfig: VtsAppConfig = VtsAppConfig.fromOptionalString(Option(context.getAttribute("valintatulos.profile").asInstanceOf[String]))
    globalConfig = Some(appConfig)
    appConfig.start

    def isTrue(string:String) = null != string && "true".equalsIgnoreCase(string)

    context.setInitParameter(org.scalatra.EnvironmentKey, "production")
    context.setInitParameter(org.scalatra.CorsSupport.EnableKey, "false")
    if (appConfig.isInstanceOf[IT] || appConfig.isInstanceOf[Dev]) {
      context.mount(new FixtureServlet(valintarekisteriDb), "/util")
      SijoitteluFixtures(valintarekisteriDb).importFixture("hyvaksytty-kesken-julkaistavissa.json")
      SijoitteluFixtures(valintarekisteriDb).importFixture("yksi-lisajono.json")
    }

    lazy val organisaatioService = OrganisaatioService(appConfig)
    lazy val koodistoService = if (appConfig.isInstanceOf[StubbedExternalDeps]) {
      new StubbedKoodistoService
    } else {
      new CachedKoodistoService(new RemoteKoodistoService(appConfig))
    }

    lazy val ohjausparametritService = if (appConfig.isInstanceOf[StubbedExternalDeps]) {
      new StubbedOhjausparametritService
    } else {
      new RemoteOhjausparametritService(appConfig)
    }
    lazy val cachedOhjausparametritService = if (appConfig.isInstanceOf[StubbedExternalDeps]) {
      ohjausparametritService
    } else {
      new CachedOhjausparametritService(appConfig, ohjausparametritService)
    }
    lazy val hakuService = HakuService(appConfig, ohjausparametritService, organisaatioService, koodistoService)
    lazy val oppijanTunnistusService = OppijanTunnistusService(appConfig)
    lazy val valintarekisteriDb = new ValintarekisteriDb(appConfig.settings.valintaRekisteriDbConfig, appConfig.isInstanceOf[IT])
    lazy val hakukohdeRecordService = new HakukohdeRecordService(hakuService, valintarekisteriDb, appConfig.settings.lenientTarjontaDataParsing)
    lazy val sijoitteluService = new SijoitteluService(valintarekisteriDb, authorizer, hakuService, audit)

    lazy val siirtotiedostoClient = new SiirtotiedostoPalveluClient(appConfig.settings.siirtotiedostoConfig)

    lazy val valintarekisteriValintatulosDao = new ValintarekisteriValintatulosDaoImpl(valintarekisteriDb)
    lazy val valintarekisteriRaportointiService = new ValintarekisteriRaportointiServiceImpl(valintarekisteriDb, valintarekisteriValintatulosDao)
    lazy val valintarekisteriSijoittelunTulosClient = new ValintarekisteriSijoittelunTulosClientImpl(valintarekisteriDb)
    lazy val (raportointiService, valintatulosDao, sijoittelunTulosClient, hakijaDTOClient) =
      (valintarekisteriRaportointiService,
        valintarekisteriValintatulosDao,
        valintarekisteriSijoittelunTulosClient,
        new ValintarekisteriHakijaDTOClientImpl(valintarekisteriRaportointiService, valintarekisteriSijoittelunTulosClient, valintarekisteriDb))
    lazy val sijoittelutulosService = new SijoittelutulosService(raportointiService, cachedOhjausparametritService, valintarekisteriDb, sijoittelunTulosClient)
    lazy val hakuAppRepository = new HakuAppRepository()
    lazy val ataruHakemusRepository = new AtaruHakemusRepository(appConfig)
    lazy val oppijanumerorekisteriService = new OppijanumerorekisteriService(appConfig)
    lazy val ataruHakemusTarjontaEnricher = new AtaruHakemusEnricher(appConfig, hakuService, oppijanumerorekisteriService)
    lazy val hakemusRepository = new HakemusRepository(hakuAppRepository, ataruHakemusRepository, ataruHakemusTarjontaEnricher)
    lazy val valintatulosService = new ValintatulosService(valintarekisteriDb, sijoittelutulosService, hakemusRepository, valintarekisteriDb, cachedOhjausparametritService, hakuService, valintarekisteriDb, hakukohdeRecordService, valintatulosDao, koodistoService)(appConfig)
    lazy val streamingValintatulosService = new StreamingValintatulosService(valintatulosService, valintarekisteriDb, hakijaDTOClient)(appConfig)
    lazy val vastaanottoService = new VastaanottoService(hakuService, hakukohdeRecordService, valintatulosService, valintarekisteriDb, cachedOhjausparametritService, sijoittelutulosService, hakemusRepository, valintarekisteriDb)
    lazy val ilmoittautumisService = new IlmoittautumisService(valintatulosService, valintarekisteriDb, valintarekisteriDb)
    lazy val hakukohderyhmaService = new HakukohderyhmaService(appConfig)
    lazy val siirtotiedostoService = new SiirtotiedostoService(valintarekisteriDb, siirtotiedostoClient, appConfig.settings.siirtotiedostoConfig)

    lazy val authorizer = new OrganizationHierarchyAuthorizer(appConfig, hakukohderyhmaService)
    lazy val yhdenPaikanSaannos = new YhdenPaikanSaannos(hakuService, valintarekisteriDb)
    lazy val valinnantulosService = new ValinnantulosService(
        valintarekisteriDb,
        authorizer,
        hakuService,
        cachedOhjausparametritService,
        hakukohdeRecordService,
        appConfig.valintaPerusteetService,
        yhdenPaikanSaannos,
        appConfig,
        audit,
      hakemusRepository)
    lazy val valintojenToteuttaminenService = new ValintojenToteuttaminenService(valintarekisteriDb)
    lazy val userDetailsService = new KayttooikeusUserDetailsService(appConfig)
    lazy val hyvaksymiskirjeService = new HyvaksymiskirjeService(valintarekisteriDb, hakuService, audit, authorizer)
    lazy val lukuvuosimaksuService = new LukuvuosimaksuService(valintarekisteriDb, audit)
    lazy val hakemustenTulosHakuLock: HakemustenTulosHakuLock = new HakemustenTulosHakuLock(appConfig.settings.hakuResultsLoadingLockQueueLimit, appConfig.settings.hakuResultsLoadingLockSeconds)

    val sijoitteluajoDeleteScheduler = new SijoitteluajoDeleteScheduler(valintarekisteriDb, appConfig)
    sijoitteluajoDeleteScheduler.startScheduler()

    mountBasicVts()

    context.mount(new HakukohdeRefreshServlet(valintarekisteriDb, hakukohdeRecordService), "/virkistys")

    context.mount(new OpenAPIServlet(appConfig), "/swagger/open-api", "openapi")
    context.mount(new SwaggerServlet, "/swagger/*", "swagger")

    def mountBasicVts(): Unit = {
      context.mount(new BuildInfoServlet, "/", "buildinfoservlet")
      context.mount(new CasLogin(
        appConfig.settings.securitySettings.casUrl,
        new CasSessionService(
          appConfig,
          appConfig.securityContext,
          appConfig.securityContext.casServiceIdentifier + "/auth/login",
          valintarekisteriDb
        )
      ), "/auth/login", "auth/login")

      context.mount(new VirkailijanVastaanottoServlet(valintatulosService, vastaanottoService), "/virkailija", "virkailija")
      context.mount(new LukuvuosimaksuServletWithoutCAS(lukuvuosimaksuService), "/lukuvuosimaksu", "lukuvuosimaksu")
      context.mount(handler = new MuutoshistoriaServlet(valinnantulosService, valintarekisteriDb, skipAuditForServiceCall = true), urlPattern = "/muutoshistoria", name = "muutoshistoria")
      context.mount(new PrivateValintatulosServlet(valintatulosService,
        streamingValintatulosService,
        vastaanottoService,
        ilmoittautumisService,
        valintarekisteriDb,
        hakemustenTulosHakuLock),
        "/haku", "haku")
      context.mount(new EnsikertalaisuusServlet(valintarekisteriDb, appConfig.settings.valintaRekisteriEnsikertalaisuusMaxPersonOids), "/ensikertalaisuus", "ensikertalaisuus")
      context.mount(new HakijanVastaanottoServlet(vastaanottoService), "/vastaanotto", "vastaanotto")
      context.mount(new ErillishakuServlet(valinnantulosService, hyvaksymiskirjeService, userDetailsService, appConfig), "/erillishaku/valinnan-tulos", "erillishaku/valinnan-tulos")
      context.mount(new NoAuthSijoitteluServlet(sijoitteluService), "/sijoittelu", "sijoittelu")
      context.mount(new NoAuthHyvaksynnanEhtoServlet(valintarekisteriDb), "/hyvaksynnan-ehto", "hyvaksynnan-ehto")

      context.mount(new SiirtotiedostoServlet(siirtotiedostoService, valintarekisteriDb), "/cas/siirtotiedosto", "siirtotiedosto")

      val casSessionService = new CasSessionService(
        appConfig,
        appConfig.securityContext,
        appConfig.securityContext.casServiceIdentifier,
        valintarekisteriDb
      )

      context.addFilter("cas", createCasFilter(casSessionService, appConfig.securityContext.requiredRoles))
        .addMappingForUrlPatterns(util.EnumSet.allOf(classOf[DispatcherType]), true, "/cas/haku/*")
      context.addFilter("kelaCas", createCasFilter(casSessionService, Set.empty))
        .addMappingForUrlPatterns(util.EnumSet.allOf(classOf[DispatcherType]), true, "/cas/kela/*")
      context.mount(new PublicValintatulosServlet(audit,
        valintatulosService,
        streamingValintatulosService,
        vastaanottoService,
        ilmoittautumisService,
        valintarekisteriDb,
        valintarekisteriDb,
        hakemustenTulosHakuLock
      ),
        "/cas/haku", "cas/haku")
      context.mount(new KelaServlet(audit, new KelaService(HakijaResolver(appConfig), hakuService, valintarekisteriDb), valintarekisteriDb), "/cas/kela", "cas/kela")
      context.mount(new MigriServlet(audit, new MigriService(hakemusRepository, hakuService, valinnantulosService, oppijanumerorekisteriService, valintarekisteriDb, lukuvuosimaksuService, HakijaResolver(appConfig)), valintarekisteriDb), "/cas/migri", "cas/migri")

      val valintaesitysService = new ValintaesitysService(hakuService, authorizer, valintarekisteriDb, valintarekisteriDb, audit)

      context.mount(new ValinnantulosServlet(valinnantulosService, valintatulosService, hakuService, valintarekisteriDb, appConfig), "/auth/valinnan-tulos", "auth/valinnan-tulos")
      context.mount(new SijoitteluServlet(sijoitteluService, valintarekisteriDb), "/auth/sijoittelu", "auth/sijoittelu")
      context.mount(new SijoittelunTulosServlet(valintatulosService, valintaesitysService, valinnantulosService, hyvaksymiskirjeService, lukuvuosimaksuService, hakuService, authorizer, sijoitteluService, valintarekisteriDb), "/auth/sijoitteluntulos", "auth/sijoitteluntulos")
      context.mount(new ValintojenToteuttaminenServlet(valintojenToteuttaminenService, valintarekisteriDb), "/auth/valintojen-toteuttaminen", "/auth/valintojen-toteuttaminen")
      context.mount(new HyvaksymiskirjeServlet(hyvaksymiskirjeService, valintarekisteriDb), "/auth/hyvaksymiskirje", "auth/hyvaksymiskirje")
      context.mount(new LukuvuosimaksuServletWithCAS(lukuvuosimaksuService, valintarekisteriDb, hakuService, authorizer), "/auth/lukuvuosimaksu", "auth/lukuvuosimaksu")
      context.mount(handler = new MuutoshistoriaServlet(valinnantulosService, valintarekisteriDb), urlPattern = "/auth/muutoshistoria", name = "auth/muutoshistoria")
      context.mount(new ValintaesitysServlet(valintaesitysService, valintarekisteriDb), "/auth/valintaesitys", "auth/valintaesitys")
      context.mount(new PuuttuvienTulostenMetsastajaServlet(audit, valintarekisteriDb, hakuAppRepository, appConfig.properties("host.virkailija")), "/auth/puuttuvat", "auth/puuttuvat")
      context.mount(new HyvaksynnanEhtoServlet(valintarekisteriDb, hakuService, hakemusRepository, authorizer, audit, valintarekisteriDb), "/auth/hyvaksynnan-ehto", "auth/hyvaksynnan-ehto")
      context.mount(new HyvaksynnanEhtoMuutoshistoriaServlet(valintarekisteriDb, hakuService, hakemusRepository, authorizer, audit, valintarekisteriDb), "/auth/hyvaksynnan-ehto-muutoshistoria", "auth/hyvaksynnan-ehto-muutoshistoria")

      lazy val mailPollerRepository: MailPollerRepository = valintarekisteriDb
      lazy val mailPoller: MailPoller = new MailPoller(mailPollerRepository, valintatulosService, hakuService, hakemusRepository, cachedOhjausparametritService, appConfig.settings)
      lazy val mailDecorator: MailDecorator = new MailDecorator(hakuService, oppijanTunnistusService, cachedOhjausparametritService)
      context.mount(new PublicEmailStatusServlet(mailPoller, valintarekisteriDb, audit), "/auth/vastaanottoposti", "auth/vastaanottoposti")

      val registry: EmailerRegistry = EmailerRegistry.fromString(Option(System.getProperty("vtemailer.profile")).getOrElse(if (appConfig.isInstanceOf[IT]) "it" else "default"))(mailPoller, mailDecorator)
      val emailerService = new EmailerService(registry, valintarekisteriDb, appConfig.settings.emailerCronString)
      context.mount(new EmailerServlet(emailerService, valintarekisteriDb, audit), "/auth/emailer", "auth/emailer")
    }
  }

  def createCasFilter(casSessionService: CasSessionService, roles: Set[Role]): CasFilter =
    new CasFilter(casSessionService, roles)


  override def destroy(context: ServletContext) = {
    super.destroy(context)
  }
}
