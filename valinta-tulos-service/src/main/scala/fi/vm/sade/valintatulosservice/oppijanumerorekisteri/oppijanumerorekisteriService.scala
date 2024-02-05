package fi.vm.sade.valintatulosservice.oppijanumerorekisteri

import fi.vm.sade.utils.cas.{CasAuthenticatingClient, CasParams}
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakijaOid
import org.http4s.Method.POST
import org.http4s.client.blaze.SimpleHttp1Client
import org.http4s.json4s.native.{jsonEncoderOf, jsonOf}
import org.http4s.{Request, Uri}
import org.json4s.DefaultReaders.{BooleanReader, StringReader}
import org.json4s.JsonAST.{JBool, JNull, JObject, JString, JValue}
import org.json4s.{DefaultFormats, JArray, Reader, Writer}
import scalaz.concurrent.Task

import org.json4s.DefaultReaders.arrayReader
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

case class KansalaisuusKoodi(kansalaisuusKoodi: String)

case class Hetu(s: String) {
  override def toString: String = s
}

case class Henkilo(oid: HakijaOid,
                   hetu: Option[Hetu],
                   kutsumanimi: Option[String],
                   sukunimi: Option[String],
                   etunimet: Option[String],
                   kansalaisuudet: Option[List[String]],
                   syntymaaika: Option[String],
                   yksiloity: Option[Boolean] = None,
                   yksiloityVTJ: Option[Boolean] = None)

object Henkilo {
  val henkiloReader = new Reader[Henkilo] {
    implicit val formats = DefaultFormats

    override def read(value: JValue): Henkilo = {
      val kansalaisuusKoodit: List[String] = (value \ "kansalaisuus").extract[List[Option[KansalaisuusKoodi]]].map(x => x.get.kansalaisuusKoodi)

      Henkilo(
        HakijaOid(StringReader.read(value \ "oidHenkilo")),
        Option(StringReader.read(value \ "hetu")).map(Hetu),
        Option(StringReader.read(value \ "kutsumanimi")),
        Option(StringReader.read(value \ "sukunimi")),
        Option(StringReader.read(value \ "etunimet")),
        Option(kansalaisuusKoodit),
        Option(StringReader.read(value \ "syntymaaika")),
        Option(BooleanReader.read(value \ "yksiloity")),
        Option(BooleanReader.read(value \ "yksiloityVTJ"))
      )
    }
  }
  val henkiloWriter = new Writer[Henkilo] {
    override def write(h: Henkilo): JValue = {
      JObject(
        "oidHenkilo" -> JString(h.oid.toString),
        "hetu" -> h.hetu.map(s => JString(s.toString)).getOrElse(JNull),
        "kutsumanimi" -> h.kutsumanimi.map(JString).getOrElse(JNull),
        "sukunimi" -> h.sukunimi.map(JString).getOrElse(JNull),
        "etunimet" -> h.etunimet.map(JString).getOrElse(JNull),
        "kansalaisuus" -> h.kansalaisuudet.map(k => k.asInstanceOf[JArray]).getOrElse(JNull),
        "syntymaaika" -> h.syntymaaika.map(JString).getOrElse(JNull),
        "yksiloity" -> h.yksiloity.map(b => JBool(b)).getOrElse(JNull),
        "yksiloityVTJ" -> h.yksiloityVTJ.map(b => JBool(b)).getOrElse(JNull)
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
  private val client = CasAuthenticatingClient(
    casClient = appConfig.securityContext.casClient,
    casParams = params,
    serviceClient = SimpleHttp1Client(appConfig.blazeDefaultConfig),
    clientCallerId = appConfig.settings.callerId,
    sessionCookieName = "JSESSIONID"
  )

  def henkilot(oids: Set[HakijaOid]): Either[Throwable, Map[HakijaOid, Henkilo]] = {
    oids.grouped(5000).foldLeft(Task(Map.empty[HakijaOid, Henkilo])) {
      (f, chunk) => f.flatMap(m => henkilotChunk(chunk).map(m ++ _))
    }.attemptRunFor(Duration(5, TimeUnit.MINUTES)).toEither
  }

  def henkilotForHetus(hetus: Set[String]): Either[Throwable, Set[Henkilo]] = {
    hetus.grouped(5000).foldLeft(Task(Set.empty[Henkilo])) {
      (f, chunk) => f.flatMap(m => henkilotChunkHetus(chunk).map(m ++ _))
    }.attemptRunFor(Duration(5, TimeUnit.MINUTES)).toEither
  }

  private def henkilotChunk(oids: Set[HakijaOid]): Task[Map[HakijaOid, Henkilo]] = {
    import org.json4s.DefaultReaders.mapReader
    import org.json4s.DefaultWriters.{StringWriter, arrayWriter}
    implicit val hr: Reader[Henkilo] = Henkilo.henkiloReader

    Uri.fromString(appConfig.ophUrlProperties.url("oppijanumerorekisteri-service.henkilotByOids"))
      .fold(Task.fail, uri => {
        val req = Request(method = POST, uri = uri)
          .withBody[Array[String]](oids.map(_.toString).toArray)(jsonEncoderOf[Array[String]])
        client.fetch(req) {
          case r if r.status.code == 200 => r.as[Map[String, Henkilo]](jsonOf[Map[String, Henkilo]]).map(_.map { case (oid, h) => HakijaOid(oid) -> h })
            .handleWith { case t => Task.fail(new IllegalStateException(s"Parsing henkilöt $oids failed", t)) }
          case r => Task.fail(new RuntimeException(s"Failed to get henkilöt $oids: ${r.toString()}"))
        }
      })
  }

  private def henkilotChunkHetus(hetus: Set[String]): Task[Set[Henkilo]] = {
    import org.json4s.DefaultWriters.{StringWriter, arrayWriter}

    val hr: Reader[Henkilo] = Henkilo.henkiloReader
    val henkiloDecoder = jsonOf[Array[Henkilo]](arrayReader[Henkilo](manifest[Henkilo], hr))

    Uri.fromString(appConfig.ophUrlProperties.url("oppijanumerorekisteri-service.perustiedotByHetus"))
      .fold(Task.fail, uri => {
        val req = Request(method = POST, uri = uri)
          .withBody[Array[String]](hetus.toArray)(jsonEncoderOf[Array[String]])
        client.fetch(req) {
          case r if r.status.code == 200 =>
            r.as[Array[Henkilo]](henkiloDecoder).map(_.toSet)
              .handleWith { case t => Task.fail(new IllegalStateException(s"Parsing onr-result for hetus ($hetus) failed", t)) }
          case r => Task.fail(new RuntimeException(s"Failed to get henkilöt for hetus ($hetus): ${r.toString()}"))
        }
      })
  }
}
