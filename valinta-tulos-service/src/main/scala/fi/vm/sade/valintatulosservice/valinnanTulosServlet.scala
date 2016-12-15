package fi.vm.sade.valintatulosservice
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.util.UUID

import fi.vm.sade.security.AuthenticationFailedException
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.security.Session
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import org.json4s.DefaultFormats
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger.{Swagger, SwaggerSupport}

import scala.util.{Failure, Try}

case class ValinnanTulos(hakemusOid: String, ilmoittautumistila: String)

case class ValinnanTulosPatch(hakemusOid: String, ilmoittautumistila: String)

class ValinnanTulosServlet(ilmoittautumisService: IlmoittautumisService, sessionRepository: SessionRepository)
                          (implicit val swagger: Swagger)
  extends ScalatraServlet with JacksonJsonSupport with SwaggerSupport with Logging {

  override val applicationName = Some("auth/valinnan-tulos")
  override val applicationDescription = "Valinnan tuloksen REST API"

  override protected implicit def jsonFormats = DefaultFormats

  error {
    case e: AuthenticationFailedException =>
      logger.warn("authentication failed", e)
      Forbidden("error" -> "Forbidden")
    case e: IllegalArgumentException =>
      logger.warn("bad request", e)
      BadRequest("error" -> s"Bad request. ${e.getMessage}")
    case e: Throwable =>
      logger.error("internal server error", e)
      InternalServerError("error" -> "Internal server error")
  }

  private def getSession: Session = {
    cookies.get("session").map(UUID.fromString).flatMap(sessionRepository.get)
      .getOrElse(throw new AuthenticationFailedException)
  }

  private def parseIfUnmodifiedSince: Instant = {
    request.headers.get("If-Unmodified-Since") match {
      case Some(s) =>
        Try(Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(s))).recoverWith {
          case e => Failure(new IllegalArgumentException(s"Ei voitu jäsentää otsaketta If-Unmodified-Since muodossa $sample.", e))
        }.get
      case None => throw new IllegalArgumentException("Otsake If-Unmodified-Since on pakollinen.")
    }
  }

  private def parseValintatapajonoOid: String = {
    params.getOrElse("valintatapajonoOid", throw new IllegalArgumentException("URL parametri Valintatapajono OID on pakollinen."))
  }

  private def renderHttpDate(instant: Instant): String = {
    DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(instant, ZoneId.of("GMT")))
  }

  val valinnanTulosSwagger: OperationBuilder = (apiOperation[List[ValinnanTulos]]("valinnanTulos")
    summary "Valinnan tulos"
    parameter pathParam[String]("valintatapajonoOid").description("Valintatapajonon OID")
    )
  get("/:valintatapajonoOid", operation(valinnanTulosSwagger)) {
    contentType = formats("json")
    val session = getSession
    val valintatapajonoOid = parseValintatapajonoOid
    val ilmoittautumiset = ilmoittautumisService.getIlmoittautumistilat(valintatapajonoOid).right.get
    Ok(
      body = ilmoittautumiset.map(i => ValinnanTulos(i._1, i._2.toString)),
      headers = Map("Last-Modified" -> renderHttpDate(ilmoittautumiset.map(_._3).max))
    )
  }

  val sample = renderHttpDate(Instant.EPOCH)
  val valinnanTuloksenMuutosSwagger: OperationBuilder = (apiOperation[Unit]("muokkaaValinnanTulosta")
    summary "Muokkaa valinnan tulosta"
    parameter pathParam[String]("valintatapajonoOid").description("Valintatapajonon OID")
    parameter headerParam[String]("If-Unmodified-Since").description(s"Aikaleima RFC 1123 määrittelemässä muodossa $sample").required
    parameter bodyParam[List[ValinnanTulosPatch]].description("Muutokset valinnan tulokseen").required
    )
  patch("/:valintatapajonoOid", operation(valinnanTuloksenMuutosSwagger)) {
    contentType = null
    val session = getSession
    val valintatapajonoOid = parseValintatapajonoOid
    val ifUnmodifiedSince = parseIfUnmodifiedSince
    parsedBody.extract[List[ValinnanTulosPatch]].foreach(muutos => {
      val (edellinenTila, lastModified) = ilmoittautumisService.getIlmoittautumistila(muutos.hakemusOid, valintatapajonoOid).right.get
      if (lastModified.isAfter(ifUnmodifiedSince)) {
        logger.warn(s"Hakemus ${muutos.hakemusOid} valintatapajonossa $valintatapajonoOid " +
          s"on muuttunut $lastModified lukemisajan $ifUnmodifiedSince jälkeen.")
      }
      logger.info(s"Käyttäjä ${session.personOid} muokkasi " +
        s"hakemuksen ${muutos.hakemusOid} ilmoittautumistilaa valintatapajonossa $valintatapajonoOid " +
        s"tilasta $edellinenTila tilaan ${muutos.ilmoittautumistila}")
    })
    NoContent()
  }
}
