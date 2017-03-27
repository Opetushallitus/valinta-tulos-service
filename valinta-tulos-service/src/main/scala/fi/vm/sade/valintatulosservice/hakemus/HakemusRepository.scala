package fi.vm.sade.valintatulosservice.hakemus

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.{Imports, commons}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.domain.{Hakemus, Hakutoive, Henkilotiedot}
import fi.vm.sade.valintatulosservice.hakemus.DatabaseKeys.tarjoajaIdKeyPostfix
import fi.vm.sade.valintatulosservice.mongo.MongoFactory

import scala.util.Try

object DatabaseKeys {
  val oidKey: String = "oid"
  val personOidKey: String = "personOid"
  val applicationSystemIdKey: String = "applicationSystemId"
  val hakutoiveetPath: String = "answers.hakutoiveet"
  val hakutoiveetSearchPath: String = "authorizationMeta.applicationPreferences.preferenceData.Koulutus-id"
  val henkilotiedotPath: String = "answers.henkilotiedot"
  val answersKey: String = "answers"
  val hakutoiveetKey: String = "hakutoiveet"
  val hakutoiveIdKeyPostfix: String = "Koulutus-id"
  val tarjoajaIdKeyPostfix: String = "Opetuspiste-id"
  val hakutoiveKeyPostfix: String = "Koulutus"
  val tarjoajaKeyPostfix: String = "Opetuspiste"
  val lisatiedotPath: String = "answers.lisatiedot"
  val asiointiKieliKey: String = "lisatiedot.asiointikieli"
}

@deprecated("Should be removed ASAP. Has no idea of indexes. Also has no idea of search structures")
class HakemusRepository()(implicit appConfig: VtsAppConfig) extends Logging {

  val application = MongoFactory.createCollection(appConfig.settings.hakemusMongoConfig, "application")
  val fields = MongoDBObject(
    DatabaseKeys.hakutoiveetPath -> 1,
    DatabaseKeys.henkilotiedotPath -> 1,
    DatabaseKeys.applicationSystemIdKey -> 1,
    DatabaseKeys.oidKey -> 1,
    DatabaseKeys.personOidKey -> 1,
    DatabaseKeys.lisatiedotPath -> 1
  )

  val kieliKoodit = Map(("suomi", "FI"), ("ruotsi", "SV"), ("englanti", "EN"))

  def findPersonOids(hakuOid: String): Map[String, String] = {
    application.find(
      MongoDBObject(DatabaseKeys.applicationSystemIdKey -> hakuOid),
      MongoDBObject(DatabaseKeys.oidKey -> 1, DatabaseKeys.personOidKey -> 1)
    ).map(o => o.as[String](DatabaseKeys.oidKey) -> o.getAs[String](DatabaseKeys.personOidKey).getOrElse("")).toMap
  }

  def findPersonOids(hakuOid: String, hakukohdeOid: String): Map[String, String] = {
    application.find(
      MongoDBObject(DatabaseKeys.applicationSystemIdKey -> hakuOid, DatabaseKeys.hakutoiveetSearchPath -> hakukohdeOid),
      MongoDBObject(DatabaseKeys.oidKey -> 1, DatabaseKeys.personOidKey -> 1)
    ).map(o => o.as[String](DatabaseKeys.oidKey) -> o.getAs[String](DatabaseKeys.personOidKey).getOrElse("")).toMap
  }

  def findHakemukset(hakuOid: String): Iterator[Hakemus] = {
    findHakemuksetByQuery(MongoDBObject(DatabaseKeys.applicationSystemIdKey -> hakuOid))
  }

  def findHakemus(hakemusOid: String): Either[Throwable, Hakemus] = {
    Try(findHakemuksetByQuery(MongoDBObject(DatabaseKeys.oidKey -> hakemusOid)).toStream.headOption
      .toRight(new IllegalArgumentException(s"No hakemus $hakemusOid found"))).recover {
      case e => Left(e)
    }.get
  }

  def findHakemuksetByOids(hakemusOids: Iterable[String]): Iterator[Hakemus] = {
    findHakemuksetByQuery(DatabaseKeys.oidKey $in hakemusOids)
  }

  def findHakemukset(hakuOid: String, personOid: String): Iterator[Hakemus] = {
    findHakemuksetByQuery(MongoDBObject(DatabaseKeys.personOidKey -> personOid, DatabaseKeys.applicationSystemIdKey -> hakuOid))
  }

  def findHakemuksetByHakukohde(hakuOid: String, hakukohdeOid: String): Iterator[Hakemus] = {
    findHakemuksetByQuery(MongoDBObject(DatabaseKeys.applicationSystemIdKey -> hakuOid, DatabaseKeys.hakutoiveetSearchPath -> hakukohdeOid))
  }

  def findHakemuksetByHakukohdeAndPerson(hakukohdeOid: String, personOid: String): Iterator[Hakemus] = {
    findHakemuksetByQuery(MongoDBObject(DatabaseKeys.hakutoiveetSearchPath -> hakukohdeOid, DatabaseKeys.personOidKey -> personOid))
  }

  def findHakemuksetByQuery(query: commons.Imports.DBObject): Iterator[Hakemus] = {
    val cursor = application.find(query, fields)

    for (hakemus <- cursor;
         h <- parseHakemus(hakemus)) yield h
  }

  private def parseHakemus(data: Imports.MongoDBObject): Option[Hakemus] = {
    for {
      hakemusOid <- data.getAs[String](DatabaseKeys.oidKey)
      hakuOid <- data.getAs[String](DatabaseKeys.applicationSystemIdKey)
      henkiloOid <- data.getAs[String](DatabaseKeys.personOidKey)
      answers <- data.getAs[MongoDBObject](DatabaseKeys.answersKey)
      asiointikieli = parseAsiointikieli(answers.expand[String](DatabaseKeys.asiointiKieliKey))
      hakutoiveet <- extractHakutoiveet(answers)
      henkilotiedot <- answers.getAs[MongoDBObject]("henkilotiedot")
    } yield {
      Hakemus(hakemusOid, hakuOid, henkiloOid, asiointikieli, parseHakutoiveet(hakutoiveet), parseHenkilotiedot(henkilotiedot))
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
    Henkilotiedot(emptyStringToNone(data.getAs[String]("Kutsumanimi")), emptyStringToNone(data.getAs[String]("Sähköposti")), data.getAs[String]("Henkilotunnus").isDefined)
  }

  private val HakutoiveKey = s"preference([0-9]+)-${DatabaseKeys.hakutoiveIdKeyPostfix}".r

  private def parseHakutoiveet(hakutoiveet: Imports.MongoDBObject): List[Hakutoive] = {
    hakutoiveet.toList.collect {
      case (key@HakutoiveKey(index), value: String) if value != "" => (index.toInt, key, value)
    }.sortBy(_._1).map {
      case (index, _, hakukohdeOid) =>
        Hakutoive(
          hakukohdeOid,
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
