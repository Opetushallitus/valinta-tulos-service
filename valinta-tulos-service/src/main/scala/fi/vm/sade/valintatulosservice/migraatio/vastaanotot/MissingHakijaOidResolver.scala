package fi.vm.sade.valintatulosservice.migraatio.vastaanotot

import java.net.URLEncoder
import java.util.concurrent.TimeUnit

import fi.vm.sade.javautils.nio.cas.{CasClient, CasClientBuilder}
import fi.vm.sade.security.ScalaCasConfig
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.StubbedExternalDeps
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.json.JsonFormats
import org.asynchttpclient.{RequestBuilder, Response}
import org.json4s.native.JsonMethods.parse
import org.json4s.native.Serialization.write
import org.json4s.ParserUtil.ParseException
import org.json4s.JsonAST.JValue
import org.json4s._

import scala.compat.java8.FutureConverters.toScala
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scalaz.concurrent.Task
import scalaz.{-\/, \/-}
import scala.concurrent.duration._

case class Henkilo(oidHenkilo: String, hetu: String, etunimet: String, sukunimi: String)

trait HakijaResolver {
  def findPersonByHetu(hetu: String, timeout: Duration = 60 seconds): Option[Henkilo]
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
  private val hakuClient = createCasClient(appConfig, appConfig.ophUrlProperties.url("haku-app.baseUrl"))
  private val oppijanumerorekisteriClient = createCasClient(appConfig, appConfig.ophUrlProperties.url("url-oppijanumerorekisteri"))

  case class HakemusHenkilo(personOid: Option[String], hetu: Option[String], etunimet: String, sukunimi: String, kutsumanimet: String,
                            syntymaaika: String, aidinkieli: Option[String], sukupuoli: Option[String])

  private def findPersonOidByHetu(hetu: String): Option[String] = findPersonByHetu(hetu).map(_.oidHenkilo)

  override def findPersonByHetu(hetu: String, timeout: Duration = 60 seconds): Option[Henkilo] = {
    val requestUri = appConfig.ophUrlProperties.url("oppijanumerorekisteri-service.henkiloPerusByHetu", hetu)
    val req = new RequestBuilder()
      .setUrl(requestUri)
      .addHeader("Accept", "application/json")
      .build()
    val result = toScala(oppijanumerorekisteriClient.execute(req)).map {
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

  def extractHakemusHenkiloFromResponse(r: JValue): HakemusHenkilo = {
    val henkilotiedot = r \ "answers" \ "henkilotiedot"
    HakemusHenkilo( (r \ "personOid").extractOpt[String],
      (henkilotiedot \ "Henkilotunnus").extractOpt[String],
      (henkilotiedot \ "Etunimet").extract[String],
      (henkilotiedot \ "Sukunimi").extract[String],
      (henkilotiedot \ "Kutsumanimi").extract[String],
      (henkilotiedot \ "syntymaaika").extract[String],
      (henkilotiedot \ "aidinkieli").extractOpt[String],
      (henkilotiedot \ "sukupuoli").extractOpt[String])
  }

  def findPersonOidByHakemusOid(hakemusOid: String): Option[String] = {

    val requestUri = appConfig.ophUrlProperties.url("haku-app.application.queryBase", hakemusOid)
    val req = new RequestBuilder()
      .setUrl(requestUri)
      .addHeader("Accept", "application/json")
      .build()
    //val backoff = RetryPolicy.exponentialBackoff(Duration(30, TimeUnit.SECONDS), maxRetry = 10)
    //val retryingClient = Retry(RetryPolicy(backoff))(hakuClient)
    val result = toScala(hakuClient.execute(req)).map {
      case r if 200 == r.getStatusCode =>
        extractHakemusHenkiloFromResponse(parse(r.getResponseBodyAsStream)) match {
          case henkilo if henkilo.personOid.isDefined => henkilo.personOid
          case henkilo => henkilo.hetu.map(findPersonOidByHetu).getOrElse({throw new RuntimeException(s"Hakemuksella $hakemusOid ei hakijaoidia!")})
        }
      case r =>
        throw new RuntimeException(s"Got non-OK response from haku-app when fetching hakemus $hakemusOid: ${r.toString}")
    }
    try {
      Await.result(result, Duration(3, TimeUnit.MINUTES))
    } catch {
      case e: Throwable => {
        handleFailure(e, s"finding henkilö from hakemus $hakemusOid")
        throw new RuntimeException(s"Hakemuksen $hakemusOid henkilötietoja ei saatu jäsennetyksi.")
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
