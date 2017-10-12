package fi.vm.sade.valintatulosservice.hakemus

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.properties.OphProperties
import fi.vm.sade.utils.http.DefaultHttpClient
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.MonadHelper
import fi.vm.sade.valintatulosservice.domain.Henkilotiedot
import fi.vm.sade.valintatulosservice.memoize.TTLOptionalMemoize
import fi.vm.sade.valintatulosservice.tarjonta.{Haku, HakuTarjonnassa}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.joda.time.DateTime
import org.json4s.JsonAST.{JInt, JObject, JString}
import org.json4s.jackson.JsonMethods._
import org.json4s.{CustomSerializer, Formats, JValue, MappingException}

import scala.collection.JavaConversions._
import scala.util.Try
import scala.util.control.NonFatal
import scalaj.http.HttpOptions

import fi.vm.sade.valintatulosservice.valintarekisteri.HttpHelper

trait AtaruHakemusRepository extends HttpHelper {
  def getHakemukset(hakuOid: HakuOid)
}

case class AtaruHakemus(oid: HakemusOid,
                        hakuOid: HakuOid,
                        henkiloOid: HakijaOid,
                        asiointikieli: String,
                        hakukohteet: List[String],
                        henkilotiedot: Henkilotiedot)

class RemoteAtaruHakemusRepository(config: OphProperties) extends AtaruHakemusRepository {
  def getHakemukset(hakuOid: HakuOid): Either[Throwable, Haku] = {
    val url = config.ophProperties.url("ataru-service.applications", hakuOid)
    fetch(url) { response =>
      val hakemukset: List[AtaruHakemus] = (parse(response) \ "result").extract[List[AtaruHakemus]]
    }.left.map {
      case e: IllegalArgumentException => new IllegalArgumentException(s"No applications for $hakuOid found", e)
      case e: IllegalStateException => new IllegalStateException(s"Parsing applications for $hakuOid failed", e)
      case e: Exception => new RuntimeException(s"Failed to get applications for $hakuOid", e)
    }
  }
}
