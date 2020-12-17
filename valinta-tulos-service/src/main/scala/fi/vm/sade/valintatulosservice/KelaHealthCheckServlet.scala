package fi.vm.sade.valintatulosservice

import java.util.concurrent.atomic.AtomicReference
import javax.servlet.http.HttpServletResponse

import fi.vm.sade.auditlog.Audit
import fi.vm.sade.utils.cas.CasClient.SessionCookie
import fi.vm.sade.utils.http.DefaultHttpRequest
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.kela.VtsKelaSessionCookie
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import org.scalatra.swagger.Swagger

import scalaj.http.{Http, HttpOptions}

class KelaHealthCheckServlet(
  val audit: Audit,
  val sessionRepository: SessionRepository,
  val appConfig: VtsAppConfig,
  val vtsKelaSessionCookie: VtsKelaSessionCookie
)(override implicit val swagger: Swagger)
    extends VtsServletBase
    with Logging {

  protected val applicationDescription =
    "Valvonnan Kelan paikanvastaanottorajapinnan health check REST API"

  get("/") {
    val hetu: String = appConfig.settings.securitySettings.kelaVastaanototTestihetu

    var vtsSessionCookie: SessionCookie = authenticate()
    var kelaHealthCheckResponse: KelaHealthCheckResponse = doRequest(hetu, vtsSessionCookie)

    if (kelaHealthCheckResponse.statusCode == HttpServletResponse.SC_UNAUTHORIZED) {
      KelaHealthCheckSessionCookieHolder.clear()
      vtsSessionCookie = authenticate()
      kelaHealthCheckResponse = doRequest(hetu, vtsSessionCookie)
    }

    response.setStatus(HttpServletResponse.SC_OK)
    response.setContentType("text/plain")

    kelaHealthCheckResponse.statusCode match {
      case HttpServletResponse.SC_NO_CONTENT => "OK"
      case HttpServletResponse.SC_OK =>
        if (kelaHealthCheckResponse.result.isEmpty) "ERROR"
        val json = parse(kelaHealthCheckResponse.result)
        val henkilo: Option[fi.vm.sade.valintatulosservice.kela.Henkilo] =
          Option(json.extract[fi.vm.sade.valintatulosservice.kela.Henkilo])
        henkilo match {
          case Some(henkilo) =>
            if (henkilo.henkilotunnus == hetu) "OK"
            else "ERROR"
          case _ => "ERROR"
        }
      case _ => "ERROR"
    }
  }

  private def doRequest(hetu: String, vtsSessionCookie: SessionCookie): KelaHealthCheckResponse = {
    val (statusCode, responseHeaders, result) =
      new DefaultHttpRequest(
        Http(
          appConfig.settings.securitySettings.casServiceIdentifier + "/cas/kela/vastaanotot/henkilo"
        )
          .method("POST")
          .options(Seq(HttpOptions.connTimeout(10000), HttpOptions.readTimeout(120000)))
          .header("Content-Type", "text/plain")
          .header("Cookie", s"session=$vtsSessionCookie")
          .postData(hetu)
      ).responseWithHeaders()
    KelaHealthCheckResponse(statusCode, responseHeaders, result)
  }

  private def authenticate(): SessionCookie = {
    KelaHealthCheckSessionCookieHolder.synchronized {
      if (KelaHealthCheckSessionCookieHolder.hasSessionCookie) {
        logger.info("Returned existing session cookie")
        KelaHealthCheckSessionCookieHolder.sessionCookie.get()
      } else {
        var vtsSessionCookie = KelaHealthCheckSessionCookieHolder.NOT_FETCHED
        vtsSessionCookie = cookies.get("session") match {
          case Some(cookie) => cookie
          case _            => vtsKelaSessionCookie.retrieveSessionCookie()
        }
        KelaHealthCheckSessionCookieHolder.sessionCookie.set(vtsSessionCookie)
        logger.info("Set new session cookie")
        vtsSessionCookie
      }
    }
  }

  object KelaHealthCheckSessionCookieHolder {
    val NOT_FETCHED = "<not fetched>"
    val sessionCookie = new AtomicReference[String](NOT_FETCHED)

    def hasSessionCookie: Boolean = sessionCookie.get() != NOT_FETCHED

    def clear(): Unit =
      KelaHealthCheckSessionCookieHolder.synchronized {
        logger.info("Cleared stored session cookie")
        sessionCookie.set(NOT_FETCHED)
      }
  }

  case class KelaHealthCheckResponse(
    statusCode: Int,
    responseHeaders: Map[String, String],
    result: String
  )
}
