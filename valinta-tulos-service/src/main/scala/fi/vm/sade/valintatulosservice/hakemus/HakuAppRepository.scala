package fi.vm.sade.valintatulosservice.hakemus

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.{Imports, commons}
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.domain.{Hakemus, Hakutoive, Henkilotiedot}
import fi.vm.sade.valintatulosservice.hakemus.DatabaseKeys.tarjoajaIdKeyPostfix
import fi.vm.sade.valintatulosservice.logging.Logging
import fi.vm.sade.valintatulosservice.mongo.MongoFactory
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid}

import scala.util.Try

object DatabaseKeys {
  val oidKey: String = "oid"
  val personOidKey: String = "personOid"
  val hakuOidKey: String = "applicationSystemId"
  val hakutoiveetSearchPath: String = "authorizationMeta.applicationPreferences.preferenceData.Koulutus-id"
  val henkilotiedotPath: String = "answers.henkilotiedot"
  val answersKey: String = "answers"
  val hakutoiveetKey: String = "hakutoiveet"
  val hakutoiveIdKeyPostfix: String = "Koulutus-id"
  val tarjoajaIdKeyPostfix: String = "Opetuspiste-id"
  val hakutoiveKeyPostfix: String = "Koulutus"
  val tarjoajaKeyPostfix: String = "Opetuspiste"
  val asiointiKieliKey: String = "answers.lisatiedot.asiointikieli"
  val state: String = "state"
}

class HakuAppRepository()(implicit appConfig: VtsAppConfig) extends Logging {

  private val application = MongoFactory.createCollection(appConfig.settings.hakemusMongoConfig, "application")

  // Yes, having the preference-stuff in the projection makes a big difference.
  private def getProjectionFields(maxApplicationOptions: Int = 30): MongoDBObject = {
    val baseFields = MongoDBObject(
      "_id" -> 0,
      DatabaseKeys.hakuOidKey -> 1,
      DatabaseKeys.oidKey -> 1,
      DatabaseKeys.personOidKey -> 1,
      DatabaseKeys.asiointiKieliKey -> 1,
      "answers.henkilotiedot.Kutsumanimi" -> 1,
      "answers.henkilotiedot.Sähköposti" -> 1,
      "answers.henkilotiedot.Henkilotunnus" -> 1
    )

    val hakutoiveet: Map[String, Int] = (1 to maxApplicationOptions).flatMap(i => {
      List(s"answers.hakutoiveet.preference$i-Opetuspiste-id" -> 1, s"answers.hakutoiveet.preference$i-Opetuspiste" -> 1,
        s"answers.hakutoiveet.preference$i-Koulutus" -> 1,s"answers.hakutoiveet.preference$i-Koulutus-id" -> 1)
    }).toMap

    baseFields.putAll(hakutoiveet)
    baseFields
  }

  val kieliKoodit = Map(("suomi", "FI"), ("ruotsi", "SV"), ("englanti", "EN"))

  def findPersonOids(hakuOid: HakuOid): Map[HakemusOid, String] = {
    application.find(
      MongoDBObject(DatabaseKeys.hakuOidKey -> hakuOid.toString),
      MongoDBObject(DatabaseKeys.oidKey -> 1, DatabaseKeys.personOidKey -> 1)
    ).map(o => HakemusOid(o.as[String](DatabaseKeys.oidKey)) -> o.getAs[String](DatabaseKeys.personOidKey).getOrElse("")).toMap
  }

  def findPersonOids(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): Map[HakemusOid, String] = {
    application.find(
      MongoDBObject(DatabaseKeys.hakuOidKey -> hakuOid.toString, DatabaseKeys.hakutoiveetSearchPath -> hakukohdeOid.toString),
      MongoDBObject(DatabaseKeys.oidKey -> 1, DatabaseKeys.personOidKey -> 1)
    ).map(o => HakemusOid(o.as[String](DatabaseKeys.oidKey)) -> o.getAs[String](DatabaseKeys.personOidKey).getOrElse("")).toMap
  }

  def findPersonOids(hakuOid: HakuOid, hakukohdeOids: List[HakukohdeOid]): Map[HakemusOid, String] = {
    application.find(
      MongoDBObject(DatabaseKeys.hakuOidKey -> hakuOid.toString) ++ (DatabaseKeys.hakutoiveetSearchPath $in hakukohdeOids.map(_.toString)),
      MongoDBObject(DatabaseKeys.oidKey -> 1, DatabaseKeys.personOidKey -> 1)
    ).map(o => HakemusOid(o.as[String](DatabaseKeys.oidKey)) -> o.getAs[String](DatabaseKeys.personOidKey).getOrElse("")).toMap
  }


