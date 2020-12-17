package fi.vm.sade.valintatulosservice

import java.text.ParseException
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.util.ConcurrentModificationException

import fi.vm.sade.security.{AuthenticationFailedException, AuthorizationFailedException}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.json.JsonFormats.writeJavaObjectToOutputStream
import fi.vm.sade.valintatulosservice.json.{JsonFormats, StreamingFailureException}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{
  HakemusOid,
  HakukohdeOid,
  ValintatapajonoOid
}
import org.json4s.MappingException
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger.SwaggerSupport

import scala.util.{Failure, Success, Try}

trait VtsServletBase
    extends ScalatraServlet
    with Logging
    with JacksonJsonSupport
    with JsonFormats
    with SwaggerSupport {
  private val maxBodyLengthToLog = 500000

  before() {
    contentType = formats("json")
    checkJsonContentType()
  }

  after() {
    if (!response.headers.contains("Cache-Control")) {
      response.headers += ("Cache-Control" -> "no-store")
    }
  }

  notFound {
    // remove content type in case it was set through an action
    contentType = null
    serveStaticResource() getOrElse resourceNotFound()
  }

  error {
    case t: Throwable => {
      t match {
        case e: AuthenticationFailedException =>
          logger.warn("authentication failed", e)
          Unauthorized("error" -> "Unauthorized")
        case e: AuthorizationFailedException =>
          logger.warn("authorization failed", e)
          Forbidden("error" -> "Forbidden")
        case e: IllegalStateException =>
          badRequest(e)
        case e: IllegalArgumentException =>
          badRequest(e)
        case e: MappingException =>
          badRequest(e)
        case e: ParseException =>
          badRequest(e)
        case e: NoSuchElementException =>
          badRequest(e)
        case e: StreamingFailureException =>
          logger.error(errorDescription, e)
          InternalServerError(e.contentToInsertToBody)
        case e: ConcurrentModificationException =>
          logger.error(errorDescription, e)
          Conflict(
            "error" -> ("Tietoihin on tehty samanaikaisia muutoksia, päivitä sivu ja yritä uudelleen (" + e.getMessage + ")")
          )
        case e =>
          logger.error(errorDescription, e)
          InternalServerError("error" -> "500 Internal Server Error")
      }
    }
  }

  private def errorDescription: String = {
    val bodyLength = request.body.length
    def bodyToLog(): String = {
      if (bodyLength > maxBodyLengthToLog) {
        request.body.substring(
          0,
          maxBodyLengthToLog
        ) + s"[TRUNCATED from $bodyLength to $maxBodyLengthToLog characters]"
      } else {
        request.body
      }
    }

    "%s %s%s".format(
      request.getMethod,
      requestPath,
      if (bodyLength > 0) {
        s" (body: ${bodyToLog()})"
      } else {
        ""
      }
    )
  }

  private def badRequest(e: Throwable) = {
    logger.warn(errorDescription + ": " + e.toString, e)
    BadRequest("error" -> e.getMessage)
  }

  protected def checkJsonContentType() {
    if (
      request.requestMethod == Post && request.body.nonEmpty && request.contentType.forall(
        !_.contains("application/json")
      )
    ) {
      halt(415, "error" -> "Only application/json accepted")
    }
  }

  def streamOk(x: Object): Unit = {
    response.setStatus(200)
    response.setContentType("application/json;charset=UTF-8")
    writeJavaObjectToOutputStream(x, response.getOutputStream)
  }

  protected def parseHakemusOid: Either[Throwable, HakemusOid] = {
    params
      .get("hakemusOid")
      .fold[Either[Throwable, HakemusOid]](
        Left(new NoSuchElementException("URL parametri hakemusOid on pakollinen."))
      )(s => Right(HakemusOid(s)))
  }

  protected def parseHakukohdeOid: Either[Throwable, HakukohdeOid] = {
    params
      .get("hakukohdeOid")
      .fold[Either[Throwable, HakukohdeOid]](
        Left(new NoSuchElementException("URL parametri hakukohdeOid on pakollinen."))
      )(s => Right(HakukohdeOid(s)))
  }

  protected def parseValintatapajonoOid: Either[Throwable, ValintatapajonoOid] = {
    params
      .get("valintatapajonoOid")
      .fold[Either[Throwable, ValintatapajonoOid]](
        Left(new NoSuchElementException("URL parametri valintatapajonoOid on pakollinen."))
      )(s => Right(ValintatapajonoOid(s)))
  }

  protected def createLastModifiedHeader(instant: Instant): String = {
    //- system_time range in database is of form ["2017-02-28 13:40:02.442277+02",)
    //- RFC-1123 date-time format used in headers has no millis
    //- if Last-Modified/If-Unmodified-Since header is set to 2017-02-28 13:40:02, it will never be inside system_time range
    //-> this is why we wan't to set it to 2017-02-28 13:40:03 instead
    renderHttpDate(instant.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusSeconds(1))
  }

  protected def renderHttpDate(instant: Instant): String = {
    DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(instant, ZoneId.of("GMT")))
  }

  protected val RFC1123sample: String = renderHttpDate(Instant.EPOCH)
  protected def parseIfUnmodifiedSince: Either[Throwable, Instant] =
    request.headers.get("If-Unmodified-Since") match {
      case Some(s) =>
        Try(Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(s))) match {
          case Success(i) => Right(i)
          case Failure(e) =>
            Left(
              new IllegalArgumentException(
                s"Ei voitu jäsentää otsaketta If-Unmodified-Since muodossa $RFC1123sample.",
                e
              )
            )
        }
      case None => Left(new NoSuchElementException("Otsake If-Unmodified-Since on pakollinen."))
    }

  protected def parseIfNoneMatch: Either[Throwable, String] = {
    request.headers
      .get("If-None-Match")
      .fold[Either[Throwable, String]](
        Left(new NoSuchElementException("Otsake If-None-Match on pakollinen."))
      )(Right(_))
  }
}
