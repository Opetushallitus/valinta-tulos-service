package fi.vm.sade.valintatulosservice.valintaperusteet

import java.time.LocalDateTime

import fi.vm.sade.utils.http.DefaultHttpClient
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.AppConfig
import fi.vm.sade.valintatulosservice.tarjonta.{Haku, HakuFixtures}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.ValintatapajonoOid
import org.json4s.jackson.JsonMethods.parse
import scalaj.http.HttpOptions

import scala.util.Try
import scala.util.control.NonFatal

trait ValintaPerusteetService {
  def getKaytetaanValintalaskentaaFromValintatapajono(valintatapajonoOid: ValintatapajonoOid, haku: Haku): Either[Throwable, Boolean]
}

class ValintaPerusteetServiceImpl(appConfig: AppConfig) extends ValintaPerusteetService with Logging {

  import org.json4s._

  implicit val formats = DefaultFormats

  case class ValintatapaJono(aloituspaikat: String, nimi: String, kuvaus: String, tyyppi: String, siirretaanSijoitteluun: Boolean,
                             tasapistesaanto: String, aktiivinen: Boolean, valisijoittelu: Boolean, getautomaattinenSijoitteluunSiirto: Boolean,
                             eiVarasijatayttoa: Boolean, kaikkiEhdonTayttavatHyvaksytaan: Boolean, varasijat: Int, varasijaTayttoPaivat: Int,
                             poissaOlevaTaytto: Boolean, poistetaankoHylatyt: Boolean, varasijojaKaytetaanAlkaen: LocalDateTime, varasijojaTaytetaanAsti: LocalDateTime,
                             eiLasketaPaivamaaranJalkeen: LocalDateTime, kaytetaanValintalaskentaa: Boolean, tayttojono: String, oid: String, inheritance: Boolean,
                             prioriteetti: Int
                            )

  def getKaytetaanValintalaskentaaFromValintatapajono(valintapajonoOid: ValintatapajonoOid, haku: Haku): Either[Throwable, Boolean] = {
    getValintatapajonoByValintatapajonoOid(valintapajonoOid, haku) match {
      case Right(valintatapaJono) => Right(valintatapaJono.kaytetaanValintalaskentaa)
      case Left(e) => Left(e)
    }
  }

  def getValintatapajonoByValintatapajonoOid(valintatapajonoOid: ValintatapajonoOid, haku: Haku): Either[Throwable, ValintatapaJono] = {
    val url = appConfig.ophUrlProperties.url("valintaperusteet-service.valintatapajono", valintatapajonoOid.toString)

    fetch(url) { response =>
      parse(response).extract[ValintatapaJono]
    }.left.map {
      case e: Exception => new RuntimeException(s"Failed to get valintapajono $valintatapajonoOid details for haku ${haku.oid}", e)
    }
  }

  private def fetch[T](url: String)(parse: (String => T)): Either[Throwable, T] = {
    Try(DefaultHttpClient.httpGet(
      url,
      HttpOptions.connTimeout(5000),
      HttpOptions.readTimeout(10000)
    )("1.2.246.562.10.00000000001.valinta-tulos-service")
      .responseWithHeaders match {
      case (200, _, resultString) =>
        Try(Right(parse(resultString))).recover {
          case NonFatal(e) => Left(new IllegalStateException(s"Parsing result $resultString of GET $url failed", e))
        }.get
      case (responseCode, _, resultString) =>
        Left(new RuntimeException(s"GET $url failed for with status $responseCode: $resultString"))
    }).recover {
      case NonFatal(e) => Left(new RuntimeException(s"GET $url failed", e))
    }.get
  }
}

class ValintaPerusteetServiceMock extends ValintaPerusteetService {
  override def getKaytetaanValintalaskentaaFromValintatapajono(valintatapajonoOid: ValintatapajonoOid, haku: Haku): Either[Throwable, Boolean] = {
    if (valintatapajonoOid.toString == "14090336922663576781797489829886" && haku.oid != HakuFixtures.korkeakouluErillishakuEiSijoittelua) {
      return Right(true)
    }
    Right(false)
  }
}

