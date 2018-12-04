package fi.vm.sade.valintatulosservice

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.util.concurrent.{Executors, TimeUnit}

import com.google.gson.GsonBuilder
import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.sijoittelu.tulos.dto.HakukohdeDTO
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

  private implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(8))

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
      val futureSijoittelunTulokset: Future[HakukohdeDTO] = Future { sijoitteluService.getHakukohdeBySijoitteluajo(hakuOid, sijoitteluajoId, hakukohdeOid, authenticated.session, ai) }
      val futureLukuvuosimaksut: Future[Seq[Lukuvuosimaksu]] = Future { lukuvuosimaksuService.getLukuvuosimaksut(hakukohdeOid, ai) }
      val futureHyvaksymiskirjeet: Future[Set[Hyvaksymiskirje]] = Future { hyvaksymiskirjeService.getHyvaksymiskirjeet(hakukohdeOid, ai) }
      val futureValintaesitys: Future[Set[Valintaesitys]] = Future { valintaesitysService.get(hakukohdeOid, ai) }

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

      val rtt = Await.result(resultJson, Duration(1, TimeUnit.MINUTES))
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
