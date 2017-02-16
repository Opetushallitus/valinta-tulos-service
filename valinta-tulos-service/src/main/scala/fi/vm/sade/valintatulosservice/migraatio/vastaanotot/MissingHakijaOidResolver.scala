package fi.vm.sade.valintatulosservice.migraatio.vastaanotot

import java.net.URLEncoder

import fi.vm.sade.utils.cas.{CasAuthenticatingClient, CasParams}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.json.JsonFormats
import org.http4s._
import org.http4s.headers.`Content-Type`
import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.JsonMethods._

import scala.util.{Failure, Success, Try}
import scalaz.concurrent.Task

class MissingHakijaOidResolver(appConfig: VtsAppConfig) extends JsonFormats with Logging {
  private val hakuClient = createCasClient(appConfig, "/haku-app")
  private val henkiloClient = createCasClient(appConfig, "/authentication-service")
  private val hakuUrlBase = appConfig.settings.ophUrlProperties.url("haku-app.listfull.queryBase")
  private val henkiloPalveluUrlBase = appConfig.settings.ophUrlProperties.url("authentication-service.henkilo")

  case class HakemusHenkilo(personOid: Option[String], hetu: String, etunimet: String, sukunimi: String, kutsumanimet: String,
                            syntymaaika: String, aidinkieli: String, sukupuoli: String)

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

  private def findPersonOidByHetu(hetu: String): Option[String] = {
    def parsePersonOid(response: String): Option[String] = (parse(response) \ "results" \\ "oidHenkilo").extractOpt[String]

    Try(henkiloClient.prepare(createUri(henkiloPalveluUrlBase + "?q=", hetu)).flatMap {
      case r if 200 == r.status.code => r.as[String]
      case r => Task.fail(new RuntimeException(r.toString))
    }.run) match {
      case Success(response) => parsePersonOid(response)
      case Failure(t) => handleFailure(t, "searching person oid by hetu")
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
