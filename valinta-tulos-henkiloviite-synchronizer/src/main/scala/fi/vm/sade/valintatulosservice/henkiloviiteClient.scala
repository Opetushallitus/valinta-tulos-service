package fi.vm.sade.valintatulosservice

import java.util.concurrent.TimeUnit

import fi.vm.sade.javautils.nio.cas.{CasClient, CasClientBuilder}
import fi.vm.sade.security.ScalaCasConfig
import org.asynchttpclient.{RequestBuilder, Response}
import org.json4s.native.JsonMethods.parse
import org.json4s.DefaultReaders.{StringReader, arrayReader}
import org.json4s.JsonAST.JValue
import org.json4s.{Reader, Formats, DefaultFormats}
import org.http4s.json4s.native.jsonOf  // TODO get rid of this
import org.http4s._  // TODO this too
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.compat.java8.FutureConverters.toScala
import scala.concurrent.Await
import scalaz.concurrent.Task
import scalaz.{-\/, \/-}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

case class Henkiloviite(masterOid: String, henkiloOid: String)
case class Duplicates(tyyppi: String)

class HenkiloviiteClient(configuration: AuthenticationConfiguration) {
  val logger = LoggerFactory.getLogger(classOf[HenkiloviiteClient])
  private val resourceUrl: Uri = configuration.url
  private val callerId = "1.2.246.562.10.00000000001.valinta-tulos-henkiloviite-synchronizer"
  private val client = createCasClient()  // order dep; needs to be last

  def fetchHenkiloviitteet(): Try[List[Henkiloviite]] = {
    implicit val formats: Formats = DefaultFormats
    val request = new RequestBuilder()
      .setMethod("POST")
      .setUrl(resourceUrl.renderString)
      .addHeader("Content-type", "application/json")
      .setBody("{}")
      .build()
    val result = toScala(client.execute(request)).map {
      case r if r.getStatusCode() == 200 =>
        Success(parse(r.getResponseBodyAsStream()).extract[List[Henkiloviite]])
      case r =>
        Failure(new RuntimeException(s"Request $request failed with response $r"))
    }
    try {
      Await.result(result, Duration(1, TimeUnit.MINUTES))
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  private def createCasClient(): CasClient = {
    val config = ScalaCasConfig(
      configuration.cas.user,
      configuration.cas.password,
      configuration.cas.host, 
      configuration.cas.service,
      callerId,
      callerId,
      "/j_spring_cas_security_check",
      "JSESSIONID"
    )
    logger.info(s"Using CAS config: user: ${config.getUsername} host: ${config.getCasUrl} service: ${config.getServiceUrl}")
    CasClientBuilder.build(config)
  }

}

object HenkiloviiteClient {
  val henkiloviiteReader = new Reader[Henkiloviite] {
    override def read(v: JValue): Henkiloviite = {
      Henkiloviite(StringReader.read(v \ "masterOid"), StringReader.read(v \ "henkiloOid"))
    }
  }
  val henkiloviiteDecoder = jsonOf[Array[Henkiloviite]](
    arrayReader[Henkiloviite](manifest[Henkiloviite], HenkiloviiteClient.henkiloviiteReader)
  )
}
