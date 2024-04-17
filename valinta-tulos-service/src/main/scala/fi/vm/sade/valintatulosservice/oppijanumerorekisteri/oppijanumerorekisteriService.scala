package fi.vm.sade.valintatulosservice.oppijanumerorekisteri

import fi.vm.sade.javautils.nio.cas.{CasClient, CasClientBuilder}
import fi.vm.sade.security.ScalaCasConfig
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakijaOid
import org.asynchttpclient.{RequestBuilder, Response}
import org.json4s.native.JsonMethods.parse
import org.json4s.native.Serialization.write
import org.json4s.JsonAST.{JBool, JNull, JObject, JString, JValue}
import org.json4s.{DefaultFormats, JArray}

import java.util.concurrent.TimeUnit
import scala.compat.java8.FutureConverters.toScala
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try

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

object Henkilo extends JsonFormats {

  def fromJson(value: JValue): Henkilo = {
    val kansalaisuusKoodit: List[String] = (value \ "kansalaisuus").extract[List[Option[KansalaisuusKoodi]]].map(x => x.get.kansalaisuusKoodi)
    Henkilo(
      HakijaOid((value \ "oidHenkilo").extract[String]),
      (value \ "hetu").extractOpt[String].map(Hetu),
      (value \ "kutsumanimi").extractOpt[String],
      (value \ "sukunimi").extractOpt[String],
      (value \ "etunimet").extractOpt[String],
      Option(kansalaisuusKoodit),
      (value \ "syntymaaika").extractOpt[String],
      Try((value \ "yksiloity").extract[Boolean]).toOption,
      Try((value \ "yksiloityVTJ").extract[Boolean]).toOption
    )
  }

  def toJson(h: Henkilo): JValue = {
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

class OppijanumerorekisteriService(appConfig: VtsAppConfig) extends JsonFormats {
  private val retryCodes = Set(new Integer(401),new Integer(302)).asJava
  private val client: CasClient =
    appConfig.securityContext.javaCasClient.getOrElse(
      CasClientBuilder.build(ScalaCasConfig(
        appConfig.settings.securitySettings.casUsername,
        appConfig.settings.securitySettings.casPassword,
        appConfig.settings.securitySettings.casUrl,
        appConfig.ophUrlProperties.url("url-oppijanumerorekisteri"),
        appConfig.settings.callerId,
        appConfig.settings.callerId,
        "/j_spring_cas_security_check",
        "JSESSIONID"
      )))

  def henkilot(oids: Set[HakijaOid]): Either[Throwable, Map[HakijaOid, Henkilo]] = {
    try {
      Right(oids.grouped(5000).foldLeft(Map.empty[HakijaOid, Henkilo]) {
        (result, chunk) => result ++ henkilotChunk(chunk)
      })
    } catch {
      case e: Throwable => Left(e)
    }
  }

  def henkilotForHetus(hetus: Set[String]): Either[Throwable, Set[Henkilo]] = {
    try {
      Right(hetus.grouped(5000).foldLeft(Set.empty[Henkilo]) {
        (result, chunk) => result ++ henkilotChunkHetus(chunk)
      })
    } catch {
      case e: Throwable => Left(e)
    }
  }

  private def henkilotChunk(oids: Set[HakijaOid]): Map[HakijaOid, Henkilo] = {
    val req = new RequestBuilder()
      .setMethod("POST")
      .setUrl(appConfig.ophUrlProperties.url("oppijanumerorekisteri-service.henkilotByOids"))
      .addHeader("Content-type", "application/json")
      .setBody(write(oids.map(_.toString).toArray))
      .build()

    val result = toScala(client.executeAndRetryWithCleanSessionOnStatusCodes(req, retryCodes)).map {
      case r if r.getStatusCode == 200 =>
        parse(r.getResponseBodyAsStream).extract[Map[String, JValue]].map { case (oid, henkiloJson) => HakijaOid(oid) -> Henkilo.fromJson(henkiloJson) }
      case r =>
        throw new RuntimeException(s"Failed to get henkilöt $oids: ${r.toString()}")
    }
    Await.result(result, Duration(1, TimeUnit.MINUTES))
  }

  private def henkilotChunkHetus(hetus: Set[String]): Set[Henkilo] = {
    val req = new RequestBuilder()
      .setMethod("POST")
      .setUrl(appConfig.ophUrlProperties.url("oppijanumerorekisteri-service.perustiedotByHetus"))
      .addHeader("Content-type", "application/json")
      .setBody(write(hetus.toArray))
      .build()

    val result = toScala(client.executeAndRetryWithCleanSessionOnStatusCodes(req, retryCodes)).map {
      case r if r.getStatusCode == 200 =>
        parse(r.getResponseBodyAsStream).children.map(Henkilo.fromJson).toSet
      case r =>
        throw new RuntimeException(s"Failed to get henkilöt for hetus ($hetus): ${r.toString()}")
    }
    Await.result(result, Duration(1, TimeUnit.MINUTES))
  }

}