  def findHakemukset(hakuOid: HakuOid): Iterator[Hakemus] = {
    findHakemuksetByQuery(MongoDBObject(DatabaseKeys.hakuOidKey -> hakuOid.toString))
  }

  def findHakemus(hakemusOid: HakemusOid): Either[Throwable, Hakemus] = {
    Try(findHakemuksetByQuery(MongoDBObject(DatabaseKeys.oidKey -> hakemusOid.toString)).toStream.headOption
      .toRight(new IllegalArgumentException(s"No hakemus $hakemusOid found"))).recover {
      case e => Left(e)
    }.get
  }

  def findHakemuksetByOids(hakemusOids: Iterable[HakemusOid]): Iterator[Hakemus] = {
    findHakemuksetByQuery(DatabaseKeys.oidKey $in hakemusOids.map(_.toString))
  }

  def findHakemuksetByHakukohde(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): Iterator[Hakemus] = {
    findHakemuksetByQuery(MongoDBObject(DatabaseKeys.hakuOidKey -> hakuOid.toString, DatabaseKeys.hakutoiveetSearchPath -> hakukohdeOid.toString))
  }

  def findHakemuksetByQuery(query: commons.Imports.DBObject): Iterator[Hakemus] = {
    val fields = getProjectionFields()
    val cursor = application.find(query, fields)

    for (hakemus <- cursor;
         h <- parseHakemus(hakemus)) yield h
  }

  private def parseHakemus(data: Imports.MongoDBObject): Option[Hakemus] = {
    for {
      hakemusOid <- data.getAs[String](DatabaseKeys.oidKey)
      hakuOid <- data.getAs[String](DatabaseKeys.hakuOidKey)
      henkiloOid <- data.getAs[String](DatabaseKeys.personOidKey)
      henkilotiedot <- data.getAs[MongoDBObject](DatabaseKeys.henkilotiedotPath)
      asiointikieli = parseAsiointikieli(data.getAs[String](DatabaseKeys.asiointiKieliKey))
      answers <- data.getAs[MongoDBObject](DatabaseKeys.answersKey)
      hakutoiveet <- extractHakutoiveet(answers)
    } yield {
      Hakemus(HakemusOid(hakemusOid), HakuOid(hakuOid), henkiloOid, asiointikieli, parseHakutoiveet(hakutoiveet), parseHenkilotiedot(henkilotiedot), Map())
    }
  }

  private def extractHakutoiveet(answers: MongoDBObject): Option[MongoDBObject] = {
    if (answers.containsField(DatabaseKeys.hakutoiveetKey)) {
      answers.getAs[MongoDBObject](DatabaseKeys.hakutoiveetKey)
    } else {
      None
    }
  }

  private def parseAsiointikieli(asiointikieli: Option[String]): String = {
    kieliKoodit.getOrElse(asiointikieli.getOrElse(""), "FI")
  }

  private def parseHenkilotiedot(data: Imports.MongoDBObject): Henkilotiedot = {
    Henkilotiedot(emptyStringToNone(data.getAs[String]("Kutsumanimi")), emptyStringToNone(data.getAs[String]("Sähköposti")), data.getAs[String]("Henkilotunnus").isDefined, List())
  }

  private val hakutoiveKey = s"preference([0-9]+)-${DatabaseKeys.hakutoiveIdKeyPostfix}".r

  private def parseHakutoiveet(hakutoiveet: Imports.MongoDBObject): List[Hakutoive] = {
    hakutoiveet.toList.collect {
      case (key@hakutoiveKey(index), value: String) if value != "" => (index.toInt, key, value)
    }.sortBy(_._1).map {
      case (index, _, hakukohdeOid) =>
        Hakutoive(
          HakukohdeOid(hakukohdeOid),
          hakutoiveet.get(s"preference$index-$tarjoajaIdKeyPostfix").map(_.asInstanceOf[String]).getOrElse(""),
          hakutoiveet.get(s"preference$index-${DatabaseKeys.hakutoiveKeyPostfix}").map(_.asInstanceOf[String]).getOrElse(""),
          hakutoiveet.get(s"preference$index-${DatabaseKeys.tarjoajaKeyPostfix}").map(_.asInstanceOf[String]).getOrElse("")
        )
    }
  }

  private def emptyStringToNone(o: Option[String]): Option[String] = o.flatMap {
    case "" => None
    case s => Some(s)
  }
}
