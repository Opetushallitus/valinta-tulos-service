package fi.vm.sade.valintatulosservice.hakukohderyhmat

import fi.vm.sade.javautils.nio.cas.{CasClient, CasClientBuilder, CasConfig}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, HakukohderyhmaOid}
import org.asynchttpclient.{RequestBuilder, Response}
import org.http4s.Method
import org.json4s.jackson.JsonMethods._

import java.util.concurrent.CompletableFuture
import scala.compat.java8.FutureConverters.toScala
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object ScalaCasConfig {

  def apply(username: String,
            password: String,
            casUrl: String,
            serviceUrl: String,
            csrf: String,
            callerId: String,
            serviceUrlSuffix: String,
            jSessionName: String): CasConfig = {

    new CasConfig.CasConfigBuilder(
      username,
      password,
      casUrl,
      serviceUrl,
      csrf,
      callerId,
      serviceUrlSuffix
    ).setJsessionName(jSessionName).build()
  }
}


class HakukohderyhmaService(appConfig: VtsAppConfig) extends Logging {
  protected val loginParams: String = "/auth/cas"
  protected val sessionCookieName: String = "ring-session"
  protected val serviceName: String = appConfig.ophUrlProperties.url("hakukohderyhmapalvelu.service")
  protected val callerId: String = appConfig.settings.callerId
  protected val casUrl: String = appConfig.ophUrlProperties.url("cas.url")

  lazy protected val client: CasClient = {
    CasClientBuilder.build(ScalaCasConfig(
      appConfig.settings.securitySettings.casUsername,
      appConfig.settings.securitySettings.casPassword,
      casUrl,
      serviceName,
      callerId,
      callerId,
      loginParams,
      sessionCookieName)
    )
  }

  def getHakukohderyhmat(oid: HakukohdeOid): Future[Seq[HakukohderyhmaOid]] = {
    logger.info(s"getHakukohderyhmat with oid: $oid")
    fetch(Method.GET, appConfig.ophUrlProperties.url("hakukohderyhmapalvelu.hakukohderyhmat", oid)).flatMap {
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
    fetch(Method.GET, appConfig.ophUrlProperties.url("hakukohderyhmapalvelu.hakukohteet", oid)).flatMap {
      case response if response.getStatusCode.equals(200) => Future.successful(parse(response.getResponseBody).values.asInstanceOf[Seq[String]].map(s => HakukohdeOid(s)))
      case failure =>
        val errorString = s"Hakukohteet fetch failed for hakukohderyhmäoid: $oid with status ${failure.getStatusCode}, body: ${failure.getResponseBody}"
        logger.error(errorString)
        Future.failed(
          new RuntimeException(errorString)
        )
    }
  }

  protected def fetch(method: Method, url: String): Future[Response] = {
    val requestBuilder = new RequestBuilder()
      .setMethod(method.name)
      .setUrl(url)
    logger.info(s"Hakukohderyhmapalvelu fetch for oid: $url")
    val request = requestBuilder.build()
    val future: CompletableFuture[Response] = client.execute(request)
    toScala(future)
  }
}
