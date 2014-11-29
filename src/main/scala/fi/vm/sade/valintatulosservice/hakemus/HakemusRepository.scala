package fi.vm.sade.valintatulosservice.hakemus

import fi.vm.sade.valintatulosservice.Logging
import com.mongodb.casbah.Imports
import com.mongodb.casbah.Imports._
import fi.vm.sade.valintatulosservice.config.AppConfig.AppConfig
import fi.vm.sade.valintatulosservice.domain.{Henkilotiedot, Hakutoive, Hakemus}
import fi.vm.sade.valintatulosservice.mongo.MongoFactory

object DatabaseKeys {
  val oidKey: String = "oid"
  val personOidKey: String = "personOid"
  val applicationSystemIdKey: String = "applicationSystemId"
  val hakutoiveetPath: String = "answers.hakutoiveet"
  val henkilotiedotPath: String = "answers.henkilotiedot"
  val answersKey: String = "answers"
  val hakutoiveetKey: String = "hakutoiveet"
  val hakutoiveKeyPostfix: String = "Koulutus-id"
  val tarjoajaKeyPostfix: String = "Opetuspiste-id"
  val lisatiedotPath: String = "answers.lisatiedot"
  val asiointiKieliKey: String = "lisatiedot.asiointikieli"
}

class HakemusRepository()(implicit appConfig: AppConfig) extends Logging {
  val application = MongoFactory.createCollection(appConfig.settings.hakemusMongoConfig, "application")
  val fields = MongoDBObject(
    DatabaseKeys.hakutoiveetPath -> 1,
    DatabaseKeys.henkilotiedotPath -> 1,
    DatabaseKeys.oidKey -> 1,
    DatabaseKeys.personOidKey -> 1,
    DatabaseKeys.lisatiedotPath -> 1
  )

  val kieliKoodit = Map(("suomi", "FI"), ("ruotsi", "SE"), ("englanti", "EN"))

  def findHakemukset(hakuOid: String): Seq[Hakemus] = {
    val query = MongoDBObject(DatabaseKeys.applicationSystemIdKey -> hakuOid)
    val cursor = application.find(query, fields)
    (for {
      hakemus <- cursor
      h <- parseHakemus(hakemus)
    } yield { h }).toList
  }

  def findHakemus(hakemusOid: String): Option[Hakemus] = {
    val query = MongoDBObject(DatabaseKeys.oidKey -> hakemusOid)
    val res = application.findOne(query, fields)

    res.flatMap(parseHakemus(_))
  }

  def findHakemukset(hakuOid: String, personOid: String): List[Hakemus] = {
    val query = MongoDBObject(DatabaseKeys.applicationSystemIdKey -> hakuOid, DatabaseKeys.personOidKey -> personOid)
    val cursor = application.find(query, fields)
    (for {
      hakemus <- cursor
      h <- parseHakemus(hakemus)
    } yield { h }).toList
  }

  def parseHakemus(data: Imports.MongoDBObject): Option[Hakemus] = {
    for {
      hakemusOid <- data.getAs[String](DatabaseKeys.oidKey)
      henkiloOid <- data.getAs[String](DatabaseKeys.personOidKey)
      answers <- data.getAs[MongoDBObject](DatabaseKeys.answersKey)
      lisatiedot <- {
        answers.expand[String](DatabaseKeys.asiointiKieliKey)
      }
      hakutoiveet <- answers.getAs[MongoDBObject](DatabaseKeys.hakutoiveetKey)
      henkilotiedot <- answers.getAs[MongoDBObject]("henkilotiedot")
    } yield {
      Hakemus(hakemusOid, henkiloOid, parseAsiointikieli(""), parseHakutoiveet(hakutoiveet), parseHenkilotiedot(henkilotiedot))
    }
  }

  def parseAsiointikieli(asiointikieli: String): String = {
    kieliKoodit.getOrElse(asiointikieli, "FI")
  }

  def parseHenkilotiedot(data: Imports.MongoDBObject): Henkilotiedot = {
    Henkilotiedot(emptyStringToNone(data.getAs[String]("Kutsumanimi")), emptyStringToNone(data.getAs[String]("Sähköposti")))
  }

  def parseHakutoiveet(data: Imports.MongoDBObject): List[Hakutoive] = {
    data.filter { case (key, value) =>
      key.endsWith(DatabaseKeys.hakutoiveKeyPostfix) && !value.asInstanceOf[String].isEmpty
    }.toList.sortWith {
      _._1 < _._1
    }.map {
      case (hakukohdeKey, hakuKohdeOid) => {
       Hakutoive(hakuKohdeOid.asInstanceOf[String], parseTarjoajaOid(data, hakukohdeKey))
      }
    }
  }

  def parseTarjoajaOid(data: MongoDBObject, hakukohdeKey: String): String = {
    data.find {
      case (tarjoajaKey, tarjoajaOId) =>  {
        hakukohdeKey.regionMatches(0, tarjoajaKey, 0, 11) && tarjoajaKey.endsWith(DatabaseKeys.tarjoajaKeyPostfix)
      }
    }.map {
      case (_, tarjoajaOid) => tarjoajaOid.asInstanceOf[String]
    }.getOrElse("")
  }

  private def emptyStringToNone(o: Option[String]): Option[String] = o.flatMap {
    case "" => None
    case s => Some(s)
  }
}
