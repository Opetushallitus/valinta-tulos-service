package fi.vm.sade.valintatulosservice.vastaanottomeili

import java.util.Date

import com.mongodb.casbah.Imports
import com.mongodb.casbah.Imports._
import fi.vm.sade.utils.config.MongoConfig
import fi.vm.sade.valintatulosservice.json.JsonFormats._
import fi.vm.sade.valintatulosservice.mongo.MongoFactory
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid}
import org.joda.time.{DateTime, DateTimeUtils}

class ValintatulosMongoCollection(mongoConfig: MongoConfig) {
  private val valintatulos = MongoFactory.createDB(mongoConfig)("Valintatulos")

  def pollForCandidates(hakuOids: List[HakuOid], limit: Int, recheckIntervalHours: Int = (24 * 3), excludeHakemusOids: Set[HakemusOid] = Set.empty): Set[HakemusIdentifier] = {
    val query = Map(
      "hakuOid" -> Map("$in" -> hakuOids.map(_.toString)),
      "julkaistavissa" -> true,
      "mailStatus.done" -> Map("$exists" -> false),
      "$or" -> List(
        Map("mailStatus.previousCheck" -> Map("$lt" -> new DateTime().minusHours(recheckIntervalHours).toDate)),
        Map("mailStatus.previousCheck" -> Map("$exists" -> false))
      )
    )

    val candidates = valintatulos.find(query)
      .filterNot { tulos =>
      excludeHakemusOids.contains(HakemusOid(tulos.get("hakemusOid").asInstanceOf[String]))
    }
      .take(limit)
      .toList
      .map{ tulos => HakemusIdentifier(HakuOid(tulos.get("hakuOid").asInstanceOf[String]),
        HakemusOid(tulos.get("hakemusOid").asInstanceOf[String]),
        tulos.expand[java.util.Date]("mailStatus.sent"))}

    updateCheckTimestamps(candidates.map(_.hakemusOid))

    candidates.toSet
  }

  def alreadyMailed(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid): Option[java.util.Date] = {
    val result = valintatulos.findOne(
      ("hakukohdeOid" $eq hakukohdeOid.toString) ++
        ("hakemusOid" $eq hakemusOid.toString) ++
        ("mailStatus.sent" $exists(true))
    )
    result.flatMap(_.expand[java.util.Date]("mailStatus.sent"))
  }

  def addMessage(hakemus: HakemusMailStatus, hakukohde: HakukohdeMailStatus, message: String): Unit = {
    updateValintatulos(hakemus.hakemusOid, hakukohde.hakukohdeOid, Map("mailStatus.message" -> message))
  }

  def markAsSent(mailContents: LahetysKuittaus) {
    mailContents.hakukohteet.foreach { hakukohde =>
      markAsSent(mailContents.hakemusOid, hakukohde, mailContents.mediat, "Lähetetty " + formatJson(mailContents.mediat))
    }
  }

  def markAsNonMailable(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, message: String) {
    val timestamp = new Date(DateTimeUtils.currentTimeMillis)
    val fields: Map[JSFunction, Any] = Map("mailStatus.done" -> timestamp, "mailStatus.message" -> message)

    updateValintatulos(hakemusOid, hakukohdeOid, fields)
  }

  private def markAsSent(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, sentViaMedias: List[String], message: String) {
    val timestamp = new Date(DateTimeUtils.currentTimeMillis)
    val fields: Map[JSFunction, Any] = Map("mailStatus.sent" -> timestamp, "mailStatus.media" -> sentViaMedias, "mailStatus.message" -> message)

    updateValintatulos(hakemusOid, hakukohdeOid, fields)
  }

  private def updateValintatulos(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, fields: Map[Imports.JSFunction, Any]): Unit = {
    val query = MongoDBObject("hakemusOid" -> hakemusOid.toString, "hakukohdeOid" -> hakukohdeOid.toString)
    val update = Map("$set" -> fields)
    valintatulos.update(query, update, multi = true)
  }

  private def updateCheckTimestamps(hakemusOids: List[HakemusOid]) = {
    val timestamp = new DateTime().toDate

    val update = Map(
      "$set" -> Map(
        "mailStatus.previousCheck" -> timestamp
      )
    )

    val query = MongoDBObject(
      "hakemusOid" -> Map(
        "$in" -> hakemusOids.map(_.toString)
      )
    )

    valintatulos.update(query, update, multi = true)
  }
}
