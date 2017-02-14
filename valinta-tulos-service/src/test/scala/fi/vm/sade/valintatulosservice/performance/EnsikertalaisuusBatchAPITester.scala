package fi.vm.sade.valintatulosservice.performance

import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigValueFactory
import fi.vm.sade.utils.http.{DefaultHttpClient, DefaultHttpRequest}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.SharedJetty
import fi.vm.sade.valintatulosservice.config.VtsAppConfig
import fi.vm.sade.valintatulosservice.ensikertalaisuus.EnsikertalaisuusServlet
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Ensikertalaisuus
import org.json4s.jackson.Serialization
import slick.driver.PostgresDriver.api._

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Random
import scalaj.http.Http

object EnsikertalaisuusBatchAPITester extends App with Logging {
  implicit val formats = EnsikertalaisuusServlet.ensikertalaisuusJsonFormats
  implicit val appConfig = new VtsAppConfig.IT
  private val dbConfig = appConfig.settings.valintaRekisteriDbConfig
  lazy val valintarekisteriDb = new ValintarekisteriDb(
    dbConfig.withValue("connectionPool", ConfigValueFactory.fromAnyRef("disabled"))).db
  SharedJetty.start
  private val testDataSize = appConfig.settings.valintaRekisteriEnsikertalaisuusMaxPersonOids
  val oids = 1.to(testDataSize).map(i => s"1.2.246.562.24.$i")

  Await.ready(valintarekisteriDb.run(
    sqlu"""insert into hakukohteet (hakukohde_oid, haku_oid, kk_tutkintoon_johtava, koulutuksen_alkamiskausi, yhden_paikan_saanto_voimassa)
             values ('1.2.246.561.20.00000000001', '1.2.246.561.29.00000000001', true, '2015K', true)"""
  ), Duration(1, TimeUnit.SECONDS))
  val henkilot = insertHenkilos()
  insertHenkiloviitteet(henkilot)


  println("...done inserting test data, let's make some requests...")
  for (_ <- 1.to(5)) {
    val fetchOids = Random.shuffle(oids.zipWithIndex.filter(t => (t._2 % 10) == 0).map(t => t._1))
    var start = System.currentTimeMillis()
    val (_, _, result) = new DefaultHttpRequest(Http(s"http://localhost:${SharedJetty.port}/valinta-tulos-service/ensikertalaisuus?koulutuksenAlkamiskausi=2014K")
      .method("POST")
      .options(DefaultHttpClient.defaultOptions)
      .header("Content-Type", "application/json")
      .postData(Serialization.write(fetchOids))).responseWithHeaders()
    println(s"request of size ${fetchOids.size} took ${System.currentTimeMillis() - start} ms")
    start = System.currentTimeMillis()
    println(s"parsing response of size ${Serialization.read[List[Ensikertalaisuus]](result).size} took ${System.currentTimeMillis() - start} ms")
  }
  println("***** Finished.")
  System.exit(0)

  private def insertHenkilos(): (List[String], List[String]) = {
    println(s"***** Inserting $testDataSize rows of test data. This might take a while...")
    Await.result(valintarekisteriDb.run(SimpleDBIO[(List[String], List[String])](jdbcActionContext => {
      val insertStatement = jdbcActionContext.connection.prepareStatement(
        """insert into vastaanotot (henkilo, hakukohde, ilmoittaja, action, selite)
         values (?, '1.2.246.561.20.00000000001', 'ilmoittaja', ?::vastaanotto_action, 'testiselite')""")
      try {
        val x = oids.foldLeft((List.empty[String], List.empty[String]))((t, oid) => {
          val writeVastaanottoEvent = Random.nextDouble() < 0.75
          val isSitova = Random.nextDouble() < 0.5
          val isEhdollinen = Random.nextDouble() < 0.9
          if (writeVastaanottoEvent) {
            insertStatement.setString(1, oid)
            insertStatement.setString(2, if (isSitova) "VastaanotaSitovasti" else if (isEhdollinen) "VastaanotaEhdollisesti" else "Peru")
            insertStatement.addBatch()
            (oid +: t._1, t._2)
          } else {
            (t._1, oid +: t._2)
          }
        })
        insertStatement.executeBatch()
        x
      } finally {
        insertStatement.close()
      }
    })), Duration(4, TimeUnit.MINUTES))
  }

  private def insertHenkiloviitteet(henkilot: (List[String], List[String])): Unit = {
    val vastaanottaneet = Random.shuffle(henkilot._1)
    val eiVastaanottaneet = Random.shuffle(henkilot._2)
    def groupHenkilot(vastaanottaneet: List[String], eiVastaanottaneet: List[String], groups: List[List[String]]): List[List[String]] = vastaanottaneet match {
      case Nil => groups
      case oid :: rest =>
        val n = Random.nextInt(4) + 1
        groupHenkilot(rest, eiVastaanottaneet.drop(n), Random.shuffle(oid +: eiVastaanottaneet.take(n)) +: groups)
    }
    Await.ready(valintarekisteriDb.run(SimpleDBIO[Unit](jdbcActionContext => {
      val insert = jdbcActionContext.connection.prepareStatement(
        "insert into henkiloviitteet (person_oid, linked_oid) values (?, ?)")
      try {
        for {
          group <- groupHenkilot(vastaanottaneet.take(testDataSize / 50), eiVastaanottaneet, List())
          personOid :: linkedOid :: Nil = group.permutations.flatMap(_.take(2)).toSeq
        } {
          insert.setString(1, personOid)
          insert.setString(2, linkedOid)
          insert.addBatch()
        }
        insert.executeBatch()
      } finally {
        insert.close()
      }
    })), Duration(4, TimeUnit.MINUTES))
  }
}
