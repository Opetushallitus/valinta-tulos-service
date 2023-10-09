package fi.vm.sade.valintatulosservice.hakemus

import com.mongodb._
import fi.vm.sade.utils.config.MongoConfig
import fi.vm.sade.valintatulosservice.config.VtsApplicationSettings
import fi.vm.sade.valintatulosservice.mongo.MongoFactory
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid}
import org.bson.types.ObjectId

class HakemusFixtures(config: MongoConfig) {
  lazy val db = MongoFactory.createDB(config)

  if (config.url.indexOf("localhost") < 0)
    throw new IllegalArgumentException("HakemusFixtureImporter can only be used with IT profile")

  def clear = {
    db.getCollection("application").remove(new BasicDBObject())
    this
  }

  def importDefaultFixtures: HakemusFixtures = {
    HakemusFixtures.defaultFixtures.foreach(importFixture(_))
    this
  }

  def importFixture(fixtureName: String): HakemusFixtures = {
    val filename = "fixtures/hakemus/" + fixtureName + ".json"
    val data: DBObject = MongoMockData.readJson(filename)
    insertData(db.underlying, data)
    this
  }

  private def insertData(db: DB, data: DBObject) {
    import scala.collection.JavaConversions._
    for (collection <- data.keySet) {
      val collectionData: BasicDBList = data.get(collection).asInstanceOf[BasicDBList]
      val c: DBCollection = db.getCollection(collection)
      import scala.collection.JavaConversions._
      for (dataObject <- collectionData) {
        val dbObject: DBObject = dataObject.asInstanceOf[DBObject]
        val id: AnyRef = dbObject.get("_id")
        c.insert(dbObject)
      }
    }
  }

  private var builder:BulkWriteOperation = null

  def startBulkOperationInsert() = {
    builder = db.underlying.getCollection("application").initializeUnorderedBulkOperation
  }

  def importTemplateFixture(hakemus: HakemusFixture) = {
    val currentTemplateObject = MongoMockData.readJson("fixtures/hakemus/hakemus-template.json").asInstanceOf[BasicDBObject]
    currentTemplateObject.put("_id", new ObjectId())
    currentTemplateObject.put("oid", hakemus.hakemusOid.toString)
    currentTemplateObject.put("applicationSystemId", hakemus.hakuOid.toString)
    currentTemplateObject.put("personOid", hakemus.hakemusOid.toString)
    val hakutoiveetDbObject = currentTemplateObject.get("answers").asInstanceOf[BasicDBObject].get("hakutoiveet").asInstanceOf[BasicDBObject]
    val hakutoiveetMetaDbList = currentTemplateObject
      .get("authorizationMeta").asInstanceOf[BasicDBObject]
      .get("applicationPreferences").asInstanceOf[BasicDBList]

    hakemus.hakutoiveet.foreach { hakutoive =>
      hakutoiveetDbObject.put("preference" + hakutoive.index + "-Koulutus-id", hakutoive.hakukohdeOid.toString)
      hakutoiveetDbObject.put("preference" + hakutoive.index + "-Opetuspiste-id", hakutoive.tarjoajaOid)
      hakutoiveetMetaDbList.add(BasicDBObjectBuilder.start()
        .add("ordinal", hakutoive.index)
        .push("preferenceData")
        .add("Koulutus-id", hakutoive.hakukohdeOid.toString)
        .add("Opetuspiste-id", hakutoive.tarjoajaOid)
        .pop()
        .get())
    }
    builder.insert(currentTemplateObject)
  }

  def commitBulkOperationInsert = {
    import scala.collection.JavaConverters._
    try {
      builder.execute(WriteConcern.UNACKNOWLEDGED)
    } catch {
      case e:BulkWriteException => {
        e.printStackTrace()
        for(error <- e.getWriteErrors.asScala) println(error.getMessage)
      }
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
  import java.io.StringWriter
  import java.io.IOException
  import com.mongodb.util.JSON

  def readJson(path:String):DBObject = {
    val writer = new StringWriter()
    try {
      IOUtils.copy(new ClassPathResource(path).getInputStream, writer)
    } catch {
      case ioe:IOException => throw new RuntimeException(ioe)
    }
    JSON.parse(writer.toString()).asInstanceOf[DBObject]
  }
}

case class HakemusFixture(hakuOid: HakuOid, hakemusOid: HakemusOid, hakutoiveet: List[HakutoiveFixture])
case class HakutoiveFixture(index: Int, tarjoajaOid: String, hakukohdeOid: HakukohdeOid)
