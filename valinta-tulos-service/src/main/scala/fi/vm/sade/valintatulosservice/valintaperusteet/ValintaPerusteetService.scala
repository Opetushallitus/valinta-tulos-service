package fi.vm.sade.valintatulosservice.valintaperusteet

import fi.vm.sade.javautils.nio.cas.CasClientBuilder
import fi.vm.sade.security.ScalaCasConfig
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.logging.Logging
import fi.vm.sade.valintatulosservice.tarjonta.{Haku, HakuFixtures}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, NotFoundException, ValintatapajonoOid}
import org.asynchttpclient.RequestBuilder
import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods.parse

import java.util.concurrent.TimeUnit
import scala.compat.java8.FutureConverters.toScala
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

trait ValintaPerusteetService {
  def getKaytetaanValintalaskentaaFromValintatapajono(valintatapajonoOid: ValintatapajonoOid, haku: Haku, hakukohdeOid: HakukohdeOid): Either[Throwable, Boolean]
}

class ValintaPerusteetServiceImpl(appConfig: VtsAppConfig) extends ValintaPerusteetService with Logging {
  private val client = CasClientBuilder.build(ScalaCasConfig(
    appConfig.settings.securitySettings.casUsername,
    appConfig.settings.securitySettings.casPassword,
    appConfig.settings.securitySettings.casUrl,
    appConfig.settings.valintaPerusteetServiceUrl,
    appConfig.settings.callerId,
    appConfig.settings.callerId,
    "/j_spring_cas_security_check",
    "JSESSIONID"
  ))

  implicit val formats = DefaultFormats

  def getKaytetaanValintalaskentaaFromValintatapajono(
    valintatapajonoOid: ValintatapajonoOid,
    haku: Haku,
    hakukohdeOid: HakukohdeOid
  ): Either[Throwable, Boolean] = {

    val req = new RequestBuilder()
      .setMethod("GET")
      .setUrl(appConfig.ophUrlProperties.url("valintaperusteet-service.valintatapajono",
        valintatapajonoOid.toString))
      .build()

    val result = toScala(client.execute(req)).map {
      case r if r.getStatusCode == 200 => {
        logger.info(
          s"Successfully got valintatapajono (oid: $valintatapajonoOid) from valintaperusteet-service for haku: ${haku.oid}, hakukohde: $hakukohdeOid"
        )
        (parse(r.getResponseBodyAsStream) \ "kaytetaanValintalaskentaa").extract[Boolean]
      }
      case r if r.getStatusCode == 404 =>
        throw new NotFoundException(s"Valintatapajono was not found for oid $valintatapajonoOid")
      case r =>
        throw new RuntimeException(
          s"Failed to get valintatapajono for oid $valintatapajonoOid, hakukohde: $hakukohdeOid, haku: ${haku.oid}, body: ${r.getResponseBody}"
        )
    }

    try {
      Right(Await.result(result, Duration(1, TimeUnit.MINUTES)))
    } catch {
      case e: Throwable => Left(e)
    }
  }
}

class ValintaPerusteetServiceMock extends ValintaPerusteetService {
  override def getKaytetaanValintalaskentaaFromValintatapajono(valintatapajonoOid: ValintatapajonoOid, haku: Haku, hakukohdeOid: HakukohdeOid): Either[Throwable, Boolean] = {
    if (valintatapajonoOid.toString == "14090336922663576781797489829886" && haku.oid != HakuFixtures.korkeakouluErillishakuEiSijoittelua) {
      return Right(true)
    }
    Right(false)
  }
}

