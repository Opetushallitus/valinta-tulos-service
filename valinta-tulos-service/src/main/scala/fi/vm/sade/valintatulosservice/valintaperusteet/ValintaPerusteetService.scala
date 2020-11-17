package fi.vm.sade.valintatulosservice.valintaperusteet

import java.util.concurrent.TimeUnit

import fi.vm.sade.utils.cas.{CasAuthenticatingClient, CasParams}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.tarjonta.{Haku, HakuFixtures}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, ValintatapajonoOid}
import org.http4s._
import org.http4s.client.blaze.SimpleHttp1Client
import org.http4s.json4s.native.{jsonExtract, jsonOf}
import scalaz.concurrent.Task

import scala.concurrent.duration.Duration

trait ValintaPerusteetService {
  def getKaytetaanValintalaskentaaFromValintatapajono(valintatapajonoOid: ValintatapajonoOid, haku: Haku, hakukohdeOid: HakukohdeOid): Either[Throwable, Boolean]
}

class ValintaPerusteetServiceImpl(appConfig: VtsAppConfig) extends ValintaPerusteetService with Logging {
  private val params = CasParams(
    "/valintaperusteet-service",
    appConfig.settings.securitySettings.casUsername,
    appConfig.settings.securitySettings.casPassword
  )
  private val client = CasAuthenticatingClient(
    casClient = appConfig.securityContext.casClient,
    casParams = params,
    serviceClient = SimpleHttp1Client(appConfig.blazeDefaultConfig),
    clientCallerId = appConfig.settings.callerId,
    sessionCookieName = "JSESSIONID"
  )

  import org.json4s._

  case class ValintatapaJono(aloituspaikat: Int,
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

  def getKaytetaanValintalaskentaaFromValintatapajono(valintapajonoOid: ValintatapajonoOid, haku: Haku, hakukohdeOid: HakukohdeOid): Either[Throwable, Boolean] = {
    getValintatapajonoByValintatapajonoOid(valintapajonoOid, haku, hakukohdeOid) match {
      case Right(valintatapaJono) => Right(valintatapaJono.kaytetaanValintalaskentaa)
      case Left(e) => Left(e)
    }
  }

  private def getValintatapajonoByValintatapajonoOid(valintatapajonoOid: ValintatapajonoOid, haku: Haku, hakukohdeOid: HakukohdeOid): Either[Throwable, ValintatapaJono] = {
    implicit val formats = DefaultFormats
    implicit val valintatapajonoReader = new Reader[ValintatapaJono] {
      def read(value: JValue): ValintatapaJono = {
        ValintatapaJono(
          (value \ "aloituspaikat").extract[Int],
          (value \ "nimi").extract[String],
          (value \ "kuvaus").extract[Option[String]],
          (value \ "tyyppi").extract[Option[String]],
          (value \ "siirretaanSijoitteluun").extract[Boolean],
          (value \ "tasapistesaanto").extract[String],
          (value \ "aktiivinen").extract[Boolean],
          (value \ "valisijoittelu").extract[Boolean],
          (value \ "automaattinenSijoitteluunSiirto").extract[Boolean],
          (value \ "eiVarasijatayttoa").extract[Boolean],
          (value \ "kaikkiEhdonTayttavatHyvaksytaan").extract[Boolean],
          (value \ "varasijat").extract[Int],
          (value \ "varasijaTayttoPaivat").extract[Int],
          (value \ "poissaOlevaTaytto").extract[Boolean],
          (value \ "poistetaankoHylatyt").extract[Boolean],
          (value \ "varasijojaKaytetaanAlkaen").extract[Option[String]],
          (value \ "varasijojaTaytetaanAsti").extract[Option[String]],
          (value \ "eiLasketaPaivamaaranJalkeen").extract[Option[String]],
          (value \ "kaytetaanValintalaskentaa").extract[Boolean],
          (value \ "tayttojono").extract[Option[String]],
          (value \ "oid").extract[String],
          (value \ "inheritance").extract[Option[Boolean]],
          (value \ "prioriteetti").extract[Option[Int]]
        )
      }
    }

    implicit val ValintatapajonoDecoder = jsonOf[ValintatapaJono]

    Uri.fromString(appConfig.ophUrlProperties.url("valintaperusteet-service.valintatapajono", valintatapajonoOid.toString))
      .fold(Task.fail, uri => {
        val request = Request(
          method = Method.GET,
          uri = uri
        )

        client.fetch(request) {
          case r if r.status.code == 200 => {
            logger.info(s"Successfully got valintatapajono (oid: $valintatapajonoOid) from valintaperusteet-service for haku: ${haku.oid}, hakukohde: $hakukohdeOid")
            r.as[ValintatapaJono]
          }
            .handleWith { case t => Task.fail(new IllegalStateException(s"Parsing valintatapajono for oid: $valintatapajonoOid failed", t)) }
          case r => r.bodyAsText.runLast
            .flatMap(body => Task.fail(new RuntimeException(s"Failed to get valintatapajono for oid $valintatapajonoOid, hakukohde: $hakukohdeOid, haku: ${haku.oid}, body: ${body.getOrElse("Failed to parse body")}")))
        }
      }).attemptRunFor(Duration(1, TimeUnit.MINUTES)).toEither
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

