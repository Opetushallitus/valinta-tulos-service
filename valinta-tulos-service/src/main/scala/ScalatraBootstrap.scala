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
import fi.vm.sade.valintatulosservice.hakemus.{AtaruHakemusEnricher, AtaruHakemusRepository, HakemusRepository, HakuAppRepository}
import fi.vm.sade.valintatulosservice.kayttooikeus.KayttooikeusUserDetailsService
import fi.vm.sade.valintatulosservice.kela.{KelaService, VtsKelaAuthenticationClient}
import fi.vm.sade.valintatulosservice.migraatio.valinta.ValintalaskentakoostepalveluService
import fi.vm.sade.valintatulosservice.migraatio.vastaanotot.HakijaResolver
import fi.vm.sade.valintatulosservice.oppijanumerorekisteri.OppijanumerorekisteriService
import fi.vm.sade.valintatulosservice.organisaatio.OrganisaatioService
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.sijoittelu._
import fi.vm.sade.valintatulosservice.sijoittelu.fixture.SijoitteluFixtures
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.tulostenmetsastaja.PuuttuvienTulostenMetsastajaServlet
import fi.vm.sade.valintatulosservice.valintarekisteri.YhdenPaikanSaannos
import fi.vm.sade.valintatulosservice.valintarekisteri.db.MailPollerRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
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

    if (appConfig.isInstanceOf[IT] || appConfig.isInstanceOf[Dev]) {
      context.mount(new FixtureServlet(valintarekisteriDb), "/util")
      SijoitteluFixtures(valintarekisteriDb).importFixture("hyvaksytty-kesken-julkaistavissa.json")
    }
    implicit lazy val dynamicAppConfig = new OhjausparametritAppConfig(appConfig.ohjausparametritService)

    lazy val hakuService = HakuService(appConfig.hakuServiceConfig)
    lazy val oppijanTunnistusService = OppijanTunnistusService(appConfig.settings)
    lazy val organisaatioService = OrganisaatioService(appConfig)
    lazy val valintarekisteriDb = new ValintarekisteriDb(appConfig.settings.valintaRekisteriDbConfig, appConfig.isInstanceOf[IT])
    lazy val hakukohdeRecordService = new HakukohdeRecordService(hakuService, valintarekisteriDb, appConfig.settings.lenientTarjontaDataParsing)
    lazy val sijoitteluService = new SijoitteluService(valintarekisteriDb, authorizer, hakuService)

    lazy val valintarekisteriValintatulosDao = new ValintarekisteriValintatulosDaoImpl(valintarekisteriDb)
    lazy val valintarekisteriRaportointiService = new ValintarekisteriRaportointiServiceImpl(valintarekisteriDb, valintarekisteriValintatulosDao)
    lazy val valintarekisteriSijoittelunTulosClient = new ValintarekisteriSijoittelunTulosClientImpl(valintarekisteriDb)
    lazy val (raportointiService, valintatulosDao, sijoittelunTulosClient, hakijaDTOClient) =
      (valintarekisteriRaportointiService,
        valintarekisteriValintatulosDao,
        valintarekisteriSijoittelunTulosClient,
        new ValintarekisteriHakijaDTOClientImpl(valintarekisteriRaportointiService, valintarekisteriSijoittelunTulosClient, valintarekisteriDb))
    lazy val sijoittelutulosService = new SijoittelutulosService(raportointiService,
        appConfig.ohjausparametritService, valintarekisteriDb, sijoittelunTulosClient)
    lazy val vastaanotettavuusService = new VastaanotettavuusService(hakukohdeRecordService, valintarekisteriDb)
    lazy val hakuAppRepository = new HakuAppRepository()
    lazy val ataruHakemusRepository = new AtaruHakemusRepository(appConfig)
    lazy val oppijanumerorekisteriService = new OppijanumerorekisteriService(appConfig)
    lazy val ataruHakemusTarjontaEnricher = new AtaruHakemusEnricher(hakuService, oppijanumerorekisteriService)
    lazy val hakemusRepository = new HakemusRepository(hakuAppRepository, ataruHakemusRepository, ataruHakemusTarjontaEnricher)
    lazy val valintatulosService = new ValintatulosService(valintarekisteriDb, vastaanotettavuusService, sijoittelutulosService, hakemusRepository, valintarekisteriDb, hakuService, valintarekisteriDb, hakukohdeRecordService, valintatulosDao, hakijaDTOClient)(appConfig,dynamicAppConfig)
    lazy val vastaanottoService = new VastaanottoService(hakuService, hakukohdeRecordService, vastaanotettavuusService, valintatulosService, valintarekisteriDb, appConfig.ohjausparametritService, sijoittelutulosService, hakemusRepository, valintarekisteriDb)
    lazy val ilmoittautumisService = new IlmoittautumisService(valintatulosService, valintarekisteriDb, valintarekisteriDb)
    lazy val mailPollerRepository: MailPollerRepository = valintarekisteriDb
    lazy val mailPoller: MailPollerAdapter = new MailPollerAdapter(mailPollerRepository, valintatulosService, hakuService, hakemusRepository, appConfig.ohjausparametritService, appConfig.settings)

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
    lazy val userDetailsService = new KayttooikeusUserDetailsService(appConfig)
    lazy val hyvaksymiskirjeService = new HyvaksymiskirjeService(valintarekisteriDb, hakuService, audit, authorizer)
    lazy val lukuvuosimaksuService = new LukuvuosimaksuService(valintarekisteriDb, audit)

    val sijoitteluajoDeleteScheduler = new SijoitteluajoDeleteScheduler(valintarekisteriDb, appConfig)
    sijoitteluajoDeleteScheduler.startScheduler()

    mountBasicVts()

    context.mount(new HakukohdeRefreshServlet(valintarekisteriDb, hakukohdeRecordService), "/virkistys")

    context.mount(new SwaggerServlet, "/swagger/*")

    def mountBasicVts(): Unit = {
      context.mount(new BuildInfoServlet, "/")
      context.mount(new CasLogin(
        appConfig.settings.securitySettings.casUrl,
        new CasSessionService(
          appConfig.securityContext.casClient,
          appConfig.securityContext.casServiceIdentifier + "/auth/login",
          userDetailsService,
          valintarekisteriDb
        )
      ), "/auth/login")

      context.mount(new VirkailijanVastaanottoServlet(valintatulosService, vastaanottoService), "/virkailija")
      context.mount(new LukuvuosimaksuServletWithoutCAS(lukuvuosimaksuService), "/lukuvuosimaksu")
      context.mount(handler = new MuutoshistoriaServlet(valinnantulosService, valintarekisteriDb, skipAuditForServiceCall = true), urlPattern = "/muutoshistoria", name = "PrivateMuutosHistoriaServlet")
      context.mount(new PrivateValintatulosServlet(valintatulosService, vastaanottoService, ilmoittautumisService, valintarekisteriDb), "/haku")
      context.mount(new EmailStatusServlet(mailPoller, new MailDecorator(hakuService, oppijanTunnistusService)), "/vastaanottoposti")
      context.mount(new EnsikertalaisuusServlet(valintarekisteriDb, appConfig.settings.valintaRekisteriEnsikertalaisuusMaxPersonOids), "/ensikertalaisuus")
      context.mount(new HakijanVastaanottoServlet(vastaanottoService), "/vastaanotto")
      context.mount(new ErillishakuServlet(valinnantulosService, hyvaksymiskirjeService, userDetailsService), "/erillishaku/valinnan-tulos")
      context.mount(new NoAuthSijoitteluServlet(sijoitteluService), "/sijoittelu")

      val casSessionService = new CasSessionService(
        appConfig.securityContext.casClient,
        appConfig.securityContext.casServiceIdentifier,
        userDetailsService,
        valintarekisteriDb
      )

      context.addFilter("cas", createCasFilter(casSessionService, appConfig.securityContext.requiredRoles))
        .addMappingForUrlPatterns(util.EnumSet.allOf(classOf[DispatcherType]), true, "/cas/haku/*")
      context.addFilter("kelaCas", createCasFilter(casSessionService, Set.empty))
        .addMappingForUrlPatterns(util.EnumSet.allOf(classOf[DispatcherType]), true, "/cas/kela/*")
        context.mount(new PublicValintatulosServlet(valintatulosService, vastaanottoService, ilmoittautumisService, valintarekisteriDb), "/cas/haku")
      context.mount(new KelaServlet(audit, new KelaService(HakijaResolver(appConfig), hakuService, organisaatioService, valintarekisteriDb), valintarekisteriDb), "/cas/kela")
      context.mount(new KelaHealthCheckServlet(audit, valintarekisteriDb, appConfig, new VtsKelaAuthenticationClient(appConfig)), "/health-check/kela")

      val valintaesitysService = new ValintaesitysService(hakuService, authorizer, valintarekisteriDb, valintarekisteriDb, audit)

      context.mount(new ValinnantulosServlet(valinnantulosService, valintatulosService, valintarekisteriDb), "/auth/valinnan-tulos")
      context.mount(new SijoitteluServlet(sijoitteluService, valintarekisteriDb), "/auth/sijoittelu")
      context.mount(new SijoittelunTulosServlet(valintatulosService, valintaesitysService, valinnantulosService, hyvaksymiskirjeService, lukuvuosimaksuService, hakuService, authorizer, sijoitteluService, valintarekisteriDb), "/auth/sijoitteluntulos")
      context.mount(new HyvaksymiskirjeServlet(hyvaksymiskirjeService, valintarekisteriDb), "/auth/hyvaksymiskirje")
      context.mount(new LukuvuosimaksuServletWithCAS(lukuvuosimaksuService, valintarekisteriDb, hakuService, authorizer), "/auth/lukuvuosimaksu")
      context.mount(handler = new MuutoshistoriaServlet(valinnantulosService, valintarekisteriDb), urlPattern = "/auth/muutoshistoria", name = "PublicMuutosHistoriaServlet")
      context.mount(new ValintaesitysServlet(valintaesitysService, valintarekisteriDb), "/auth/valintaesitys")
      context.mount(new PuuttuvienTulostenMetsastajaServlet(valintarekisteriDb, hakuAppRepository, appConfig.properties("host.virkailija")), "/auth/puuttuvat")
      context.mount(new PublicEmailStatusServlet(mailPoller, valintarekisteriDb), "/auth/vastaanottoposti")
    }
  }

  def createCasFilter(casSessionService: CasSessionService, roles: Set[Role]): CasFilter =
    new CasFilter(casSessionService, roles)


  override def destroy(context: ServletContext) = {
    super.destroy(context)
  }
}
