package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.valintarekisteri.db.ehdollisestihyvaksyttavissa._
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid}
import org.scalatra._
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder

class NoAuthHyvaksynnanEhtoServlet(hyvaksynnanEhtoRepository: HyvaksynnanEhtoRepository)
                                  (implicit val swagger: Swagger)
  extends VtsServletBase {
  override val applicationDescription = "Hyväksynnän ehto REST API ilman autentikaatiota"

  val hyvaksynnanEhdotHakukohteessaSwagger: OperationBuilder =
    (apiOperation[Map[HakemusOid, HyvaksynnanEhto]]("hyvaksynnanEhdotHakukohteessa")
      summary "Hyväksynnän ehdot hakukohteessa"
      parameter pathParam[String]("hakukohdeOid").description("Hakukohteen OID").required
      tags "hyvaksynnan-ehto")
  get("/hakukohteessa/:hakukohdeOid", operation(hyvaksynnanEhdotHakukohteessaSwagger)) {
    contentType = formats("json")
    val hakukohdeOid = parseHakukohdeOid.fold(throw _, x => x)

    val response = hyvaksynnanEhtoRepository.runBlocking(
      hyvaksynnanEhtoRepository.hyvaksynnanEhtoHakukohteessa(hakukohdeOid))

    response match {
      case Nil => Ok(body = Map.empty)
      case ehdot => Ok(body = ehdot.map(t => t._1 -> t._2).toMap, headers = Map("Last-Modified" -> createLastModifiedHeader(ehdot.map(_._3).max)))
    }
  }
}
