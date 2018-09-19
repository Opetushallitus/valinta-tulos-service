package fi.vm.sade.valintatulosservice.hakemus

import java.util.concurrent.TimeUnit

import fi.vm.sade.utils.cas.{CasAuthenticatingClient, CasParams}
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.http4s.Method.GET
import org.http4s.json4s.native.jsonExtract
import org.http4s.{Request, Uri}

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scalaz.concurrent.Task

case class AtaruHakutoive(processingState: String,
                          eligibilityState: String,
                          paymentObligation: String,
                          hakukohdeOid: String)

case class AtaruHakemus(oid: HakemusOid,
                        hakuOid: HakuOid,
                        henkiloOid: HakijaOid,
                        asiointikieli: String,
                        hakutoiveet: List[AtaruHakutoive],
                        email: Option[String])

sealed trait HakemuksetQuery

case class WithHakuOid(hakuOid: HakuOid,
                       hakukohdeOid: Option[HakukohdeOid],
                       hakemusOids: Option[List[HakemusOid]]) extends HakemuksetQuery

case class WithHakemusOids(hakuOid: Option[HakuOid],
                           hakukohdeOid: Option[HakukohdeOid],
                           hakemusOids: List[HakemusOid]) extends HakemuksetQuery

class AtaruHakemusRepository(config: VtsAppConfig) extends JsonFormats {

  private implicit val jsonFormat = JsonFormats.jsonFormats

  private val params = CasParams(
    config.ophUrlProperties.url("url-ataru-service"),
    "auth/cas",
    config.settings.securitySettings.casUsername,
    config.settings.securitySettings.casPassword
  )
  private val client = CasAuthenticatingClient(
    config.securityContext.casClient,
    params,
    org.http4s.client.blaze.defaultClient,
    Some("valinta-tulos-service"),
    "ring-session"
  )

  def getHakemukset(query: HakemuksetQuery): Either[Throwable, List[AtaruHakemus]] = {
    val params = query match {
      case WithHakuOid(hakuOid, hakukohdeOid, hakemusOids) =>
        (Option("hakuOid" -> hakuOid.toString) ++
          hakukohdeOid.map("hakukohdeOid" -> _.toString) ++
          hakemusOids.map("hakemusOids" -> _.mkString(","))).toMap
      case WithHakemusOids(hakuOid, hakukohdeOid, hakemusOids) =>
        (hakuOid.map("hakuOid" -> _.toString) ++
          hakukohdeOid.map("hakukohdeOid" -> _.toString) ++
          Option("hakemusOids" -> hakemusOids.mkString(","))).toMap
    }
    Uri.fromString(config.ophUrlProperties.url("ataru-service.applications", params.asJava))
      .fold(Task.fail, uri => {
        client.fetch(Request(method = GET, uri = uri)) {
          case r if r.status.code == 200 => r.as[List[AtaruHakemus]](jsonExtract[List[AtaruHakemus]])
            .handleWith { case t => Task.fail(new IllegalStateException(s"Parsing hakemukset for $query failed", t)) }
          case r => Task.fail(new RuntimeException(s"Failed to get hakemukset for $query: ${r.toString()}"))
        }
      }).attemptRunFor(Duration(1, TimeUnit.MINUTES)).toEither
  }

  def getHakemusToHakijaOidMapping(hakuOid: HakuOid, hakukohdeOids: Option[List[HakukohdeOid]]): Either[Throwable, Map[HakemusOid, String]] = {
    val params = (Option("hakuOid" -> hakuOid.toString) ++
      hakukohdeOids.map("hakukohdeOids" -> _.mkString(","))).toMap.asJava
    Uri.fromString(config.ophUrlProperties.url("ataru-service.persons", params))
      .fold(Task.fail, uri => {
        client.fetch(Request(method = GET, uri = uri)) {
          case r if r.status.code == 200 => r.as[Map[HakemusOid, String]](jsonExtract[Map[HakemusOid, String]])
            .handleWith { case t => Task.fail(new IllegalStateException(s"Parsing hakemukset for $params failed", t)) }
          case r => Task.fail(new RuntimeException(s"Failed to get hakemukset for $params: ${r.toString()}"))
        }
      }).attemptRunFor(Duration(1, TimeUnit.MINUTES)).toEither
  }
}
