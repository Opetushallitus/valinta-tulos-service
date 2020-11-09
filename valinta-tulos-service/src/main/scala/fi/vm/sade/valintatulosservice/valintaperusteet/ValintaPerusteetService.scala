package fi.vm.sade.valintatulosservice.valintaperusteet

import java.time.LocalDateTime

import fi.vm.sade.utils.http.DefaultHttpClient
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.AppConfig
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.ValintatapajonoOid
import org.json4s.jackson.JsonMethods.parse
import scalaj.http.HttpOptions

import scala.util.Try
import scala.util.control.NonFatal

class ValintaPerusteetService(appConfig:AppConfig) extends Logging {
  import org.json4s._
  implicit val formats = DefaultFormats

  case class ValintatapaJono(aloituspaikat: String, nimi: String, kuvaus: String, tyyppi: String, siirretaanSijoitteluun: Boolean,
                             tasapistesaanto: String, aktiivinen: Boolean, valisijoittelu: Boolean, getautomaattinenSijoitteluunSiirto: Boolean,
                             eiVarasijatayttoa: Boolean, kaikkiEhdonTayttavatHyvaksytaan: Boolean, varasijat: Int, varasijaTayttoPaivat: Int,
                             poissaOlevaTaytto: Boolean, poistetaankoHylatyt: Boolean, varasijojaKaytetaanAlkaen: LocalDateTime, varasijojaTaytetaanAsti: LocalDateTime,
                             eiLasketaPaivamaaranJalkeen: LocalDateTime, kaytetaanValintalaskentaa: Boolean, tayttojono: String, oid: String, inheritance: Boolean,
                             prioriteetti: Int
                            )

  def getKaytetaanValintalaskentaaFromValintatapajono(valintapajonoOid: ValintatapajonoOid):Boolean = {
    getValintatapajonoByValintatapajonoOid(valintapajonoOid) match {
      case Right(valintatapaJono) => valintatapaJono.kaytetaanValintalaskentaa
      case Left(_) => false
    }
  }

  def getValintatapajonoByValintatapajonoOid(valintatapajonoOid: ValintatapajonoOid): Either[Throwable, ValintatapaJono] = {
    val url = appConfig.ophUrlProperties.url("valintaperusteet-service.valintatapajono", valintatapajonoOid.toString)

    fetch(url) { response =>
      parse(response).extract[ValintatapaJono]
    }.left.map {
      case e: Exception => new RuntimeException(s"Failed to get valintapajono $valintatapajonoOid details", e)
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
      case (404, _, resultString) =>
        Left(new IllegalArgumentException(s"User not found"))
      case (responseCode, _, resultString) =>
        Left(new RuntimeException(s"GET $url failed with status $responseCode: $resultString"))
    }).recover {
      case NonFatal(e) => Left(new RuntimeException(s"GET $url failed", e))
    }.get
  }

}

