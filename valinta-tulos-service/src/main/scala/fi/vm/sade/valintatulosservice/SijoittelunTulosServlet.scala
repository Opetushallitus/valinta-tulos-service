package fi.vm.sade.valintatulosservice

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId, ZonedDateTime}

import com.google.gson.GsonBuilder
import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.sijoittelu.tulos.dto.HakukohdeDTO
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{Hyvaksymiskirje, SessionRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.scalatra.{NotFound, Ok}
import org.scalatra.swagger.{Swagger, SwaggerEngine}

case class SijoittelunTulos(hakukohdeBySijoitteluAjo: HakukohdeDTO, lukuvuosimaksut: Seq[Lukuvuosimaksu], hyvaksymiskirjeet: Set[Hyvaksymiskirje], valinnantulokset: Set[Valinnantulos])

class SijoittelunTulosServlet(valinnantulosService: ValinnantulosService,
                              hyvaksymiskirjeService: HyvaksymiskirjeService,
                              lukuvuosimaksuService: LukuvuosimaksuService, hakuService: HakuService,
                              authorizer: OrganizationHierarchyAuthorizer,
                              sijoitteluService: SijoitteluService, val sessionRepository: SessionRepository)
                             (implicit val swagger: Swagger, appConfig: VtsAppConfig) extends VtsServletBase
  with CasAuthenticatedServlet {

  override val applicationName = Some("auth/sijoitteluntulos")

  override protected def applicationDescription: String = "Sijoittelun Tulos REST API"

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
      val hakukohdeBySijoitteluAjo: HakukohdeDTO = sijoitteluService.getHakukohdeBySijoitteluajo(hakuOid, sijoitteluajoId, hakukohdeOid, authenticated.session)
      val lukuvuosimaksu: Seq[Lukuvuosimaksu] = lukuvuosimaksuService.getLukuvuosimaksut(hakukohdeOid, ai)
      val hyvaksymiskirje: Set[Hyvaksymiskirje] = hyvaksymiskirjeService.getHyvaksymiskirjeet(hakukohdeOid, ai)


      val (lastModified, valinnantulokset) = valinnantulosService.getValinnantuloksetForHakukohde(hakukohdeOid, ai).map(a => (Some(a._1), a._2)).getOrElse((None[Instant], Set()))

      val modifiedHeaders: Map[String, String] = lastModified.map(l => Map("Last-Modified" -> createLastModifiedHeader(l))).getOrElse(Map())

      Ok(gson.toJson(SijoittelunTulos(hakukohdeBySijoitteluAjo,lukuvuosimaksu,hyvaksymiskirje, valinnantulokset)), headers = modifiedHeaders)
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
