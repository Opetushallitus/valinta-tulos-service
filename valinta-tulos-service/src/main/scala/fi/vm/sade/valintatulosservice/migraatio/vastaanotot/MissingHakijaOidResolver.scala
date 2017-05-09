package fi.vm.sade.valintatulosservice.migraatio.vastaanotot

import java.net.URLEncoder
import java.util.concurrent.TimeUnit

import fi.vm.sade.utils.cas.{CasAuthenticatingClient, CasParams}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.StubbedExternalDeps
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.json.JsonFormats
import org.http4s._
import org.http4s.client.middleware.{Retry, RetryPolicy}
import org.http4s.headers.`Content-Type`
import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.JsonMethods._

import scala.concurrent.duration._
import scalaz.concurrent.Task
import scalaz.{-\/, \/-}

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
        case _ =>
          None
      }
    }
    case _ => new MissingHakijaOidResolver(appConfig)
  }
}

class MissingHakijaOidResolver(appConfig: VtsAppConfig) extends JsonFormats with Logging with HakijaResolver {
  private val hakuClient = createCasClient(appConfig, "/haku-app")
  private val henkiloClient = createCasClient(appConfig, "/authentication-service")
  private val oppijanumerorekisteriClient = createCasClient(appConfig, "/oppijanumerorekisteri-service")
  private val henkiloPalveluUrlBase = appConfig.ophUrlProperties.url("authentication-service.henkilo")

  case class HakemusHenkilo(personOid: Option[String], hetu: Option[String], etunimet: String, sukunimi: String, kutsumanimet: String,
                            syntymaaika: String, aidinkieli: Option[String], sukupuoli: Option[String])

  private def findPersonOidByHetu(hetu: String): Option[String] = findPersonByHetu(hetu).map(_.oidHenkilo)

  override def findPersonByHetu(hetu: String, timeout: Duration = 60 seconds): Option[Henkilo] = {
    implicit val henkiloReader = new Reader[Option[Henkilo]] {
      override def read(v: JValue): Option[Henkilo] = {
        Some(Henkilo( (v \ "oidHenkilo").extract[String],
          (v \ "hetu").extract[String],
          (v \ "etunimet").extract[String],
          (v \ "sukunimi").extract[String]))
      }
    }

    implicit val henkiloDecoder = org.http4s.json4s.native.jsonOf[Option[Henkilo]]

    val requestUri = createUri(appConfig.ophUrlProperties.url("oppijanumerorekisteri-service.henkiloPerusByHetu",hetu))
    oppijanumerorekisteriClient.httpClient.fetch(Request(uri = requestUri)) {
      case r if 200 == r.status.code => r.as[Option[Henkilo]]
      case r => Task.fail(new RuntimeException(r.toString))
    }.attemptRunFor(timeout) match {
      case \/-(found@Some(henkilo)) => found
      case \/-(None) =>
        logger.warn(s"Could not find henkilo by hetu from ONR")
        None
      case -\/(t) =>
        handleFailure(t, "searching person oid by hetu " + Option(hetu).map(_.replaceAll(".","*")))
        throw t
    }
  }

