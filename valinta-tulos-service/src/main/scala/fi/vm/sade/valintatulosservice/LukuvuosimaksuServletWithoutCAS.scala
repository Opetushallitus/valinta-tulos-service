package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.lukuvuosimaksut.LukuvuosimaksuMuutos
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, Maksuntila}
import org.json4s.Formats
import org.scalatra.Ok
import org.scalatra.swagger.Swagger


class LukuvuosimaksuServletWithoutCAS(lukuvuosimaksuService: LukuvuosimaksuService)
                                  (implicit val swagger: Swagger)
  extends VtsServletBase with AuditInfoParameter {

  implicit val vtsJsonFormats: Formats = JsonFormats.jsonFormats + new Scala213EnumNameSerializer(Maksuntila)

  override protected def applicationDescription: String = "Lukuvuosimaksut unauthenticated REST API"

  // TODO: Sure kutsuu tätä. Endpoint ja samalla koko servlet voidaan poistaa, kun Sure sammutetaan.
  post("/read") {
    val maksuRequest: LukuvuosimaksuBulkReadRequest = parsedBody.extract[LukuvuosimaksuBulkReadRequest]
    Ok(lukuvuosimaksuService.getLukuvuosimaksut(maksuRequest.hakukohdeOids.toSet, getAuditInfo(maksuRequest)))
  }
}

case class LukuvuosimaksuRequest(lukuvuosimaksuMuutokset: List[LukuvuosimaksuMuutos], auditSession: AuditSessionRequest)
  extends RequestWithAuditSession

case class LukuvuosimaksuBulkReadRequest(hakukohdeOids: List[HakukohdeOid], auditSession: AuditSessionRequest)
  extends RequestWithAuditSession
