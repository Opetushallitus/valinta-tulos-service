package fi.vm.sade.valintatulosservice.migraatio.vastaanotot

import fi.vm.sade.javautils.nio.cas.{CasClient, CasClientBuilder}
import fi.vm.sade.security.ScalaCasConfig
import fi.vm.sade.valintatulosservice.config.StubbedExternalDeps
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.logging.Logging
import org.asynchttpclient.RequestBuilder
import org.json4s.ParserUtil.ParseException
import org.json4s.native.JsonMethods.parse

import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters.toScala
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, DurationInt}


case class Henkilo(oidHenkilo: String, hetu: String, etunimet: String, sukunimi: String)

trait HakijaResolver {
  def findPersonByHetu(hetu: String, timeout: Duration = 60.seconds): Option[Henkilo]
}

object HakijaResolver {
  def apply(appConfig: VtsAppConfig): HakijaResolver = appConfig match {
    case _:StubbedExternalDeps => new HakijaResolver {
      override def findPersonByHetu(hetu: String, timeout: Duration): Option[Henkilo] = hetu match {
        case "face-beef" =>
          Some(Henkilo("1.2.3.4", "face-beef", "Test", "Henkilo"))
        case "090121-321C" =>
          Some(Henkilo("1.2.246.562.24.26463409086", "090121-321C", "Vili", "Rossi"))
        case _ =>
          None
      }
    }
    case _ => new MissingHakijaOidResolver(appConfig)
  }
}

class MissingHakijaOidResolver(appConfig: VtsAppConfig) extends JsonFormats with Logging with HakijaResolver {
  private val retryCodes = Set(new Integer(401),new Integer(302)).asJava
  private val oppijanumerorekisteriClient = createCasClient(appConfig, appConfig.ophUrlProperties.url("url-oppijanumerorekisteri"))

  case class HakemusHenkilo(personOid: Option[String], hetu: Option[String], etunimet: String, sukunimi: String, kutsumanimet: String,
                            syntymaaika: String, aidinkieli: Option[String], sukupuoli: Option[String])

  override def findPersonByHetu(hetu: String, timeout: Duration = 60.seconds): Option[Henkilo] = {
    val requestUri = appConfig.ophUrlProperties.url("oppijanumerorekisteri-service.henkiloPerusByHetu", hetu)
    val req = new RequestBuilder()
      .setUrl(requestUri)
      .addHeader("Accept", "application/json")
      .build()
    val result = toScala(oppijanumerorekisteriClient.executeAndRetryWithCleanSessionOnStatusCodes(req, retryCodes)).map {
      case r if 200 == r.getStatusCode =>
        parse(r.getResponseBodyAsStream).extract[Option[Henkilo]]
      case r if 404 == r.getStatusCode => {
        logger.warn(s"Could not find henkilo by hetu from ONR. URL=${requestUri}, response=${r}")
        None
      }
      case r =>
        throw new RuntimeException(r.toString)
    }
    try {
      Await.result(result, Duration(1, TimeUnit.MINUTES))
    } catch {
      case e: Throwable => {
        handleFailure(e, "searching person oid by hetu " + Option(hetu).map(_.replaceAll(".","*")))
        throw e
      }
    }
  }

  private def handleFailure(t:Throwable, message:String) = t match {
    case pf: ParseException =>
      logger.error(s"Got parse exception when $message : $pf", t); None
    case e: Exception =>
      logger.error(s"Got exception when $message : ${e.getMessage}", e); None
  }

  private def createCasClient(appConfig: VtsAppConfig, targetService: String): CasClient =
    CasClientBuilder.build(ScalaCasConfig(
      appConfig.settings.securitySettings.casUsername,
      appConfig.settings.securitySettings.casPassword,
      appConfig.settings.securitySettings.casUrl,
      targetService,
      appConfig.settings.callerId,
      appConfig.settings.callerId,
      "/j_spring_cas_security_check",
      "JSESSIONID"
    ))

}
