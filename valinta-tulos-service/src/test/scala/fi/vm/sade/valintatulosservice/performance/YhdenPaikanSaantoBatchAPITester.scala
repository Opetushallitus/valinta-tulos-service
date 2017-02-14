package fi.vm.sade.valintatulosservice.performance

import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigValueFactory
import fi.vm.sade.utils.http.{DefaultHttpClient, DefaultHttpRequest}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.SharedJetty
import fi.vm.sade.valintatulosservice.config.VtsAppConfig
import fi.vm.sade.valintatulosservice.generatedfixtures.{GeneratedFixture, SimpleGeneratedHakuFixture2}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import org.json4s.jackson.Serialization
import org.json4s.{DefaultFormats, JValue}
import slick.driver.PostgresDriver.api._

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scalaj.http.Http


object YhdenPaikanSaantoBatchAPITester extends App with Logging {
  implicit val formats = DefaultFormats
  implicit val appConfig = new VtsAppConfig.IT
  private val dbConfig = appConfig.settings.valintaRekisteriDbConfig
  lazy val valintarekisteriDb = new ValintarekisteriDb(
    dbConfig.withValue("connectionPool", ConfigValueFactory.fromAnyRef("disabled"))).db
  SharedJetty.start
  private val testDataSize = 50000

  println(s"***** Inserting $testDataSize rows of test data. This might take a while...")

  new GeneratedFixture(new SimpleGeneratedHakuFixture2(5, testDataSize, "1.2.246.562.5.2013080813081926341928")).apply

  for(i <- 1 to 5) {
    Await.ready(valintarekisteriDb.run(
      sqlu"""insert into hakukohteet (hakukohde_oid, haku_oid, kk_tutkintoon_johtava, koulutuksen_alkamiskausi, yhden_paikan_saanto_voimassa)
             values (${i.toString}, '1.2.246.562.5.2013080813081926341928', true, '2015K', true)"""
    ), Duration(1, TimeUnit.SECONDS))

  }
  println("...done inserting test data, let's make some requests...")
  var start = System.currentTimeMillis()
  val (status, _, result) = new DefaultHttpRequest(Http(s"http://localhost:${SharedJetty.port}/valinta-tulos-service/virkailija/valintatulos/haku/1.2.246.562.5.2013080813081926341928")
    .method("GET")
    .options(DefaultHttpClient.defaultOptions)
    .header("Content-Type", "application/json")).responseWithHeaders()
  println(s"request took ${System.currentTimeMillis() - start} ms")
  start = System.currentTimeMillis()
  println(s"parsing response of size ${Serialization.read[List[Map[String, JValue]]](result).size} took ${System.currentTimeMillis() - start} ms")
  println("***** Finished.")
  System.exit(0)
}
