package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{
  HakuOid,
  HakukohdeOid,
  NotFoundException,
  ValintatapajonoOid
}
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger._
import org.scalatra.{NotFound, Ok}

class NoAuthSijoitteluServlet(sijoitteluService: SijoitteluService)(implicit val swagger: Swagger)
    extends VtsServletBase {

  override protected def applicationDescription: String =
    "Sijoittelun REST API ilman autentikaatiota"

  lazy val getHakukohdeBySijoitteluajoSwagger: OperationBuilder =
    (apiOperation[Unit]("getHakukohdeBySijoitteluajoSwagger")
      summary "Hakee hakukohteen tiedot tietyssa sijoitteluajossa."
      parameter pathParam[String]("hakuOid").description("Haun yksilöllinen tunniste")
      parameter pathParam[String]("sijoitteluajoId").description(
        "Sijoitteluajon yksilöllinen tunniste, tai 'latest' avainsana."
      )
      parameter pathParam[String]("hakukohdeOid").description("Hakukohteen yksilöllinen tunniste")
      tags "sijoittelu-noauth")
  get(
    "/:hakuOid/sijoitteluajo/:sijoitteluajoId/hakukohde/:hakukohdeOid",
    operation(getHakukohdeBySijoitteluajoSwagger)
  ) {
    val hakuOid = HakuOid(params("hakuOid"))
    val sijoitteluajoId = params("sijoitteluajoId")
    val hakukohdeOid = HakukohdeOid(params("hakukohdeOid"))

    try {
      Ok(
        JsonFormats.javaObjectToJsonString(
          sijoitteluService.getHakukohdeBySijoitteluajoWithoutAuthentication(
            hakuOid,
            sijoitteluajoId,
            hakukohdeOid
          )
        )
      )
    } catch {
      case e: NotFoundException =>
        NotFound(Map("error" -> e.getMessage))
    }
  }

  lazy val sijoitteluajoExistsForHakuJonoSwaggerWithoutCas: OperationBuilder =
    (apiOperation[Unit]("sijoitteluajoExistsForHakuJonoSwaggerWithoutCas")
      summary "Kertoo onko valintatapajonolle suoritettu sijoittelua"
      parameter pathParam[String]("jonoOid").description("Valintatapajonon yksilöllinen tunniste")
      tags "sijoittelu-noauth")
  get("/jono/:jonoOid", operation(sijoitteluajoExistsForHakuJonoSwaggerWithoutCas)) {

    import org.json4s.native.Json
    import org.json4s.DefaultFormats

    val jonoOid = ValintatapajonoOid(params("jonoOid"))
    val isSijoiteltu: Boolean = sijoitteluService.isJonoSijoiteltu(jonoOid)
    Ok(Json(DefaultFormats).write(Map("IsSijoiteltu" -> isSijoiteltu)))
  }
}
