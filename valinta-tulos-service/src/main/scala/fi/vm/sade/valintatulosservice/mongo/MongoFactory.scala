package fi.vm.sade.valintatulosservice.mongo

import com.mongodb.client.{MongoClient, MongoClients, MongoCollection, MongoDatabase}
import com.mongodb.{BasicDBObject, ReadPreference}
import fi.vm.sade.valintatulosservice.config.MongoConfig
import org.bson.Document

import scala.collection.JavaConverters._

object MongoFactory {

  def createClient(config: MongoConfig): MongoClient = {
    MongoClients.create(config.url)
  }

  def createDB(config: MongoConfig): MongoDatabaseWrapper = {
    val client = MongoClients.create(config.url)
    new MongoDatabaseWrapper(client.getDatabase(config.dbname).withReadPreference(ReadPreference.primary()))
  }

  def createCollection(config: MongoConfig, collection: String): MongoCollectionWrapper = {
    createDB(config).getCollection(collection)
  }
}

/**
 * Wrapper around MongoDB Java driver's MongoDatabase providing Casbah-like API
 */
class MongoDatabaseWrapper(val underlying: MongoDatabase) {
  def getCollection(name: String): MongoCollectionWrapper = {
    new MongoCollectionWrapper(underlying.getCollection(name))
  }

  def apply(collectionName: String): MongoCollectionWrapper = getCollection(collectionName)
}

/**
 * Wrapper around MongoDB Java driver's MongoCollection providing Casbah-like API
 */
class MongoCollectionWrapper(val underlying: MongoCollection[Document]) {

  def find(query: Document, projection: Document): Iterator[Document] = {
    underlying.find(query).projection(projection).iterator().asScala
  }

  def find(query: Document): Iterator[Document] = {
    underlying.find(query).iterator().asScala
  }

  def remove(query: Document): Unit = {
    underlying.deleteMany(query)
  }

  def insert(doc: Document): Unit = {
    underlying.insertOne(doc)
  }

  def insertMany(docs: java.util.List[Document]): Unit = {
    underlying.insertMany(docs)
  }
}
