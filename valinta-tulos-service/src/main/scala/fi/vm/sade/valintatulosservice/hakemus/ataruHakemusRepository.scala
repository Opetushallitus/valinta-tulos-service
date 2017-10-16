package fi.vm.sade.valintatulosservice.hakemus

import fi.vm.sade.valintatulosservice.HttpHelper
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.domain.Henkilotiedot
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.json4s.jackson.JsonMethods._
import scala.collection.JavaConverters._

case class AtaruHakemus(oid: HakemusOid,
                        hakuOid: HakuOid,
                        henkiloOid: HakijaOid,
                        asiointikieli: String,
                        hakukohteet: List[String],
                        henkilotiedot: Henkilotiedot)

class AtaruHakemusRepository(config: VtsAppConfig) extends JsonFormats {
  def getHakemukset(hakuOid: Option[HakuOid], hakukohdeOid: Option[HakukohdeOid],
                    hakemusOids: Option[List[HakemusOid]]): Either[Throwable, List[AtaruHakemus]] = {
    if (hakuOid.isEmpty && hakukohdeOid.isEmpty) throw new IllegalArgumentException("Must specify either haku or hakukohde oid")
    val params = ((if (hakuOid.isDefined) Seq("hakuOid" -> hakuOid.get) else Nil) ++
      (if (hakukohdeOid.isDefined) Seq("hakukohdeOid" -> hakukohdeOid.get) else Nil) ++
      (if (hakemusOids.isDefined) Seq("hakemusOids" -> hakemusOids.get) else Nil)).toMap.asJava
    val url = config.ophUrlProperties.url("ataru-service.applications", params)
    HttpHelper.fetch(url) { response =>
      parse(response).extract[List[AtaruHakemus]]
    }.left.map {
      case e: IllegalArgumentException => new IllegalArgumentException(s"No applications for $hakuOid found", e)
      case e: IllegalStateException => new IllegalStateException(s"Parsing applications for $hakuOid failed", e)
      case e: Exception => new RuntimeException(s"Failed to get applications for $hakuOid", e)
    }
  }
}
