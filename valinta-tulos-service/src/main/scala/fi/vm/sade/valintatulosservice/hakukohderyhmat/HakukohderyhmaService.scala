package fi.vm.sade.valintatulosservice.hakukohderyhmat

import com.github.blemale.scaffeine.Scaffeine
import fi.vm.sade.javautils.nio.cas.{CasClient, CasClientBuilder}
import fi.vm.sade.security.ScalaCasConfig
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.memoize.TTLOptionalMemoize
import fi.vm.sade.valintatulosservice.ohjausparametrit.Ohjausparametrit
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakuOid, HakukohdeOid, HakukohderyhmaOid}
import org.asynchttpclient.{RequestBuilder, Response}
import org.json4s.jackson.JsonMethods._

import java.util.concurrent.{CompletableFuture, TimeUnit}
import scala.compat.java8.FutureConverters.toScala
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration


class HakukohderyhmaService(appConfig: VtsAppConfig) extends Logging {

  protected val callerId: String = appConfig.settings.callerId
  protected val casUrl: String = appConfig.ophUrlProperties.url("cas.url")
  protected val casUsername: String = appConfig.settings.securitySettings.casUsername
  protected val casPassword: String = appConfig.settings.securitySettings.casPassword
  protected val loginParams: String = "/auth/cas"
  protected val sessionCookieName: String = "ring-session"
  protected val serviceName: String = appConfig.ophUrlProperties.url("hakukohderyhmapalvelu.service")

  lazy protected val client: CasClient = {
    CasClientBuilder.build(ScalaCasConfig(
      casUsername,
      casPassword,
      casUrl,
      serviceName,
      callerId,
      callerId,
      loginParams,
      sessionCookieName)
    )
  }

  def getHakukohderyhmat(oid: HakukohdeOid): Future[Seq[HakukohderyhmaOid]] = {
    fetch(appConfig.ophUrlProperties.url("hakukohderyhmapalvelu.hakukohderyhmat", oid)).flatMap {
      case response if response.getStatusCode.equals(200) => Future.successful(parse(response.getResponseBody).values.asInstanceOf[Seq[String]].map(s => HakukohderyhmaOid(s)))
      case failure =>
        val errorString = s"Hakukohderyhmät fetch failed for hakukohdeoid: $oid with status ${failure.getStatusCode}, body: ${failure.getResponseBody}"
        logger.error(errorString)
        Future.failed(
          new RuntimeException(errorString)
        )
    }
  }

  def getHakukohteet(oid: HakukohderyhmaOid): Future[Seq[HakukohdeOid]] = {
    fetch(appConfig.ophUrlProperties.url("hakukohderyhmapalvelu.hakukohteet", oid)).flatMap {
      case response if response.getStatusCode.equals(200) => Future.successful(parse(response.getResponseBody).values.asInstanceOf[Seq[String]].map(s => HakukohdeOid(s)))
      case failure =>
        val errorString = s"Hakukohteet fetch failed for hakukohderyhmäoid: $oid with status ${failure.getStatusCode}, body: ${failure.getResponseBody}"
        logger.error(errorString)
        Future.failed(
          new RuntimeException(errorString)
        )
    }
  }

  protected def fetch(url: String): Future[Response] = {
    logger.info(s"Fetching from hakukohderyhmapalvelu: $url")
    val requestBuilder = new RequestBuilder()
      .setMethod("GET")
      .setUrl(url)
    val request = requestBuilder.build()
    val future: CompletableFuture[Response] = client.execute(request)
    toScala(future)
  }

//  def getHakukohteet(oid: HakukohderyhmaOid): Future[Seq[HakukohdeOid]] = {
//    hakukohdeCache.getFuture(oid, getHakukohteet)
//  }
//
//  def getHakukohderyhmat(oid: HakukohdeOid): Future[Seq[HakukohderyhmaOid]] = {
//    hakukohderyhmaCache.getFuture(oid, getHakukohderyhmat)
//  }
}