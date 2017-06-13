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

class KelaHealthCheckServlet(val audit: Audit, val sessionRepository: SessionRepository, val appConfig: VtsAppConfig, val vtsKelaSessionCookie: VtsKelaSessionCookie)
                            (override implicit val swagger: Swagger) extends VtsServletBase with Logging {
  override val applicationName = Some("health-check/kela")
  protected val applicationDescription = "Valvonnan Kelan paikanvastaanottorajapinnan health check REST API"

  get("/") {
    val hetu = appConfig.settings.securitySettings.kelaVastaanototTestihetu
    processRequest(hetu)
  }

  def processRequest(hetu: String): String = {
    val vtsSessionCookie: SessionCookie = authenticate()

    val (statusCode, responseHeaders, result) =
      new DefaultHttpRequest(Http(appConfig.settings.securitySettings.casServiceIdentifier + "/cas/kela/vastaanotot/henkilo")
        .method("POST")
        .options(Seq(HttpOptions.connTimeout(10000), HttpOptions.readTimeout(120000)))
        .header("Content-Type", "text/plain")
        .header("Cookie", s"session=$vtsSessionCookie")
        .postData(hetu)
      ).responseWithHeaders()

    if (statusCode == HttpServletResponse.SC_MOVED_TEMPORARILY) {
      KelaHealthCheckSessionCookieHolder.clear()
      processRequest(hetu)
    }

    response.setStatus(HttpServletResponse.SC_OK)
    response.setContentType("text/plain")

    statusCode match {
      case HttpServletResponse.SC_NO_CONTENT => "OK"
      case HttpServletResponse.SC_OK =>
        if (result.isEmpty) "ERROR"
        val json = parse(result)
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

  private def authenticate(): SessionCookie = {
    KelaHealthCheckSessionCookieHolder.synchronized {
      if (KelaHealthCheckSessionCookieHolder.hasSessionCookie) {
        KelaHealthCheckSessionCookieHolder.sessionCookie.get()
      } else {
        var vtsSessionCookie = KelaHealthCheckSessionCookieHolder.NOT_FETCHED
        vtsSessionCookie = cookies.get("session") match {
          case Some(cookie) => cookie
          case _ => vtsKelaSessionCookie.retrieveSessionCookie()
        }
        KelaHealthCheckSessionCookieHolder.sessionCookie.set(vtsSessionCookie)
        vtsSessionCookie
      }
    }
  }

  object KelaHealthCheckSessionCookieHolder {
    val NOT_FETCHED = "<not fetched>"
    val sessionCookie = new AtomicReference[String](NOT_FETCHED)

    def hasSessionCookie: Boolean = sessionCookie.get() != NOT_FETCHED

    def clear(): Unit = KelaHealthCheckSessionCookieHolder.synchronized {
      sessionCookie.set(NOT_FETCHED)
    }
  }
}
