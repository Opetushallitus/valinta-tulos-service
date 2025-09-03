package fi.vm.sade.valintatulosservice.performance

import fi.vm.sade.utils.http.{DefaultHttpClient, DefaultHttpRequest}
import fi.vm.sade.valintatulosservice.SharedJetty
import fi.vm.sade.valintatulosservice.config.VtsAppConfig
import fi.vm.sade.valintatulosservice.logging.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakuOid, HakukohdeOid, Kevat, YPSHakukohde}
import org.json4s.jackson.Serialization
import org.json4s.{DefaultFormats, JValue}
import scalaj.http.Http


object YhdenPaikanSaantoBatchAPITester extends App with Logging {
  implicit val formats = DefaultFormats
  implicit val appConfig = new VtsAppConfig.IT
  private val dbConfig = appConfig.settings.valintaRekisteriDbConfig
  lazy val valintarekisteriDb: ValintarekisteriDb = new ValintarekisteriDb(
    dbConfig.copy(maxConnections = Some(1), minConnections = Some(1)))
  SharedJetty.start
  private val testDataSize = 50000

  println(s"***** Inserting $testDataSize rows of test data. This might take a while...")

  //new GeneratedFixture(new SimpleGeneratedHakuFixture2(5, testDataSize, HakuOid("1.2.246.562.5.2013080813081926341928"))).apply()

  for(i <- 1 to 5) {
    valintarekisteriDb.storeHakukohde(YPSHakukohde(HakukohdeOid(i.toString), HakuOid("1.2.246.562.5.2013080813081926341928"), Kevat(2015)))
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
