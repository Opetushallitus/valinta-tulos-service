package fi.vm.sade.valintatulosservice.migraatio.vastaanotot

import java.net.URLEncoder

import fi.vm.sade.utils.cas.{CasAuthenticatingClient, CasParams}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.{StubbedExternalDeps, AppConfig}
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.json.JsonFormats
import org.http4s._
import org.http4s.headers.`Content-Type`
import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.JsonMethods._
import scala.concurrent.duration._

import scala.util.{Failure, Success, Try}
import scalaz.concurrent.Task

case class Henkilo(oidHenkilo: String, hetu: String, etunimet: String, sukunimi: String, kutsumanimi: String,
                   syntymaaika: String)

trait HakijaResolver {
  def findPersonByHetu(hetu: String, timeout: Duration = 60 seconds): Option[Henkilo]
}

object HakijaResolver {
  def apply(appConfig: VtsAppConfig): HakijaResolver = appConfig match {
    case _:StubbedExternalDeps => new HakijaResolver {
      override def findPersonByHetu(hetu: String, timeout: Duration): Option[Henkilo] = hetu match {
        case "face-beef" =>
          Some(Henkilo("1.2.3.4", "face-beef", "Test", "Henkilo", "Test", ""))
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
  private val hakuUrlBase = appConfig.settings.config.getString("valinta-tulos-service.haku-app-url") + "/applications/listfull?q="
  private val henkiloPalveluUrlBase = appConfig.settings.config.getString("valinta-tulos-service.authentication-service-url") + "/resources/henkilo"

  case class HakemusHenkilo(personOid: Option[String], hetu: String, etunimet: String, sukunimi: String, kutsumanimet: String,
                            syntymaaika: String, aidinkieli: String, sukupuoli: String)



  private def findPersonOidByHetu(hetu: String): Option[String] = findPersonByHetu(hetu).map(_.oidHenkilo)

  override def findPersonByHetu(hetu: String, timeout: Duration = 60 seconds): Option[Henkilo] = {
    implicit val henkiloReader = new Reader[Henkilo] {
      override def read(v: JValue): Henkilo = {
        val henkilotiedot = (v \\ "results")
        Henkilo( (v \\ "oidHenkilo").extract[String], (henkilotiedot \ "hetu").extract[String],
          (henkilotiedot \ "etunimet").extract[String], (henkilotiedot \ "sukunimi").extract[String],
          (henkilotiedot \ "kutsumanimi").extract[String], (henkilotiedot \ "syntymaaika").extract[String])
      }
    }

    implicit val henkiloDecoder = org.http4s.json4s.native.jsonOf[Henkilo]

    Try(henkiloClient.prepare(createUri(henkiloPalveluUrlBase + "?q=", hetu)).timed(timeout).flatMap {
      case r if 200 == r.status.code => r.as[Henkilo]
      case r => Task.fail(new RuntimeException(r.toString))
    }.run) match {
      case Success(henkilo) => Some(henkilo)
      case Failure(t) => handleFailure(t, "searching person oid by hetu " + Option(hetu).map(_.replaceAll(".","*")))
    }
  }

  def findPersonOidByHakemusOid(hakemusOid: String): Option[String] = {
    implicit val hakemusHenkiloReader = new Reader[HakemusHenkilo] {
      override def read(v: JValue): HakemusHenkilo = {
        val result: JValue = (v \\ "answers")
        val henkilotiedot = (result \ "henkilotiedot")
        HakemusHenkilo( (v \\ "personOid").extractOpt[String], (henkilotiedot \ "Henkilotunnus").extract[String],
          (henkilotiedot \ "Etunimet").extract[String], (henkilotiedot \ "Sukunimi").extract[String],
          (henkilotiedot \ "Kutsumanimi").extract[String], (henkilotiedot \ "syntymaaika").extract[String],
          (henkilotiedot \ "aidinkieli").extract[String], (henkilotiedot \ "sukupuoli").extract[String])
      }
    }

    implicit val hakemusHenkiloDecoder = org.http4s.json4s.native.jsonOf[HakemusHenkilo]

    ( Try[HakemusHenkilo](hakuClient.prepare(Request(method = Method.GET, uri = createUri(hakuUrlBase, hakemusOid))).flatMap {
      case r if 200 == r.status.code => r.as[HakemusHenkilo]
      case r => Task.fail(new RuntimeException(r.toString))
    }.run) match {
      case Success(henkilo: HakemusHenkilo) => Some(henkilo)
      case Failure(t) => handleFailure(t, "finding henkilö from hakemus")
    } ) match {
      case None => None
      case Some(henkilo) => henkilo.personOid.orElse(findPersonOidByHetu(henkilo.hetu).orElse(createPerson(henkilo)))

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
      ("aidinkieli" -> ("kieliKoodi" -> henkilo.aidinkieli.toLowerCase))))

    implicit val jsonStringEncoder: EntityEncoder[String] = EntityEncoder
      .stringEncoder(Charset.`UTF-8`).withContentType(`Content-Type`(MediaType.`application/json`, Charset.`UTF-8`))

    Try(
      henkiloClient.prepare({
        val task = Request(method = Method.POST, uri = createUri(henkiloPalveluUrlBase + "/", ""))
          .withBody(json)(jsonStringEncoder)
        task
        }
      ).flatMap {
        case r if 200 == r.status.code => r.as[String]
        case r => Task.fail(new RuntimeException(r.toString))
      }.run
    ) match {
      case Success(response) => {
        logger.info(s"Luotiin henkilo oid=${response}")
        Some(response)
      }
      case Failure(t) => handleFailure(t, "creating person")
    }
  }

  private def handleFailure(t:Throwable, message:String) = t match {
    case pe:ParseException => logger.error(s"Got parse exception when ${message} ${pe.failure.details}, ${pe.failure.sanitized}", t); None
    case e:Exception => logger.error(s"Got exception when ${message} ${e.getMessage}", e); None
  }

  private def createUri(base: String, rest: String): Uri = {
    val stringUri = base + URLEncoder.encode(rest, "UTF-8")
    Uri.fromString(stringUri).getOrElse(throw new RuntimeException(s"Invalid uri: $stringUri"))
  }

  private def createCasClient(appConfig: VtsAppConfig, targetService: String): CasAuthenticatingClient = {
    val params = CasParams(targetService, appConfig.settings.securitySettings.casUsername, appConfig.settings.securitySettings.casPassword)
    new CasAuthenticatingClient(appConfig.securityContext.casClient, params, org.http4s.client.blaze.defaultClient)
  }
}
