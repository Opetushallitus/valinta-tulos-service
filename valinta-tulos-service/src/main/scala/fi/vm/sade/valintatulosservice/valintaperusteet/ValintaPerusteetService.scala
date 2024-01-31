package fi.vm.sade.valintatulosservice.valintaperusteet

import fi.vm.sade.javautils.nio.cas.{CasClient, CasClientBuilder}
import fi.vm.sade.security.ScalaCasConfig
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.tarjonta.{Haku, HakuFixtures}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, NotFoundException, ValintatapajonoOid}
import org.asynchttpclient.{RequestBuilder, Response}
import org.json4s.native.JsonMethods.parse
import org.json4s.native.Serialization.write
import org.json4s.DefaultFormats

import scalaz.concurrent.Task
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.compat.java8.FutureConverters.toScala
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await

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

  case class ValintatapaJono(
    aloituspaikat: Int,
    nimi: String,
    kuvaus: Option[String],
    tyyppi: Option[String],
    siirretaanSijoitteluun: Boolean,
    tasapistesaanto: String,
    aktiivinen: Boolean,
    valisijoittelu: Boolean,
    automaattinenSijoitteluunSiirto: Boolean,
    eiVarasijatayttoa: Boolean,
    kaikkiEhdonTayttavatHyvaksytaan: Boolean,
    varasijat: Int,
    varasijaTayttoPaivat: Int,
    poissaOlevaTaytto: Boolean,
    poistetaankoHylatyt: Boolean,
    varasijojaKaytetaanAlkaen: Option[String],
    varasijojaTaytetaanAsti: Option[String],
    eiLasketaPaivamaaranJalkeen: Option[String],
    kaytetaanValintalaskentaa: Boolean,
    tayttojono: Option[String],
    oid: String,
    inheritance: Option[Boolean],
    prioriteetti: Option[Int]
  )

  implicit val formats = DefaultFormats

  def getKaytetaanValintalaskentaaFromValintatapajono(valintapajonoOid: ValintatapajonoOid, haku: Haku, hakukohdeOid: HakukohdeOid): Either[Throwable, Boolean] = {
    getValintatapajonoByValintatapajonoOid(valintapajonoOid, haku, hakukohdeOid) match {
      case Right(valintatapaJono) => Right(valintatapaJono.kaytetaanValintalaskentaa)
      case Left(e) => Left(e)
    }
  }

  private def getValintatapajonoByValintatapajonoOid(
    valintatapajonoOid: ValintatapajonoOid,
    haku: Haku,
    hakukohdeOid: HakukohdeOid
  ): Either[Throwable, ValintatapaJono] = {

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
        parse(r.getResponseBodyAsStream).extract[ValintatapaJono]
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

