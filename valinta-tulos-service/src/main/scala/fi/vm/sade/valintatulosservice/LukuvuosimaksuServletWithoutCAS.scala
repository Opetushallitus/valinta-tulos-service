package fi.vm.sade.valintatulosservice

import java.util.Date

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.lukuvuosimaksut.LukuvuosimaksuMuutos
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Lukuvuosimaksu, Maksuntila}
import org.json4s.DefaultFormats
import org.json4s.ext.EnumNameSerializer
import org.scalatra.swagger.Swagger
import org.scalatra.{InternalServerError, NoContent, Ok}

import scala.util.Try


class LukuvuosimaksuServletWithoutCAS(lukuvuosimaksuService: LukuvuosimaksuService)
                                  (implicit val swagger: Swagger, appConfig: VtsAppConfig)
  extends VtsServletBase with AuditInfoParameter {

  implicit val defaultFormats = DefaultFormats + new EnumNameSerializer(Maksuntila)

  override val applicationName = Some("lukuvuosimaksut")

  override protected def applicationDescription: String = "Lukuvuosimaksut unauthenticated REST API"

  get("/:hakukohdeOid") {
    val hakukohdeOid = hakukohdeOidParam

    val lukuvuosiRequest = parsedBody.extract[LukuvuosimaksuRequest]
    val auditInfo = getAuditInfo(lukuvuosiRequest)

    val lukuvuosimaksus = lukuvuosimaksuService.getLukuvuosimaksut(hakukohdeOid, auditInfo)
    Ok(lukuvuosimaksus)
  }


  post("/:hakukohdeOid") {
    val hakukohdeOid = hakukohdeOidParam

    val lukuvuosimaksuRequest = parsedBody.extract[LukuvuosimaksuRequest]

    val auditInfo = getAuditInfo(lukuvuosimaksuRequest)

    lukuvuosimaksuRequest.lukuvuosimaksuMuutokset match {
      case lukuvuosimaksuMuutokset if lukuvuosimaksuMuutokset.nonEmpty =>
        val lukuvuosimaksut = lukuvuosimaksuMuutokset.map(m => {
          Lukuvuosimaksu(m.personOid, hakukohdeOid, m.maksuntila, auditInfo.session._2.personOid, new Date)
        })
        lukuvuosimaksuService.updateLukuvuosimaksut(lukuvuosimaksut, auditInfo)

        NoContent()
      case _ =>
        InternalServerError("No 'lukuvuosimaksuja' in request body!")
    }
  }

  private def hakukohdeOidParam: String = {
    Try(params("hakukohdeOid")).toOption.filter(!_.isEmpty)
      .getOrElse(throw new RuntimeException("HakukohdeOid is mandatory!"))
  }

}

case class LukuvuosimaksuRequest(lukuvuosimaksuMuutokset: List[LukuvuosimaksuMuutos], auditSession: AuditSessionRequest)
  extends RequestWithAuditSession
