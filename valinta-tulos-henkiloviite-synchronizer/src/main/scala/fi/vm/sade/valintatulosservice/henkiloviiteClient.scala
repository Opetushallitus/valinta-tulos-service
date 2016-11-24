package fi.vm.sade.valintatulosservice

import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

import fi.vm.sade.utils.cas.{CasAuthenticatingClient, CasClient, CasParams}
import org.http4s.Status.ResponseClass.Successful
import org.http4s.client.Client
import org.http4s.json4s.native.jsonOf
import org.http4s.{Method, Request}
import org.json4s.DefaultReaders.{StringReader, arrayReader}
import org.json4s.JsonAST.JValue
import org.json4s.Reader

import scala.concurrent.duration.Duration
import scala.util.Try
import scalaz.concurrent.Task

case class Henkiloviite(masterOid: String, henkiloOid: String)

class HenkiloviiteClient(configuration: AuthenticationConfiguration) {
  private val dateFormater = new SimpleDateFormat("yyyy-MM-dd")
  private val resourceUrl = configuration.url.withQueryParam("date", dateFormater.format(configuration.since))
  private val client = createCasClient()

  def fetchHenkiloviitteet(): Try[List[Henkiloviite]] = {
    val request = Request(
      method = Method.GET,
      uri = resourceUrl
    )
    Try(client.fetch(request) {
      case Successful(response) => response.as[Array[Henkiloviite]](HenkiloviiteClient.henkiloviiteDecoder).map(_.toList)
      case response => Task.fail(new RuntimeException(s"Request $request failed with response $response"))
    }.unsafePerformSyncFor(Duration(1, TimeUnit.MINUTES)))
  }

  private def createCasClient(): Client = {
    val casParams = CasParams("/authentication-service", configuration.cas.user, configuration.cas.password)
    CasAuthenticatingClient(
      new CasClient(configuration.cas.host, org.http4s.client.blaze.defaultClient),
      casParams,
      org.http4s.client.blaze.defaultClient,
      null
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
