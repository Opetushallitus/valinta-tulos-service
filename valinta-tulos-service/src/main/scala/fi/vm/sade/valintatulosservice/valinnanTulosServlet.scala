package fi.vm.sade.valintatulosservice
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId, ZonedDateTime}

import fi.vm.sade.valintatulosservice.domain.Ilmoittautumistila
import org.json4s.DefaultFormats
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger.{Swagger, SwaggerSupport}

import scala.util.control.NonFatal

case class ValinnanTulos(hakemusOid: String, ilmoittautumistila: String)

case class IlmoittautuminenPatch(hakemusOid: String, ilmoittautumistila: String)

class ValinnanTulosServlet(ilmoittautumisService: IlmoittautumisService)
                          (implicit val swagger: Swagger)
  extends ScalatraServlet with JacksonJsonSupport with SwaggerSupport {

  override val applicationName = Some("auth/valinnan-tulos")
  override val applicationDescription = "Valinnan tuloksen REST API"
  override val swaggerConsumes = List("application/ilmoittautuminen+json")

  override protected implicit def jsonFormats = DefaultFormats

  error {
    case e: IllegalArgumentException =>
      BadRequest("error" -> s"Bad request. ${e.getMessage}")
    case e: Throwable =>
      InternalServerError("error" -> "internal server error")
  }

  before() {
    if (!session.as[Boolean]("authenticated")) {
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
  val valinnanTuloksenMuutosSwagger: OperationBuilder = (apiOperation[Unit]("valinnanTuloksenMuutos")
    summary "Valinnan tuloksen muutos"
    parameter pathParam[String]("valintatapajonoOid").description("Valintatapajonon OID")
    parameter headerParam[String]("If-Unmodified-Since").description(s"Timestamp in RFC 1123 format: $sample").required
    parameter bodyParam[List[IlmoittautuminenPatch]].description("").required
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

    request.contentType match {
      case Some(s) if s.startsWith("application/ilmoittautuminen+json") =>
        parsedBody.extract[List[IlmoittautuminenPatch]].foreach(muutos => {
          ilmoittautumisService.setIlmoittautumistila(
            muutos.hakemusOid,
            valintatapajonoOid,
            Ilmoittautumistila.withName(muutos.ilmoittautumistila),
            userOid,
            ifUnmodifiedSince
          )
        })
        NoContent()
      case Some(s) =>
        UnsupportedMediaType("error" -> s"Unsupported media type ${request.contentType.get}.")
      case None =>
        BadRequest("error" -> "Content-Type required.")
    }
  }
}
