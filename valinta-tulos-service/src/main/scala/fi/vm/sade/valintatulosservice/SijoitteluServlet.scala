package fi.vm.sade.valintatulosservice

import fi.vm.sade.sijoittelu.tulos.dto.SijoitteluajoDTO
import fi.vm.sade.valintatulosservice.SijoitteluServlet.wrapNotFound
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger._
import org.scalatra.{InternalServerError, NoContent, NotFound, Ok}

import scala.util.{Failure, Success, Try}

object SijoitteluServlet {
  def wrapNotFound[T](t: () => T): Any = {
    Try(t()) match {
      case Failure(e) =>
        e match {
          case e: IllegalArgumentException =>
            NotFound(Map("error" -> e.getMessage))
          case e: NotFoundException =>
            NotFound(Map("error" -> e.getMessage))
          case _ =>
            InternalServerError(Map("error" -> e.getMessage))
        }
      case Success(result) =>
        result
    }
  }
}

class SijoitteluServlet(sijoitteluService: SijoitteluService,
                        val sessionRepository: SessionRepository)
                       (implicit val swagger: Swagger, appConfig: VtsAppConfig) extends VtsServletBase
                       with CasAuthenticatedServlet {

  override protected def applicationDescription: String = "Sijoittelun REST API"

  /*lazy val postSijoitteluajoSwagger: OperationBuilder = (apiOperation[Unit]("postSijoitteluajoSwagger")
    summary "Tallentaa sijoitteluajon"
    parameter bodyParam[SijoitteluAjo]("sijoitteluajo").description("Sijoitteluajon data"))
  post("/sijoitteluajo", operation(postSijoitteluajoSwagger)) {
    val sijoitteluajo = read[SijoitteluajoWrapper](request.body)
    Ok(sijoitteluService.luoSijoitteluajo(sijoitteluajo.sijoitteluajo))
  }*/

  lazy val getSijoitteluajoSwagger: OperationBuilder = (apiOperation[Unit]("getSijoitteluajoSwagger")
    summary "Hakee koko sijoitteluajon tiedot."
    parameter pathParam[String]("hakuOid").description("Haun yksilöllinen tunniste") //TODO tarpeeton?
    parameter pathParam[String]("sijoitteluajoId").description("Sijoitteluajon yksilöllinen tunniste, tai 'latest' avainsana.")
    tags "sijoittelu")
  get("/:hakuOid/sijoitteluajo/:sijoitteluajoId", operation(getSijoitteluajoSwagger)) {
    implicit val authenticated = authenticate
    authorize(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)

    val hakuOid = HakuOid(params("hakuOid"))
    val sijoitteluajoId = params("sijoitteluajoId")

    wrapNotFound(() => streamOk(sijoitteluService.getSijoitteluajo(hakuOid, sijoitteluajoId, auditInfo)))
  }

  lazy val getSijoitteluajonPerustiedotSwagger: OperationBuilder = (apiOperation[Unit]("getSijoitteluajoSwagger")
    summary "Hakee sijoitteluajon perustiedot ja hakukohteiden oidit."
    parameter pathParam[String]("hakuOid").description("Haun yksilöllinen tunniste") //TODO tarpeeton?
    parameter pathParam[String]("sijoitteluajoId").description("Sijoitteluajon yksilöllinen tunniste, tai 'latest' avainsana.")
    tags "sijoittelu")
  get("/:hakuOid/sijoitteluajo/:sijoitteluajoId/perustiedot", operation(getSijoitteluajoSwagger)) {
    implicit val authenticated = authenticate
    authorize(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)

    val hakuOid = HakuOid(params("hakuOid"))
    val sijoitteluajoId = params("sijoitteluajoId")

    wrapNotFound(() => Ok(JsonFormats.javaObjectToJsonString(sijoitteluService.getSijoitteluajonPerustiedot(hakuOid, sijoitteluajoId, auditInfo))))
  }

  lazy val getHakemusBySijoitteluajoSwagger: OperationBuilder = (apiOperation[Unit]("getHakemusBySijoitteluajoSwagger")
    summary "Nayttaa yksittaisen hakemuksen kaikki hakutoiveet ja tiedot kaikista valintatapajonoista."
    parameter pathParam[String]("hakuOid").description("Haun yksilöllinen tunniste")
    parameter pathParam[String]("sijoitteluajoId").description("Sijoitteluajon yksilöllinen tunniste, tai 'latest' avainsana.")
    parameter pathParam[String]("hakemusOid").description("Hakemuksen yksilöllinen tunniste")
    tags "sijoittelu")
  get("/:hakuOid/sijoitteluajo/:sijoitteluajoId/hakemus/:hakemusOid", operation(getHakemusBySijoitteluajoSwagger)) {
    implicit val authenticated = authenticate
    authorize(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD) //TODO: organization hierarchy check?!?!
    val hakuOid = HakuOid(params("hakuOid"))
    val sijoitteluajoId = params("sijoitteluajoId")
    val hakemusOid = HakemusOid(params("hakemusOid"))
    wrapNotFound(() => Ok(JsonFormats.javaObjectToJsonString(sijoitteluService.getHakemusBySijoitteluajo(hakuOid, sijoitteluajoId, hakemusOid, auditInfo))))
  }

  lazy val getHakukohdeBySijoitteluajoSwagger: OperationBuilder = (apiOperation[Unit]("getHakukohdeBySijoitteluajoSwagger")
    summary "Hakee hakukohteen tiedot tietyssa sijoitteluajossa."
    parameter pathParam[String]("hakuOid").description("Haun yksilöllinen tunniste")
    parameter pathParam[String]("sijoitteluajoId").description("Sijoitteluajon yksilöllinen tunniste, tai 'latest' avainsana.")
    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen yksilöllinen tunniste")
    tags "sijoittelu")
  get("/:hakuOid/sijoitteluajo/:sijoitteluajoId/hakukohde/:hakukohdeOid", operation(getHakukohdeBySijoitteluajoSwagger)) {
    val hakuOid = HakuOid(params("hakuOid"))
    val sijoitteluajoId = params("sijoitteluajoId")
    val hakukohdeOid = HakukohdeOid(params("hakukohdeOid"))

    implicit val authenticated = authenticate
    authorize(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)

    wrapNotFound(() =>
      Ok(JsonFormats.javaObjectToJsonString(sijoitteluService.getHakukohdeBySijoitteluajo(hakuOid, sijoitteluajoId, hakukohdeOid, authenticated.session, auditInfo))))
  }

  lazy val sijoitteluajoExistsForHakuJonoSwagger: OperationBuilder = (apiOperation[Unit]("sijoitteluajoExistsForHakuJonoSwagger")
    summary "Kertoo onko valintatapajonolle suoritettu sijoittelua"
    parameter pathParam[String]("jonoOid").description("Valintatapajonon yksilöllinen tunniste")
    tags "sijoittelu")
  get("/jono/:jonoOid", operation(sijoitteluajoExistsForHakuJonoSwagger)) {

    import org.json4s.native.Json
    import org.json4s.DefaultFormats

    val jonoOid = ValintatapajonoOid(params("jonoOid"))
    implicit val authenticated = authenticate
    authorize(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)

    val isSijoiteltu = sijoitteluService.isJonoSijoiteltu(jonoOid)
    wrapNotFound(() => Ok(Json(DefaultFormats).write(Map("IsSijoiteltu" -> isSijoiteltu))))
  }
}
