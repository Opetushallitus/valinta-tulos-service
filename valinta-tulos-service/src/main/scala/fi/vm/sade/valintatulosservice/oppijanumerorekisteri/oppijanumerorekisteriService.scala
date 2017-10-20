package fi.vm.sade.valintatulosservice.oppijanumerorekisteri

import java.util.concurrent.TimeUnit

import fi.vm.sade.utils.cas.{CasAuthenticatingClient, CasParams}
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakijaOid
import org.http4s.Method.POST
import org.http4s.json4s.native.{jsonEncoderOf, jsonOf}
import org.http4s.{Request, Uri}
import org.json4s.DefaultReaders.StringReader
import org.json4s.JsonAST.{JNull, JObject, JString}
import org.json4s.{JValue, Reader, Writer}

import scala.concurrent.duration.Duration
import scalaz.concurrent.Task

case class Hetu(s: String) {
  override def toString: String = s
}
case class Henkilo(oid: HakijaOid, hetu: Option[Hetu], kutsumanimi: Option[String])

object Henkilo {
  val henkiloReader = new Reader[Henkilo] {
    override def read(value: JValue): Henkilo = {
      Henkilo(
        HakijaOid(StringReader.read(value \ "oidHenkilo")),
        Option(StringReader.read(value \ "hetu")).map(Hetu),
        Option(StringReader.read(value \ "kutsumanimi"))
      )
    }
  }
  val henkiloWriter = new Writer[Henkilo] {
    override def write(h: Henkilo): JValue = {
      JObject(
        "oidHenkilo" -> JString(h.oid.toString),
        "hetu" -> h.hetu.map(s => JString(s.toString)).getOrElse(JNull),
        "kutsumanimi" -> h.kutsumanimi.map(JString).getOrElse(JNull)
      )
    }
  }
}

class OppijanumerorekisteriService(appConfig: VtsAppConfig) {
  private val params = CasParams(
    "/oppijanumerorekisteri-service",
    appConfig.settings.securitySettings.casUsername,
    appConfig.settings.securitySettings.casPassword
  )
  private val client = new CasAuthenticatingClient(
    appConfig.securityContext.casClient,
    params,
    org.http4s.client.blaze.defaultClient,
    "valinta-tulos-service"
  )

  def henkilot(oids: Set[HakijaOid]): Either[Throwable, Set[Henkilo]] = {
    import org.json4s.DefaultWriters.{StringWriter, arrayWriter}
    implicit val henkiloReader = Henkilo.henkiloReader
    import org.json4s.DefaultReaders.arrayReader

    Uri.fromString(appConfig.ophUrlProperties.url("oppijanumerorekisteri-service.henkilotByOids"))
      .fold(Task.fail, uri => {
        val req = Request(method = POST, uri = uri)
          .withBody[Array[String]](oids.map(_.toString).toArray)(jsonEncoderOf[Array[String]])
        client.httpClient.fetch(req) {
          case r if r.status.code == 200 => r.as[Array[Henkilo]](jsonOf[Array[Henkilo]]).map(_.toSet)
            .handleWith { case t => Task.fail(new IllegalStateException(s"Parsing henkilöt $oids failed", t)) }
          case r => Task.fail(new RuntimeException(s"Failed to get henkilöt $oids: ${r.toString()}"))
        }
      }).attemptRunFor(Duration(10, TimeUnit.SECONDS)).toEither
  }
}
