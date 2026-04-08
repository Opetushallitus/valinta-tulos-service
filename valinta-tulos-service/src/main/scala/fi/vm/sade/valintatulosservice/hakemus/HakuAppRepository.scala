package fi.vm.sade.valintatulosservice.hakemus

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.domain.{Hakemus, Hakutoive, Henkilotiedot}
import fi.vm.sade.valintatulosservice.hakemus.DatabaseKeys.tarjoajaIdKeyPostfix
import fi.vm.sade.valintatulosservice.logging.Logging
import fi.vm.sade.valintatulosservice.mongo.MongoFactory
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid}
import org.bson.Document

import scala.collection.JavaConverters._
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

  private def getProjectionFields(maxApplicationOptions: Int = 30): Document = {
    val baseFields = new Document()
      .append("_id", 0)
      .append(DatabaseKeys.hakuOidKey, 1)
      .append(DatabaseKeys.oidKey, 1)
      .append(DatabaseKeys.personOidKey, 1)
      .append(DatabaseKeys.asiointiKieliKey, 1)
      .append("answers.henkilotiedot.Kutsumanimi", 1)
      .append("answers.henkilotiedot.Sähköposti", 1)
      .append("answers.henkilotiedot.Henkilotunnus", 1)

    (1 to maxApplicationOptions).foreach { i =>
      baseFields.append(s"answers.hakutoiveet.preference$i-Opetuspiste-id", 1)
      baseFields.append(s"answers.hakutoiveet.preference$i-Opetuspiste", 1)
      baseFields.append(s"answers.hakutoiveet.preference$i-Koulutus", 1)
      baseFields.append(s"answers.hakutoiveet.preference$i-Koulutus-id", 1)
    }
    baseFields
  }

  val kieliKoodit = Map(("suomi", "FI"), ("ruotsi", "SV"), ("englanti", "EN"))

  def findPersonOids(hakuOid: HakuOid): Map[HakemusOid, String] = {
    val query = new Document(DatabaseKeys.hakuOidKey, hakuOid.toString)
    val projection = new Document(DatabaseKeys.oidKey, 1).append(DatabaseKeys.personOidKey, 1)
    application.find(query, projection).map { doc =>
      HakemusOid(doc.getString(DatabaseKeys.oidKey)) -> Option(doc.getString(DatabaseKeys.personOidKey)).getOrElse("")
    }.toMap
  }

  def findPersonOids(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): Map[HakemusOid, String] = {
    val query = new Document(DatabaseKeys.hakuOidKey, hakuOid.toString)
      .append(DatabaseKeys.hakutoiveetSearchPath, hakukohdeOid.toString)
    val projection = new Document(DatabaseKeys.oidKey, 1).append(DatabaseKeys.personOidKey, 1)
    application.find(query, projection).map { doc =>
      HakemusOid(doc.getString(DatabaseKeys.oidKey)) -> Option(doc.getString(DatabaseKeys.personOidKey)).getOrElse("")
    }.toMap
  }

  def findPersonOids(hakuOid: HakuOid, hakukohdeOids: List[HakukohdeOid]): Map[HakemusOid, String] = {
    val query = new Document(DatabaseKeys.hakuOidKey, hakuOid.toString)
      .append(DatabaseKeys.hakutoiveetSearchPath, new Document("$in", hakukohdeOids.map(_.toString).asJava))
    val projection = new Document(DatabaseKeys.oidKey, 1).append(DatabaseKeys.personOidKey, 1)
    application.find(query, projection).map { doc =>
      HakemusOid(doc.getString(DatabaseKeys.oidKey)) -> Option(doc.getString(DatabaseKeys.personOidKey)).getOrElse("")
    }.toMap
  }

  def findHakemukset(hakuOid: HakuOid): Iterator[Hakemus] = {
    findHakemuksetByQuery(new Document(DatabaseKeys.hakuOidKey, hakuOid.toString))
  }

  def findHakemus(hakemusOid: HakemusOid): Either[Throwable, Hakemus] = {
    Try(findHakemuksetByQuery(new Document(DatabaseKeys.oidKey, hakemusOid.toString)).toStream.headOption
      .toRight(new IllegalArgumentException(s"No hakemus $hakemusOid found"))).recover {
      case e => Left(e)
    }.get
  }

  def findHakemuksetByOids(hakemusOids: Iterable[HakemusOid]): Iterator[Hakemus] = {
    val query = new Document(DatabaseKeys.oidKey, new Document("$in", hakemusOids.map(_.toString).toList.asJava))
    findHakemuksetByQuery(query)
  }

  def findHakemuksetByHakukohde(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): Iterator[Hakemus] = {
    val query = new Document(DatabaseKeys.hakuOidKey, hakuOid.toString)
      .append(DatabaseKeys.hakutoiveetSearchPath, hakukohdeOid.toString)
    findHakemuksetByQuery(query)
  }

  def findHakemuksetByQuery(query: Document): Iterator[Hakemus] = {
    val fields = getProjectionFields()
    val cursor = application.find(query, fields)

    for (hakemus <- cursor;
         h <- parseHakemus(hakemus)) yield h
  }

  private def parseHakemus(data: Document): Option[Hakemus] = {
    for {
      hakemusOid <- Option(data.getString(DatabaseKeys.oidKey))
      hakuOid <- Option(data.getString(DatabaseKeys.hakuOidKey))
      henkiloOid <- Option(data.getString(DatabaseKeys.personOidKey))
      henkilotiedot <- getNestedDocument(data, DatabaseKeys.henkilotiedotPath)
      asiointikieli = parseAsiointikieli(getNestedString(data, DatabaseKeys.asiointiKieliKey))
      answers <- Option(data.get(DatabaseKeys.answersKey, classOf[Document]))
      hakutoiveet <- extractHakutoiveet(answers)
    } yield {
      Hakemus(HakemusOid(hakemusOid), HakuOid(hakuOid), henkiloOid, asiointikieli, parseHakutoiveet(hakutoiveet), parseHenkilotiedot(henkilotiedot), Map())
    }
  }

  private def getNestedDocument(doc: Document, path: String): Option[Document] = {
    val parts = path.split("\\.")
    var current: Document = doc
    for (part <- parts.dropRight(1)) {
      current = current.get(part, classOf[Document])
      if (current == null) return None
    }
    Option(current.get(parts.last, classOf[Document]))
  }

  private def getNestedString(doc: Document, path: String): Option[String] = {
    val parts = path.split("\\.")
    var current: Document = doc
    for (part <- parts.dropRight(1)) {
      current = current.get(part, classOf[Document])
      if (current == null) return None
    }
    Option(current.getString(parts.last))
  }

  private def extractHakutoiveet(answers: Document): Option[Document] = {
    Option(answers.get(DatabaseKeys.hakutoiveetKey, classOf[Document]))
  }

  private def parseAsiointikieli(asiointikieli: Option[String]): String = {
    kieliKoodit.getOrElse(asiointikieli.getOrElse(""), "FI")
  }

  private def parseHenkilotiedot(data: Document): Henkilotiedot = {
    Henkilotiedot(
      emptyStringToNone(Option(data.getString("Kutsumanimi"))),
      emptyStringToNone(Option(data.getString("Sähköposti"))),
      Option(data.getString("Henkilotunnus")).isDefined,
      List()
    )
  }

  private val hakutoiveKey = s"preference([0-9]+)-${DatabaseKeys.hakutoiveIdKeyPostfix}".r

  private def parseHakutoiveet(hakutoiveet: Document): List[Hakutoive] = {
    hakutoiveet.keySet().asScala.toList.flatMap { key =>
      key match {
        case hakutoiveKey(index) =>
          val value = hakutoiveet.getString(key)
          if (value != null && value != "") Some((index.toInt, key, value)) else None
        case _ => None
      }
    }.sortBy(_._1).map {
      case (index, _, hakukohdeOid) =>
        Hakutoive(
          HakukohdeOid(hakukohdeOid),
          Option(hakutoiveet.getString(s"preference$index-$tarjoajaIdKeyPostfix")).getOrElse(""),
          Option(hakutoiveet.getString(s"preference$index-${DatabaseKeys.hakutoiveKeyPostfix}")).getOrElse(""),
          Option(hakutoiveet.getString(s"preference$index-${DatabaseKeys.tarjoajaKeyPostfix}")).getOrElse("")
        )
    }
  }

  private def emptyStringToNone(o: Option[String]): Option[String] = o.flatMap {
    case "" => None
    case s => Some(s)
  }
}
