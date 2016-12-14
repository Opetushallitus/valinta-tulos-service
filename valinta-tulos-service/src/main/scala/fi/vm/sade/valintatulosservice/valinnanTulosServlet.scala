package fi.vm.sade.valintatulosservice
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.util.UUID

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import org.json4s.DefaultFormats
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger.{Swagger, SwaggerSupport}

import scala.util.control.NonFatal

case class ValinnanTulos(hakemusOid: String, ilmoittautumistila: String)

case class ValinnanTulosPatch(hakemusOid: String, ilmoittautumistila: String)

class ValinnanTulosServlet(ilmoittautumisService: IlmoittautumisService, sessionRepository: SessionRepository)
                          (implicit val swagger: Swagger)
  extends ScalatraServlet with JacksonJsonSupport with SwaggerSupport with Logging {

  override val applicationName = Some("auth/valinnan-tulos")
  override val applicationDescription = "Valinnan tuloksen REST API"

  override protected implicit def jsonFormats = DefaultFormats

  error {
    case e: IllegalArgumentException =>
      BadRequest("error" -> s"Bad request. ${e.getMessage}")
    case e: Throwable =>
      InternalServerError("error" -> "internal server error")
  }

  before() {
    if (cookies.get("session").map(UUID.fromString).flatMap(sessionRepository.get).isEmpty) {
      halt(Forbidden("error" -> "forbidden"))
    }
    contentType = formats("json")
  }

  val valinnanTulosSwagger: OperationBuilder = (apiOperation[List[ValinnanTulos]]("valinnanTulos")
    summary "Valinnan tulos"
    parameter pathParam[String]("valintatapajonoOid").description("Valintatapajonon OID")
    )
  get("/:valintatapajonoOid", operation(valinnanTulosSwagger)) {
    val valintatapajonoOid = params("valintatapajonoOid")
    val ilmoittautumiset = ilmoittautumisService.getIlmoittautumistilat(valintatapajonoOid)
    val lastModified = ilmoittautumiset.map(_._3).max
    Ok(
      body = ilmoittautumiset.map(i => ValinnanTulos(i._1, i._2.toString)),
      headers = Map("Last-Modified" -> DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(lastModified, ZoneId.of("GMT"))))
    )
  }

  val sample = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("GMT")))
  val valinnanTuloksenMuutosSwagger: OperationBuilder = (apiOperation[Unit]("muokkaaValinnanTulosta")
    summary "Muokkaa valinnan tulosta"
    parameter pathParam[String]("valintatapajonoOid").description("Valintatapajonon OID")
    parameter headerParam[String]("If-Unmodified-Since").description(s"Timestamp in RFC 1123 format: $sample").required
    parameter bodyParam[List[ValinnanTulosPatch]].description("Muutos valinnan tulokseen").required
    )
  patch("/:valintatapajonoOid", operation(valinnanTuloksenMuutosSwagger)) {
    if (!request.headers.contains("If-Unmodified-Since")) {
      throw new IllegalArgumentException("If-Unmodified-Since required.")
    }
    val ifUnmodifiedSince = try {
      Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(request.headers("If-Unmodified-Since")))
    } catch {
      case NonFatal(e) =>
        throw new IllegalArgumentException(s"Could not parse If-Unmodified-Since in format $sample.")
    }
    val userOid = session.as[String]("personOid")
    val valintatapajonoOid = params("valintatapajonoOid")

    parsedBody.extract[List[ValinnanTulosPatch]].foreach(muutos => {
      val (edellinenTila, lastModified) = ilmoittautumisService.getIlmoittautumistila(muutos.hakemusOid, valintatapajonoOid)
      if (lastModified.isAfter(ifUnmodifiedSince)) {
        logger.info(s"Stale read of hakemus ${muutos.hakemusOid} in valintatapajono $valintatapajonoOid, last modified $lastModified, read $ifUnmodifiedSince")
      }
      logger.info(s"User $userOid changed ilmoittautumistila of hakemus ${muutos.hakemusOid} in valintatapajono $valintatapajonoOid from $edellinenTila to ${muutos.ilmoittautumistila}")
    })
    NoContent()
  }
}
