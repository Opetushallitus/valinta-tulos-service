package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.sql.Timestamp
import java.text.SimpleDateFormat

import fi.vm.sade.sijoittelu.domain.SijoitteluAjo
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.{ITSetup, ValintarekisteriDbTools}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import jdk.nashorn.internal.ir.annotations.Ignore
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.BeforeAfterExample
import fi.vm.sade.valintatulosservice.logging.PerformanceLogger
import slick.driver.PostgresDriver.api.actionBasedSQLInterpolation
import slick.jdbc.GetResult

@RunWith(classOf[JUnitRunner])
class ValintarekisteriDbSaveSijoitteluSpec extends Specification with ITSetup with ValintarekisteriDbTools with BeforeAfterExample
  with Logging with PerformanceLogger {
  sequential
  private val hakuOid = "1.2.246.561.29.00000000001"

  val now = System.currentTimeMillis
  val nowDatetime = new Timestamp(1)

  step(appConfig.start)
  step(deleteAll())

  "ValintarekisteriDb" should {
    "store sijoitteluajoWrapper fixture" in {
      val wrapper = loadSijoitteluFromFixture("hyvaksytty-korkeakoulu-erillishaku")
      singleConnectionValintarekisteriDb.storeSijoittelu(wrapper)
      assertSijoittelu(wrapper)
    }
    "store sijoitteluajoWrapper fixture with hakijaryhmä and pistetiedot" in {
      val wrapper = loadSijoitteluFromFixture("valintatapajono_hakijaryhma_pistetiedot")
      singleConnectionValintarekisteriDb.storeSijoittelu(wrapper)
      assertSijoittelu(wrapper)
    }
    "store tilan_viimeisin_muutos correctly" in {
      val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

      def assertTilanViimeisinMuutos(hakemusOid:String, expected:java.util.Date) = {
        val muutokset = findTilanViimeisinMuutos(hakemusOid)
        muutokset.size mustEqual 1
        logger.info(s"Got date:${dateFormat.format(new java.util.Date(muutokset.head.getTime))}")
        logger.info(s"Expecting date:${dateFormat.format(expected)}")
        muutokset.head.getTime mustEqual expected.getTime
      }

      val wrapper = loadSijoitteluFromFixture("haku-1.2.246.562.29.75203638285", "QA-import/")
      singleConnectionValintarekisteriDb.storeSijoittelu(wrapper)
      assertSijoittelu(wrapper)
      assertTilanViimeisinMuutos("1.2.246.562.11.00006465296", dateFormat.parse("2016-10-17T09:08:11.967+0000"))
      assertTilanViimeisinMuutos("1.2.246.562.11.00004685599", dateFormat.parse("2016-10-12T04:11:20.526+0000"))
    }
  }
  implicit val resultAsObjectMap = GetResult[Map[String,Object]] (
    prs => (1 to prs.numColumns).map(_ => prs.rs.getMetaData.getColumnName(prs.currentPos+1) -> prs.nextString ).toMap )
  val hakemusOid = "1.2.246.562.11.00006926939"
  def readTable(table:String) = singleConnectionValintarekisteriDb.runBlocking(
    sql"""select * from #${table} where hakemus_oid = ${hakemusOid}""".as[Map[String,Object]])

  "Valintarekisteri" should {
    "store valinnantulos history correctly" in {
      val wrapper = loadSijoitteluFromFixture("haku-1.2.246.562.29.75203638285", "QA-import/")
      singleConnectionValintarekisteriDb.storeSijoittelu(wrapper)

      readTable("valinnantulokset_history").size mustEqual 0

      val original = readTable("valinnantulokset").head
      singleConnectionValintarekisteriDb.runBlocking(
        sqlu"""update valinnantulokset
               set julkaistavissa = true
               where hakemus_oid = ${hakemusOid}""")

      val updated = readTable("valinnantulokset").head
      val history = readTable("valinnantulokset_history").head

      history.filterKeys(_ != "system_time") mustEqual original.filterKeys(_ != "system_time")

      history("system_time").asInstanceOf[String] mustEqual
        original("system_time").asInstanceOf[String].replace(")", "") +
        updated("system_time").asInstanceOf[String].replace("[", "").replace(",", "")
    }
    "store valinnantila history correctly" in {
      val wrapper = loadSijoitteluFromFixture("haku-1.2.246.562.29.75203638285", "QA-import/")
      singleConnectionValintarekisteriDb.storeSijoittelu(wrapper)

      readTable("valinnantilat_history").size mustEqual 0

      val original = readTable("valinnantilat").head
      singleConnectionValintarekisteriDb.runBlocking(
        sqlu"""update valinnantilat
               set tila = 'Hylatty'
               where hakemus_oid = ${hakemusOid}""")

      val updated = readTable("valinnantilat").head
      val history = readTable("valinnantilat_history").head

      history.filterKeys(_ != "system_time") mustEqual original.filterKeys(_ != "system_time")

      history("system_time").asInstanceOf[String] mustEqual
        original("system_time").asInstanceOf[String].replace(")", "") +
          updated("system_time").asInstanceOf[String].replace("[", "").replace(",", "")
    }
  }

  override protected def before: Unit = {
    deleteAll()
  }
  override protected def after: Unit = {
    deleteAll()
  }

  step(deleteAll())
}
