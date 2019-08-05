package fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu

import fi.vm.sade.utils.Timer
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.logging.PerformanceLogger
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.{DbConfig, ValintarekisteriDb}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.{ITSetup, ValintarekisteriDbTools}
import org.junit.Ignore
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.BeforeAfterExample

import scala.util.{Failure, Success, Try}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.SijoitteluWrapper
import org.springframework.util.StopWatch

@RunWith(classOf[JUnitRunner])
@Ignore
class StoreSijoitteluPerformanceSpec extends Specification with ITSetup with ValintarekisteriDbTools with BeforeAfterExample with Logging with PerformanceLogger {
  sequential
  step(appConfig.start)
  step(deleteAll())

  /**
    * This test can be used to measure store sijoittelu performance with real data.
    * It reads latest sijoittelu from read database (for example qa) and stores it
    * x times to embedded postgres and outputs the store sijoittelu times.
    */

  val hakuOid = HakuOid("1.2.246.562.29.59856749474") //HakuOid("1.2.246.562.29.57263577488") // HakuOid("1.2.246.562.29.10108985853")

  val dbUrl: String = "jdbc:postgresql://" + System.getProperty("db_url") + "/valintarekisteri"
  val dbUser: String = System.getProperty("db_user")
  val dbPasswd: String = System.getProperty("db_passwd")

  def readSijoitteluFromDb: SijoitteluWrapper = Try(new ValintarekisteriDb(DbConfig(
    dbUrl, Some(dbUser), Some(dbPasswd), Some(1), Some(1), Some(1), None, None, None, None), false)) match {

    case Failure(t) => throw t
    case Success(db) => try {
      val latest: Long = db.getLatestSijoitteluajoId(hakuOid).get

      val valintarekisteriQa = new Valintarekisteri(db, null, db) {}

      Timer.timed("Read sijoittelu from db", 0) {
        SijoitteluWrapper(valintarekisteriQa.getSijoitteluajo(hakuOid.s, s"$latest"),
                valintarekisteriQa.getSijoitteluajonHakukohteet(latest),
                valintarekisteriQa.getValintatulokset(hakuOid.s))
      }
    } finally {
      db.db.close()
    }
  }

  def storeSijoittelu(sijoitteluWrapper:SijoitteluWrapper, stopWatch: StopWatch, times:Int = 1): Unit = {
    (1 to times).foreach(i => {
      stopWatch.start(s"store sijoittelu $i")
      singleConnectionValintarekisteriDb.storeSijoittelu(sijoitteluWrapper)
      stopWatch.stop()
      if(i < times) startNewSijoittelu(sijoitteluWrapper)
    })
  }

  def testStoreSijoittelu(sijoitteluWrapper:SijoitteluWrapper, times:Int = 1): Unit = {
    sijoitteluWrapper.hakukohteet.foreach(h =>
      singleConnectionValintarekisteriDb.storeHakukohde(HakukohdeRecord(HakukohdeOid(h.getOid), hakuOid, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true, Kevat(2017)))
    )
    val stopWatch = new StopWatch()
    storeSijoittelu(sijoitteluWrapper, stopWatch, times)
    println(stopWatch.prettyPrint())
  }

  "Store sijoittelu (read from database)" in {
    skipped("Aja ainoastaan käsin!")
    testStoreSijoittelu(readSijoitteluFromDb, 10)
    true must_== true
  }


  def startNewSijoittelu(wrapper:SijoitteluWrapper): Unit = {
    val sijoitteluajoId = System.currentTimeMillis
    wrapper.sijoitteluajo.setStartMils(System.currentTimeMillis)
    wrapper.sijoitteluajo.setSijoitteluajoId(sijoitteluajoId)
    wrapper.hakukohteet.foreach(_.setSijoitteluajoId(sijoitteluajoId))
    wrapper.sijoitteluajo.setEndMils(System.currentTimeMillis)
  }

  override protected def before: Unit = {
    deleteAll()
  }
  override protected def after: Unit = {
    deleteAll()
  }
  step(deleteAll())

}
