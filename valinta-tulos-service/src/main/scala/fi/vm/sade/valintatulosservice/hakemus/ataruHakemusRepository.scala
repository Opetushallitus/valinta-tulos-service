package fi.vm.sade.valintatulosservice.hakemus

import fi.vm.sade.properties.OphProperties
import fi.vm.sade.valintatulosservice.HttpHelper
import fi.vm.sade.valintatulosservice.domain.Henkilotiedot
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.json4s.jackson.JsonMethods._

case class AtaruHakemus(oid: HakemusOid,
                        hakuOid: HakuOid,
                        henkiloOid: HakijaOid,
                        asiointikieli: String,
                        hakukohteet: List[String],
                        henkilotiedot: Henkilotiedot)

class AtaruHakemusRepository(config: OphProperties) extends JsonFormats{
  def getHakemukset(hakuOid: HakuOid): Either[Throwable, Haku] = {
    val url = config.url("ataru-service.applications", hakuOid)
    HttpHelper.fetch(url) { response =>
      val hakemukset: List[AtaruHakemus] = (parse(response) \ "result").extract[List[AtaruHakemus]]
    }.left.map {
      case e: IllegalArgumentException => new IllegalArgumentException(s"No applications for $hakuOid found", e)
      case e: IllegalStateException => new IllegalStateException(s"Parsing applications for $hakuOid failed", e)
      case e: Exception => new RuntimeException(s"Failed to get applications for $hakuOid", e)
    }
  }
}
