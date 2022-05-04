package fi.vm.sade.valintatulosservice.hakukohderyhmat

import fi.vm.sade.utils.cas.{CasAuthenticatingClient, CasClient, CasParams}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, HakukohderyhmaOid}
import org.http4s.client.blaze.SimpleHttp1Client
import org.http4s.json4s.native.jsonExtract
import org.http4s.{Method, Request, Uri}
import org.json4s.{DefaultFormats, Formats}
import scalaz.concurrent.Task

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

class HakukohderyhmaService(appConfig: VtsAppConfig,
                            casClient: CasClient) extends Logging {
  private implicit val jsonFormats: Formats = DefaultFormats
  private val casParams = CasParams(
    appConfig.ophUrlProperties.url("hakukohderyhmapalvelu.suffix"),
    "auth/cas",
    appConfig.settings.securitySettings.casUsername,
    appConfig.settings.securitySettings.casPassword
  )

  private val client = CasAuthenticatingClient(
    casClient = casClient,
    casParams = casParams,
    serviceClient = SimpleHttp1Client(appConfig.blazeDefaultConfig),
    clientCallerId = appConfig.settings.callerId,
    sessionCookieName = "ring-session"
  )

  def getHakukohderyhmat(oid: HakukohdeOid): Either[Throwable, Seq[HakukohderyhmaOid]] = {
    fetch[Seq[HakukohderyhmaOid]](appConfig.ophUrlProperties.url(appConfig.ophUrlProperties.url("hakukohderyhmapalvelu.hakukohderyhmat", oid)))
  }

  def getHakukohteet(oid: HakukohderyhmaOid): Either[Throwable, Seq[HakukohdeOid]] = {
    fetch[Seq[HakukohdeOid]](appConfig.ophUrlProperties.url(appConfig.ophUrlProperties.url("hakukohderyhmapalvelu.hakukohteet", oid)))
  }

  private def fetch[T](url: String)(implicit manifest: Manifest[T]): Either[Throwable, T] = {
    Uri.fromString(url).flatMap(uri => client.fetch(Request(method = Method.GET, uri = uri)) {
      case r if r.status.code == 200 =>
        r.as[T](jsonExtract[T])
          .handleWith {
            case t => r.bodyAsText.runLog
              .map(_.mkString)
              .flatMap(body => Task.fail(new IllegalStateException(s"Parsing result $body of GET $url failed", t)))
          }
      case r if r.status.code == 404 =>
        r.bodyAsText.runLog
          .map(_.mkString)
          .flatMap(body => Task.fail(new IllegalArgumentException(s"GET $url failed with status 404: $body")))
      case r =>
        r.bodyAsText.runLog
          .map(_.mkString)
          .flatMap(body => Task.fail(new RuntimeException(s"GET $url failed with status ${r.status.code}: $body")))
    }.unsafePerformSyncAttemptFor(Duration(1, TimeUnit.MINUTES))).toEither
  }
}
