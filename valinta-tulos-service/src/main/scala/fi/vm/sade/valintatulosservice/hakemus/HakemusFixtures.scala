package fi.vm.sade.valintatulosservice.hakemus

import com.mongodb.MongoBulkWriteException
import com.mongodb.client.model.{InsertOneModel, WriteModel}
import fi.vm.sade.valintatulosservice.config.{MongoConfig, VtsApplicationSettings}
import fi.vm.sade.valintatulosservice.mongo.MongoFactory
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid}
import org.bson.Document
import org.bson.types.ObjectId

import java.util
import scala.collection.JavaConverters._

class HakemusFixtures(config: MongoConfig) {
  lazy val db = MongoFactory.createDB(config)

  if (config.url.indexOf("localhost") < 0)
    throw new IllegalArgumentException("HakemusFixtureImporter can only be used with IT profile")

  def clear = {
    db.getCollection("application").remove(new Document())
    this
  }

  def importDefaultFixtures: HakemusFixtures = {
    HakemusFixtures.defaultFixtures.foreach(importFixture(_))
    this
  }

  def importFixture(fixtureName: String): HakemusFixtures = {
    val filename = "fixtures/hakemus/" + fixtureName + ".json"
    val data: Document = MongoMockData.readJson(filename)
    insertData(data)
    this
  }

  private def insertData(data: Document): Unit = {
    for (collection <- data.keySet.asScala) {
      val collectionData = data.get(collection, classOf[util.List[Document]])
      val c = db.underlying.getCollection(collection)
      for (dataObject <- collectionData.asScala) {
        c.insertOne(dataObject)
      }
    }
  }

  private var bulkOperations: util.List[WriteModel[Document]] = _

  def startBulkOperationInsert(): Unit = {
    bulkOperations = new util.ArrayList[WriteModel[Document]]()
  }

  def importTemplateFixture(hakemus: HakemusFixture): Unit = {
    val currentTemplateObject = MongoMockData.readJson("fixtures/hakemus/hakemus-template.json")
    currentTemplateObject.put("_id", new ObjectId())
    currentTemplateObject.put("oid", hakemus.hakemusOid.toString)
    currentTemplateObject.put("applicationSystemId", hakemus.hakuOid.toString)
    currentTemplateObject.put("personOid", hakemus.hakemusOid.toString)
    val hakutoiveetDbObject = currentTemplateObject.get("answers", classOf[Document]).get("hakutoiveet", classOf[Document])
    val hakutoiveetMetaDbList = currentTemplateObject
      .get("authorizationMeta", classOf[Document])
      .get("applicationPreferences", classOf[util.List[Document]])

    hakemus.hakutoiveet.foreach { hakutoive =>
      hakutoiveetDbObject.put("preference" + hakutoive.index + "-Koulutus-id", hakutoive.hakukohdeOid.toString)
      hakutoiveetDbObject.put("preference" + hakutoive.index + "-Opetuspiste-id", hakutoive.tarjoajaOid)
      val preferenceData = new Document()
        .append("Koulutus-id", hakutoive.hakukohdeOid.toString)
        .append("Opetuspiste-id", hakutoive.tarjoajaOid)
      val metaEntry = new Document()
        .append("ordinal", hakutoive.index)
        .append("preferenceData", preferenceData)
      hakutoiveetMetaDbList.add(metaEntry)
    }
    bulkOperations.add(new InsertOneModel[Document](currentTemplateObject))
  }

  def commitBulkOperationInsert: Unit = {
    try {
      db.underlying.getCollection("application").bulkWrite(bulkOperations)
    } catch {
      case e: MongoBulkWriteException =>
        e.printStackTrace()
        for (error <- e.getWriteErrors.asScala) println(error.getMessage)
    }
  }
}

object HakemusFixtures {
  val defaultFixtures = List("00000878229", "00000441369", "00000441370", "00000441371", "00000878230", "00000878231", "00000878229-SE")

  def apply()(implicit settings: VtsApplicationSettings) = {
    new HakemusFixtures(settings.hakemusMongoConfig)
  }
}

object MongoMockData {
  import org.springframework.core.io.ClassPathResource
  import org.apache.commons.io.IOUtils

  import java.io.{IOException, StringWriter}
  import java.nio.charset.StandardCharsets

  def readJson(path: String): Document = {
    val writer = new StringWriter()
    try {
      IOUtils.copy(new ClassPathResource(path).getInputStream, writer, StandardCharsets.UTF_8)
    } catch {
      case ioe: IOException => throw new RuntimeException(ioe)
    }
    Document.parse(writer.toString)
  }
}

case class HakemusFixture(hakuOid: HakuOid, hakemusOid: HakemusOid, hakutoiveet: List[HakutoiveFixture])
case class HakutoiveFixture(index: Int, tarjoajaOid: String, hakukohdeOid: HakukohdeOid)