  def findPersonOidByHakemusOid(hakemusOid: String): Option[String] = {
    implicit val hakemusHenkiloReader = new Reader[HakemusHenkilo] {
      override def read(v: JValue): HakemusHenkilo = {
        val henkilotiedot = v \ "answers" \ "henkilotiedot"
        HakemusHenkilo( (v \ "personOid").extractOpt[String], (henkilotiedot \ "Henkilotunnus").extractOpt[String],
          (henkilotiedot \ "Etunimet").extract[String], (henkilotiedot \ "Sukunimi").extract[String],
          (henkilotiedot \ "Kutsumanimi").extract[String], (henkilotiedot \ "syntymaaika").extract[String],
          (henkilotiedot \ "aidinkieli").extractOpt[String], (henkilotiedot \ "sukupuoli").extractOpt[String])
      }
    }

    implicit val hakemusHenkiloDecoder = org.http4s.json4s.native.jsonOf[HakemusHenkilo]

    val stringUri = appConfig.ophUrlProperties.url("haku-app.application.queryBase", hakemusOid)
    val url = Uri.fromString(stringUri).getOrElse(throw new RuntimeException(s"Invalid uri: $stringUri"))
    val retryingClient = Retry(RetryPolicy.exponentialBackoff(Duration(30, TimeUnit.SECONDS), maxRetry = 10))(hakuClient.httpClient)
    val henkiloFromHakemus = retryingClient.fetch(Request(method = Method.GET, uri = url)) {
      case r if 200 == r.status.code => r.as[HakemusHenkilo]
      case r => Task.fail(new RuntimeException(s"Got non-OK response from haku-app when fetching hakemus $hakemusOid: ${r.toString}"))
    }.attemptRunFor(Duration(3, TimeUnit.MINUTES)) match {
      case \/-(henkilo: HakemusHenkilo) => Some(henkilo)
      case -\/(t) => handleFailure(t, s"finding henkilö from hakemus $hakemusOid")
    }

    henkiloFromHakemus match {
      case Some(henkilo) if henkilo.personOid.isDefined => henkilo.personOid
      case Some(henkilo) => henkilo.hetu.map(findPersonOidByHetu).getOrElse({throw new RuntimeException(s"Hakemuksella $hakemusOid ei hakijaoidia!")})
      case None => throw new RuntimeException(s"Hakemuksen $hakemusOid henkilotietoja ei saatu parsittua.")
    }
  }

  private def createPerson(henkilo: HakemusHenkilo): Option[String] = {
    val syntymaaika = new java.text.SimpleDateFormat("dd.MM.yyyy").parse(henkilo.syntymaaika)

    val json: String = compact(render(
      ("etunimet" -> henkilo.etunimet) ~
      ("sukunimi" -> henkilo.sukunimi) ~
      ("kutsumanimi" -> henkilo.kutsumanimet) ~
      ("hetu" -> henkilo.hetu) ~
      ("henkiloTyyppi" -> "OPPIJA") ~
      ("syntymaaika" -> new java.text.SimpleDateFormat("yyyy-MM-dd").format(syntymaaika)) ~
      ("sukupuoli" -> henkilo.sukupuoli) ~
      ("asiointiKieli" -> ("kieliKoodi" -> "fi")) ~
      ("aidinkieli" -> ("kieliKoodi" -> henkilo.aidinkieli.getOrElse("fi").toLowerCase))))

    implicit val jsonStringEncoder: EntityEncoder[String] = EntityEncoder
      .stringEncoder(Charset.`UTF-8`).withContentType(`Content-Type`(MediaType.`application/json`, Charset.`UTF-8`))

    val task: Task[Request] = Request(method = Method.POST, uri = createUri(henkiloPalveluUrlBase + "/", ""))
      .withBody(json)(jsonStringEncoder)

    henkiloClient.httpClient.fetch(task) {
      case r if 200 == r.status.code => r.as[String]
      case r => Task.fail(new RuntimeException(r.toString))
    }.attemptRunFor(Duration(30, TimeUnit.SECONDS)) match {
      case \/-(response) =>
        logger.info(s"Luotiin henkilo oid=$response")
        Some(response)
      case -\/(t) => handleFailure(t, "creating person")
    }
  }

  private def handleFailure(t:Throwable, message:String) = t match {
    case pf: ParseFailure => logger.error(s"Got parse exception when $message : $pf, ${pf.sanitized}", t); None
    case e:Exception => logger.error(s"Got exception when $message : ${e.getMessage}", e); None
  }
  private def createUri(uri: String): Uri = {
    Uri.fromString(uri).getOrElse(throw new RuntimeException(s"Invalid uri: $uri"))
  }
  private def createUri(base: String, rest: String): Uri = {
    val stringUri = base + URLEncoder.encode(rest, "UTF-8")
    Uri.fromString(stringUri).getOrElse(throw new RuntimeException(s"Invalid uri: $stringUri"))
  }

  private def createCasClient(appConfig: VtsAppConfig, targetService: String): CasAuthenticatingClient = {
    val params = CasParams(targetService, appConfig.settings.securitySettings.casUsername, appConfig.settings.securitySettings.casPassword)
    new CasAuthenticatingClient(appConfig.securityContext.casClient, params, org.http4s.client.blaze.defaultClient, "valinta-tulos-service") // TODO move clientSubSystemCode to constant
  }
}
