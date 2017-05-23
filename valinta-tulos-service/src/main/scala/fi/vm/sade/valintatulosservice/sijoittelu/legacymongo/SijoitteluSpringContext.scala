package fi.vm.sade.valintatulosservice.sijoittelu.legacymongo

import com.mongodb._
import fi.vm.sade.sijoittelu.domain.Valintatulos
import fi.vm.sade.sijoittelu.tulos.dao.{HakukohdeDao, SijoitteluDao, ValintatulosDao}
import fi.vm.sade.sijoittelu.tulos.service.RaportointiService
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.sijoittelu
import fi.vm.sade.valintatulosservice.sijoittelu.{ValintarekisteriValintatulosDao, ValintarekisteriValintatulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, ValintatapajonoOid}
import org.mongodb.morphia.{Datastore, Morphia}
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation._
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import org.springframework.core.env.{MapPropertySource, MutablePropertySources}

import scala.collection.JavaConversions._

trait SijoitteluContext {

  val morphiaDs:Datastore
  val valintatulosDao:ValintarekisteriValintatulosDao
  val hakukohdeDao:HakukohdeDao
  val sijoitteluDao:SijoitteluDao
  val raportointiService:sijoittelu.ValintarekisteriRaportointiService
  val valintatulosRepository:ValintarekisteriValintatulosRepository
}

class SijoitteluSpringContext(config: VtsAppConfig, context: ApplicationContext) extends SijoitteluContext {
  override lazy val morphiaDs = context.getBean(classOf[Datastore])
  override lazy val valintatulosDao = new MongoValintatulosDao(context.getBean(classOf[ValintatulosDao]))
  override lazy val hakukohdeDao = context.getBean(classOf[HakukohdeDao])
  override lazy val sijoitteluDao = context.getBean(classOf[SijoitteluDao])
  override lazy val raportointiService = new MongoRaportointiService(context.getBean(classOf[RaportointiService]))
  override lazy val valintatulosRepository = new MongoValintatulosRepository(valintatulosDao)
}

object SijoitteluSpringContext {
  def check() {}

  def createApplicationContext(configuration: VtsAppConfig): AnnotationConfigApplicationContext = {
    val appContext: AnnotationConfigApplicationContext = new AnnotationConfigApplicationContext
    val springConfiguration = new Default
    println("Using spring configuration " + springConfiguration)
    appContext.getEnvironment.setActiveProfiles(springConfiguration.profile)
    customPropertiesHack(appContext, configuration)
    appContext.register(springConfiguration.getClass)
    appContext.refresh()
    appContext
  }

  private def customPropertiesHack(appContext: AnnotationConfigApplicationContext, configuration: VtsAppConfig) {
    val configurer: PropertySourcesPlaceholderConfigurer = new PropertySourcesPlaceholderConfigurer()
    val sources: MutablePropertySources = new MutablePropertySources()

    val properties: Map[String, String] = configuration.properties

    sources.addFirst(new MapPropertySource("custom props", mapAsJavaMap(properties)))
    configurer.setPropertySources(sources)
    appContext.addBeanFactoryPostProcessor(configurer)
  }

  @Configuration
  @ComponentScan(basePackages = Array(
    "fi.vm.sade.sijoittelu.tulos.service",
    "fi.vm.sade.sijoittelu.tulos.dao",
    "fi.vm.sade.valintatulosservice.sijoittelu.spring"))
  @Import(Array(classOf[SijoitteluMongoConfiguration]))
  class Default extends SijoitteluSpringConfiguration {
    val profile = "default"
  }
}

trait SijoitteluSpringConfiguration {
  def profile: String // <- should be able to get from annotation
}

@Configuration
class SijoitteluMongoConfiguration {
  @Bean
  def datastore(@Value("${sijoittelu-service.mongodb.dbname}") dbName: String, @Value("${sijoittelu-service.mongodb.uri}") dbUri: String): Datastore = {
    val mongoClient: MongoClient = createMongoClient(dbUri)
    new Morphia().createDatastore(mongoClient, dbName)
  }

  @Bean
  def database(@Value("${sijoittelu-service.mongodb.dbname}") dbName: String, @Value("${sijoittelu-service.mongodb.uri}") dbUri: String): DB = {
    createMongoClient(dbUri).getDB(dbName)
  }

  private def createMongoClient(dbUri: String): MongoClient = {
    val options = new MongoClientOptions.Builder().writeConcern(WriteConcern.FSYNCED)
    val mongoClientURI: MongoClientURI = new MongoClientURI(dbUri, options)
    val mongoClient: MongoClient = new MongoClient(mongoClientURI)
    mongoClient
  }
}
