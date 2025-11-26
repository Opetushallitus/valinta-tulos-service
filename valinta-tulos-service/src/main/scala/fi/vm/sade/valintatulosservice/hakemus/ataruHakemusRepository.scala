package fi.vm.sade.valintatulosservice.hakemus

import java.util.concurrent.TimeUnit

import fi.vm.sade.javautils.nio.cas.{CasClient, CasClientBuilder}
import fi.vm.sade.security.ScalaCasConfig
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.asynchttpclient.{RequestBuilder, Response}
import org.json4s.native.JsonMethods.parse
import org.json4s.native.Serialization.write
import org.json4s.Formats
import org.json4s.JsonAST._
import org.json4s.JsonDSL._

import scala.compat.java8.FutureConverters.toScala
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration.Duration

case class AtaruHakemus(oid: HakemusOid,
                        hakuOid: HakuOid,
                        hakukohdeOids: List[HakukohdeOid],
                        henkiloOid: HakijaOid,
                        asiointikieli: String,
                        email: String,
                        paymentObligations: Map[String, String])

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

  private val client =
    config.securityContext.javaCasClient.getOrElse(
      CasClientBuilder.build(ScalaCasConfig(
        config.settings.securitySettings.casUsername,
        config.settings.securitySettings.casPassword,
        config.settings.securitySettings.casUrl,
        config.ophUrlProperties.url("url-ataru-service"),
        config.settings.callerId,
        config.settings.callerId,
        "/auth/cas",
        "ring-session"
      )))

  def getHakemukset(query: HakemuksetQuery): Either[Throwable, AtaruResponse] = {
    val req = new RequestBuilder()
      .setMethod("POST")
      .setUrl(config.ophUrlProperties.url("ataru-service.applications"))
      .addHeader("Content-type", "application/json")
      .setBody(write(query))
      .build()
    val result = toScala(client.execute(req)).map {
      case r if r.getStatusCode() == 200  =>
        Right(parse(r.getResponseBodyAsStream()).extract[AtaruResponse])
      case r =>
        Left(new RuntimeException(s"Failed to get hakemus for query $query: ${r.getResponseBody()}"))
    }
    try {
      Await.result(result, Duration(1, TimeUnit.MINUTES))
    } catch {
      case e: Throwable => Left(e)
    }
  }
}
