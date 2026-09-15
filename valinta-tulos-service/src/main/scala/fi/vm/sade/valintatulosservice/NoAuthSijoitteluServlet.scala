package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.SijoitteluServlet.wrapNotFound
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakuOid, HakukohdeOid, NotFoundException, ValintatapajonoOid}
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger._
import org.scalatra.{NotFound, Ok}

class NoAuthSijoitteluServlet(sijoitteluService: SijoitteluService)
                             (implicit val swagger: Swagger)
  extends VtsServletBase {

  override protected def applicationDescription: String = "Sijoittelun REST API ilman autentikaatiota"

  // TODO: Valintaperusteet-service kutsuu tätä. Se pitäisi vaihtaa käyttämään autentikoitua rajapintaa.
  //       Sitten tämän koko servletin voi poistaa.
  lazy val sijoitteluajoExistsForHakuJonoSwaggerWithoutCas: OperationBuilder = (apiOperation[Unit]("sijoitteluajoExistsForHakuJonoSwaggerWithoutCas")
    summary "Kertoo onko valintatapajonolle suoritettu sijoittelua"
    parameter pathParam[String]("jonoOid").description("Valintatapajonon yksilöllinen tunniste")
    tags "sijoittelu-noauth")
  get("/jono/:jonoOid", operation(sijoitteluajoExistsForHakuJonoSwaggerWithoutCas)) {

    import org.json4s.native.Json
    import org.json4s.DefaultFormats

    val jonoOid = ValintatapajonoOid(params("jonoOid"))
    val isSijoiteltu = sijoitteluService.isJonoSijoiteltu(jonoOid)
    wrapNotFound(() => Ok(Json(DefaultFormats).write(Map("IsSijoiteltu" -> isSijoiteltu))))
  }
}
