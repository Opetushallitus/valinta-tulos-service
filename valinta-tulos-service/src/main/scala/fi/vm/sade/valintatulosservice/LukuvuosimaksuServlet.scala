package fi.vm.sade.valintatulosservice

import java.util.Date

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.domain.Vastaanotettavuustila
import fi.vm.sade.valintatulosservice.lukuvuosimaksut.{LukuvuosimaksuMuutos, Maksuntila, Lukuvuosimaksu}
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import org.json4s.ext.EnumNameSerializer
import org.json4s.{DefaultFormats, JValue}
import org.scalatra.{InternalServerError, NoContent, Ok}
import org.scalatra.swagger.Swagger

import scala.util.Try

class LukuvuosimaksuServlet(val sessionRepository: SessionRepository)(implicit val swagger: Swagger, appConfig: VtsAppConfig) extends VtsServletBase
  with CasAuthenticatedServlet {

  implicit val defaultFormats = DefaultFormats + new EnumNameSerializer(Maksuntila)

  override val applicationName = Some("auth/lukuvuosimaksut")

  override protected def applicationDescription: String = "Lukuvuosimaksut REST API"

  private def authenticatedPersonOid: String = {
    implicit val authenticated = authenticate
    authorize(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)
    authenticate.session.personOid
  }

  get("/:hakukohdeOid") {
    val personOid = authenticatedPersonOid
    val hakukohdeOid: String = params("hakukohdeOid")

    Ok(db.filter(_.hakukohdeOid.equals(hakukohdeOid)))
  }

  private var db: List[Lukuvuosimaksu] = Nil

  post("/") {
    val muokkaaja = authenticatedPersonOid

    Try(parsedBody.extract[List[LukuvuosimaksuMuutos]]).getOrElse(Nil) match {
      case eimaksuja if eimaksuja.isEmpty =>
        InternalServerError("No 'lukuvuosimaksuja' in request body!")
      case lukuvuosimaksut =>
        db = db ++ lukuvuosimaksut.map(m => Lukuvuosimaksu(m.personOid,m.hakukohdeOid,m.maksuntila, muokkaaja, new Date))
        NoContent()
    }
  }
}
