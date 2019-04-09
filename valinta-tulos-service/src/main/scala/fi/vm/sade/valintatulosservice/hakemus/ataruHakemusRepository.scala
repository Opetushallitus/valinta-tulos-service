package fi.vm.sade.valintatulosservice.hakemus

import java.util.concurrent.TimeUnit

import fi.vm.sade.utils.cas.{CasAuthenticatingClient, CasParams}
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.http4s.Method.POST
import org.http4s.json4s.native.{jsonEncoder, jsonExtract}
import org.http4s.{Request, Uri}
import org.json4s.Formats
import org.json4s.JsonAST._
import org.json4s.JsonDSL._

import scala.concurrent.duration.Duration
import scalaz.concurrent.Task

case class AtaruHakemus(oid: HakemusOid,
                        hakuOid: HakuOid,
                        hakukohdeOids: List[HakukohdeOid],
                        henkiloOid: HakijaOid,
                        asiointikieli: String,
                        email: String)

case class AtaruResponse(applications: List[AtaruHakemus], offset: Option[String])

sealed trait HakemuksetQuery {
  val postData: JObject
  def withOffset(offset: Option[String]): HakemuksetQuery
}

case class WithHakuOid(hakuOid: HakuOid, offset: Option[String]) extends HakemuksetQuery {
  override val postData: JObject = ("hakuOid" -> hakuOid.s) ~ offset.fold(JObject())("offset" -> _)

  override def withOffset(newOffset: Option[String]): HakemuksetQuery = this.copy(offset = newOffset)
}

case class WithHakukohdeOid(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid, offset: Option[String]) extends HakemuksetQuery {
  override val postData: JObject = ("hakuOid" -> hakuOid.s) ~ ("hakukohdeOid" -> hakukohdeOid.s) ~ offset.fold(JObject())("offset" -> _)

  override def withOffset(newOffset: Option[String]): HakemuksetQuery = this.copy(offset = newOffset)
}

case class WithHakemusOids(hakemusOids: List[HakemusOid], offset: Option[String]) extends HakemuksetQuery {
  override val postData: JObject = ("hakemusOids" -> hakemusOids.map(_.s)) ~ offset.fold(JObject())("offset" -> _)

  override def withOffset(newOffset: Option[String]): HakemuksetQuery = this.copy(offset = newOffset)
}

class AtaruHakemusRepository(config: VtsAppConfig) extends JsonFormats {

  private implicit val jsonFormat: Formats = JsonFormats.jsonFormats

  private val params = CasParams(
    config.ophUrlProperties.url("url-ataru-service"),
    "auth/cas",
    config.settings.securitySettings.casUsername,
    config.settings.securitySettings.casPassword
  )
  private val client = CasAuthenticatingClient(
    casClient = config.securityContext.casClient,
    casParams = params,
    serviceClient = org.http4s.client.blaze.defaultClient,
    clientCallerId = config.callerId,
    sessionCookieName = "ring-session"
  )

  def getHakemukset(query: HakemuksetQuery): Either[Throwable, AtaruResponse] = {
    Uri.fromString(config.ophUrlProperties.url("ataru-service.applications"))
      .fold(Task.fail, uri => {
        client.fetch(Request(method = POST, uri = uri).withBody(query.postData)(jsonEncoder[JObject])) {
          case r if r.status.code == 200 => r.as[AtaruResponse](jsonExtract[AtaruResponse])
            .handleWith { case t => Task.fail(new IllegalStateException(s"Parsing hakemukset for $query failed", t)) }
          case r => r.bodyAsText.runLast
            .flatMap(body => Task.fail(new RuntimeException(s"Failed to get hakemus for query $query: ${body.getOrElse("Failed to parse body")}")))
        }
      }).attemptRunFor(Duration(1, TimeUnit.MINUTES)).toEither
  }
}
