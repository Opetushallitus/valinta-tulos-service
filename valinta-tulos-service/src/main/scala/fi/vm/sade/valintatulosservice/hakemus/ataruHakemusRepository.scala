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

sealed trait HakemuksetQuery
case class WithHakuOid(hakuOid: HakuOid,
                       hakukohdeOid: Option[HakukohdeOid],
                       hakemusOids: Option[List[HakemusOid]]) extends HakemuksetQuery
case class WithHakemusOids(hakuOid: Option[HakuOid],
                           hakukohdeOid: Option[HakukohdeOid],
                           hakemusOids: List[HakemusOid]) extends HakemuksetQuery

class AtaruHakemusRepository(config: VtsAppConfig) extends JsonFormats {
  def getHakemukset(query: HakemuksetQuery): Either[Throwable, List[AtaruHakemus]] = {
    val params = query match {
      case WithHakuOid(hakuOid, hakukohdeOid, hakemusOids) =>
        (Option("hakuOid" -> hakuOid.toString) ++
          hakukohdeOid.map("hakukohdeOid" -> _.toString) ++
          hakemusOids.map("hakemusOids" -> _.map(_.toString))).toMap
      case WithHakemusOids(hakuOid, hakukohdeOid, hakemusOids) =>
        (hakuOid.map("hakuOid" -> _.toString) ++
          hakukohdeOid.map("hakukohdeOid" -> _.toString) ++
          Option("hakemusOids" -> hakemusOids.map(_.toString))).toMap
    }
    val url = config.ophUrlProperties.url("ataru-service.applications", params.asJava)
    HttpHelper.fetch(url) { response =>
      parse(response).extract[List[AtaruHakemus]]
    }.left.map {
      case e: IllegalArgumentException => new IllegalArgumentException(s"No applications for $query found", e)
      case e: IllegalStateException => new IllegalStateException(s"Parsing applications for $query failed", e)
      case e: Exception => new RuntimeException(s"Failed to get applications for $query", e)
    }
  }
}
