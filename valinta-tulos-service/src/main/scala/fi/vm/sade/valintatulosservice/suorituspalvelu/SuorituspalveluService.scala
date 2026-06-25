package fi.vm.sade.valintatulosservice.suorituspalvelu

import fi.vm.sade.javautils.nio.cas.CasClientBuilder
import fi.vm.sade.security.ScalaCasConfig
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakijaOid, HakuOid, HakukohdeOid, PaatettavaOpiskeluOikeus}
import fi.vm.sade.valintatulosservice.logging.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import org.asynchttpclient.RequestBuilder
import org.json4s.native.JsonMethods.parse
import org.json4s.native.Serialization

import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.TimeUnit
import scala.compat.java8.FutureConverters.toScala
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class SuorituspalveluService(config: VtsAppConfig, db: ValintarekisteriDb) extends JsonFormats with Logging {

  private val client = CasClientBuilder.build(ScalaCasConfig(
    config.settings.securitySettings.casUsername,
    config.settings.securitySettings.casPassword,
    config.settings.securitySettings.casUrl,
    config.ophUrlProperties.url("url-suorituspalvelu"),
    config.settings.callerId,
    config.settings.callerId,
    "/api/login/j_spring_cas_security_check",
    "JSESSIONID"
  ))

  def getPaatettavatOpiskeluOikeudet(hakijaOid: HakijaOid, hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): List[PaatettavaOpiskeluOikeus] = {
    logger.info(s"Haetaan päättyvät opiskeluoikeudet hakijalle $hakijaOid, haulle $hakuOid, hakukohteelle $hakukohdeOid")
    val url =
      s"${config.ophUrlProperties.url("url-suorituspalvelu")}/api/v1/yos/hakija/$hakijaOid/haku/$hakuOid/hakukohde/$hakukohdeOid/opiskeluoikeudet"
    fetchOikeudet(url) match {
      case Left(e) =>
        logger.error(
          s"Virhe päättyvien opiskeluoikeuksien hakemisessa, hakijaOid=$hakijaOid, hakuOid=$hakuOid, hakukohdeOid=$hakukohdeOid: ${e.getMessage}"
        )
        List.empty
      case Right(o) =>
        o
    }
  }

  def getAndStorePaatettavatOpiskeluOikeudet(hakijaOid: HakijaOid, hakuOid: HakuOid, hakukohdeOid: HakukohdeOid, hakemusOid: HakemusOid): List[PaatettavaOpiskeluOikeus] = {
    val oikeudet = getPaatettavatOpiskeluOikeudet(hakijaOid, hakuOid, hakukohdeOid)
    if (oikeudet.nonEmpty) {
      db.storePaatetettavatOpiskeluOikeudet(hakijaOid.toString, hakukohdeOid, hakemusOid, Serialization.write(oikeudet))
    }
    oikeudet
  }

  private def fetchOikeudet(url: String): Either[Throwable, List[PaatettavaOpiskeluOikeus]] = {
    val req = new RequestBuilder().setMethod("GET").setUrl(url).build()
    val result = toScala(client.execute(req)).map {
      case r if r.getStatusCode == 200 =>
        Right(parse(r.getResponseBodyAsStream).extract[List[PaatettavaOpiskeluOikeus]])
      case r =>
        val message = s"GET $url failed with status ${r.getStatusCode}: ${r.getResponseBody}"
        if (r.getStatusCode == 404) {
          throw new IllegalArgumentException(message)
        } else {
          throw new RuntimeException(message)
        }
    }

    try {
      Await.result(result, Duration(1, TimeUnit.MINUTES))
    } catch {
      case e: Throwable => Left(e)
    }
  }
}
