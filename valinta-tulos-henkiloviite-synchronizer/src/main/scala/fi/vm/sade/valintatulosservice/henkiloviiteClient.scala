package fi.vm.sade.valintatulosservice

import java.util.concurrent.TimeUnit

import fi.vm.sade.utils.cas.{CasAuthenticatingClient, CasClient, CasParams}
import org.http4s.Status.ResponseClass.Successful
import org.http4s._
import org.http4s.client.Client
import org.http4s.headers.`Content-Type`
import org.http4s.json4s.native.jsonOf
import org.json4s.DefaultReaders.{StringReader, arrayReader}
import org.json4s.JsonAST.JValue
import org.json4s.Reader
import scalaz.concurrent.Task
import scalaz.{-\/, \/-}

import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

case class Henkiloviite(masterOid: String, henkiloOid: String)
case class Duplicates(tyyppi: String)

class HenkiloviiteClient(configuration: AuthenticationConfiguration) {
  private val resourceUrl: Uri = configuration.url
  private val client = createCasClient()

  def fetchHenkiloviitteet(): Try[List[Henkiloviite]] = {
    val request = Request(
      method = Method.POST,
      uri = resourceUrl
    ).withBody("{}")(EntityEncoder.stringEncoder(Charset.`UTF-8`).withContentType(`Content-Type`(MediaType.`application/json`)))

    client.fetch(request) {
      case Successful(response) => response.as[Array[Henkiloviite]](HenkiloviiteClient.henkiloviiteDecoder).map(_.toList)
      case response => Task.fail(new RuntimeException(s"Request $request failed with response $response"))
    }.attemptRunFor(Duration(1, TimeUnit.MINUTES)) match {
      case \/-(results) => Success(results)
      case -\/(e) => Failure(e)
    }
  }

  private def createCasClient(): Client = {
    val casParams = CasParams("/oppijanumerorekisteri-service", configuration.cas.user, configuration.cas.password)
    CasAuthenticatingClient(
      casClient = new CasClient(configuration.cas.host, org.http4s.client.blaze.defaultClient),
      casParams = casParams,
      serviceClient = org.http4s.client.blaze.defaultClient,
      clientSubSystemCode = Some("valinta-tulos-henkiloviite-synchronizer"),
      sessionCookieName = "JSESSIONID"
    )
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
