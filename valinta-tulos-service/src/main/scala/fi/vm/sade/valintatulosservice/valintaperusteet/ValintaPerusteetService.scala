package fi.vm.sade.valintatulosservice.valintaperusteet

import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

import fi.vm.sade.utils.cas.{CasAuthenticatingClient, CasParams}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.tarjonta.{Haku, HakuFixtures}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.ValintatapajonoOid
import org.http4s.Method.GET
import org.http4s.client.blaze.SimpleHttp1Client
import org.http4s.json4s.native.jsonExtract
import org.http4s.{Request, Uri}
import scalaz.concurrent.Task

import scala.concurrent.duration.Duration

trait ValintaPerusteetService {
  def getKaytetaanValintalaskentaaFromValintatapajono(valintatapajonoOid: ValintatapajonoOid, haku: Haku): Either[Throwable, Boolean]
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
    Uri.fromString(appConfig.ophUrlProperties.url("valintaperusteet-service.valintatapajono", valintatapajonoOid.toString))
      .fold(Task.fail, uri => {
        client.fetch(Request(method = GET, uri = uri)) {
          case r if r.status.code == 200 => r.as[ValintatapaJono](jsonExtract[ValintatapaJono])
            .handleWith { case t => Task.fail(new IllegalStateException(s"Parsing valintatapajono for $valintatapajonoOid failed", t)) }
          case r => r.bodyAsText.runLast
            .flatMap(body => Task.fail(new RuntimeException(s"Failed to get valintatapajono for oid $valintatapajonoOid, haku: ${haku.oid}: ${body.getOrElse("Failed to parse body")}")))
        }
      }).attemptRunFor(Duration(1, TimeUnit.MINUTES)).toEither


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

