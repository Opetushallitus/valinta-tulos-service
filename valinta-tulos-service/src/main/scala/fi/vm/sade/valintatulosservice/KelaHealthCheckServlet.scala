package fi.vm.sade.valintatulosservice

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

    val vtsSessionCookie: SessionCookie = cookies.get("session") match {
      case Some(cookie) => cookie
      case _ => vtsKelaSessionCookie.retrieveSessionCookie()
    }

    val (statusCode, responseHeaders, result) =
      new DefaultHttpRequest(Http(appConfig.settings.securitySettings.casServiceIdentifier + "/cas/kela/vastaanotot/henkilo")
        .method("POST")
        .options(Seq(HttpOptions.connTimeout(10000), HttpOptions.readTimeout(120000)))
        .header("Content-Type", "text/plain")
        .header("Cookie", s"session=$vtsSessionCookie")
        .postData(hetu)
      ).responseWithHeaders()

    response.setStatus(200)
    response.setContentType("text/plain")

    !result.isEmpty match {
      case true => {
        val json = parse(result)
        val henkilo : Option[fi.vm.sade.valintatulosservice.kela.Henkilo] = Option(json.extract[fi.vm.sade.valintatulosservice.kela.Henkilo])
        henkilo match {
          case Some(henkilo) => if(henkilo.henkilotunnus == hetu) "OK" else "ERROR"
          case _ => "ERROR"
        }
      }
      case false => "ERROR"
    }
  }
}
