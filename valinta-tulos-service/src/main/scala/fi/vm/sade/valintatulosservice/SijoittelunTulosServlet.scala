package fi.vm.sade.valintatulosservice

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.util.concurrent.{Executors, TimeUnit}

import com.google.gson.GsonBuilder
import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.sijoittelu.tulos.dto.HakukohdeDTO
import fi.vm.sade.utils.Timer
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{Hyvaksymiskirje, SessionRepository, Valintaesitys}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.scalatra.{NotFound, Ok}
import org.scalatra.swagger.{Swagger, SwaggerEngine}

import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
import scala.concurrent.duration.Duration
import scala.util.Success

class SijoittelunTulosServlet(val valintatulosService: ValintatulosService,
                              valintaesitysService: ValintaesitysService,
                              valinnantulosService: ValinnantulosService,
                              hyvaksymiskirjeService: HyvaksymiskirjeService,
                              lukuvuosimaksuService: LukuvuosimaksuService, hakuService: HakuService,
                              authorizer: OrganizationHierarchyAuthorizer,
                              sijoitteluService: SijoitteluService, val sessionRepository: SessionRepository)
                             (implicit val swagger: Swagger, appConfig: VtsAppConfig) extends VtsServletBase
  with CasAuthenticatedServlet with DeadlineDecorator {

  override val applicationName = Some("auth/sijoitteluntulos")

  override protected def applicationDescription: String = "Sijoittelun Tulos REST API"

  private implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(20))

  val gson = new GsonBuilder().create()

  get("/:hakuOid/sijoitteluajo/:sijoitteluajoId/hakukohde/:hakukohdeOid") {
    implicit val authenticated = authenticate
    val ai: AuditInfo = auditInfo
    val hakuOid = HakuOid(params("hakuOid"))
    val sijoitteluajoId = params("sijoitteluajoId")
    val hakukohdeOid = HakukohdeOid(params("hakukohdeOid"))
    val hakukohde = hakuService.getHakukohde(hakukohdeOid).fold(throw _, h => h)

    authorizer.checkAccess(ai.session._2, hakukohde.tarjoajaOids,
      Set(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)).fold(throw _, x => x)
    try {
      val start = System.currentTimeMillis()
      val futureSijoittelunTulokset: Future[HakukohdeDTO] = Future { Timer.timed("future 1"){
        logger.info("haetaan future 1, aikaa alusta: " + (System.currentTimeMillis() - start))
        sijoitteluService.getHakukohdeBySijoitteluajo(hakuOid, sijoitteluajoId, hakukohdeOid, authenticated.session)} }
      val futureLukuvuosimaksut: Future[Seq[Lukuvuosimaksu]] = Future { Timer.timed("future 2"){
        logger.info("haetaan future 2, aikaa alusta: " + (System.currentTimeMillis() - start))
        lukuvuosimaksuService.getLukuvuosimaksut(hakukohdeOid, ai)} }
      val futureHyvaksymiskirjeet: Future[Set[Hyvaksymiskirje]] = Future { Timer.timed("future 3"){
        logger.info("haetaan future 3, aikaa alusta: " + (System.currentTimeMillis() - start))
        hyvaksymiskirjeService.getHyvaksymiskirjeet(hakukohdeOid, ai)} }
      val futureValintaesitys: Future[Set[Valintaesitys]] = Future { Timer.timed("future 4"){
        logger.info("haetaan future 4, aikaa alusta: " + (System.currentTimeMillis() - start))
        valintaesitysService.get(hakukohdeOid, ai)} }

      futureSijoittelunTulokset.onComplete({
        case Success(s) => logger.info("future 1 valmis. aikaa kulunut alusta: " + (System.currentTimeMillis() - start))
      })
      futureLukuvuosimaksut.onComplete({
        case Success(s) => logger.info("future 2 valmis. aikaa kulunut alusta: " + (System.currentTimeMillis() - start))
      })
      futureHyvaksymiskirjeet.onComplete({
        case Success(s) => logger.info("future 3 valmis. aikaa kulunut alusta: " + (System.currentTimeMillis() - start))
      })
      futureValintaesitys.onComplete({
        case Success(s) => logger.info("future 4 valmis. aikaa kulunut alusta: " + (System.currentTimeMillis() - start))
      })

      val (lastModified, valinnantulokset: Set[Valinnantulos]) = valinnantulosService.getValinnantuloksetForHakukohde(hakukohdeOid, ai).map(a => (Option(a._1), a._2)).getOrElse((None, Set()))
      val modified: String = lastModified.map(createLastModifiedHeader).getOrElse("")
      val valinnantuloksetWithTakarajat: Set[Valinnantulos] = decorateValinnantuloksetWithDeadlines(hakuOid, hakukohdeOid, valinnantulokset)



      val resultJson = for {
        sijoittelunTulokset <- futureSijoittelunTulokset
        lukuvuosimaksut <- futureLukuvuosimaksut
        kirjeet <- futureHyvaksymiskirjeet
        valintaesitys <- futureValintaesitys
      } yield {
        s"""{
           |"valintaesitys":${JsonFormats.formatJson(valintaesitys)},
           |"lastModified":"$modified",
           |"sijoittelunTulokset":${JsonFormats.javaObjectToJsonString(sijoittelunTulokset)},
           |"valintatulokset":${JsonFormats.formatJson(valinnantuloksetWithTakarajat)},
           |"kirjeLahetetty":${JsonFormats.formatJson(kirjeet)},
           |"lukuvuosimaksut":${JsonFormats.formatJson(lukuvuosimaksut)}}""".stripMargin
      }

      logger.info("Jäädään odottamaan futureiden valmistumista. Aikaa kulunut nyt (ms): " + (System.currentTimeMillis() - start))
      val rtt = Await.result(resultJson, Duration(1, TimeUnit.MINUTES))
      logger.info("Kaikki futuret valmiita ja tulokset koottu. Aikaa kulunut nyt (ms): " + (System.currentTimeMillis() - start))
      Ok(rtt)
    } catch {
      case e: NotFoundException =>
        val message = e.getMessage
        NotFound(body = Map("error" -> message), reason = message)
    }
  }

  protected def createLastModifiedHeader(instant: Instant): String = {
    DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant((instant.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusSeconds(1)), ZoneId.of("GMT")))
  }
}
