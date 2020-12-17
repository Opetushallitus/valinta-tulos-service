package fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.logging.PerformanceLogger
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakuOid
import fi.vm.sade.valintatulosservice.valintarekisteri.{ITSetup, ValintarekisteriDbTools}
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.junit.Ignore
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterEach

@RunWith(classOf[JUnitRunner])
@Ignore
class SijoitteluPerformanceSpec
    extends Specification
    with ITSetup
    with ValintarekisteriDbTools
    with BeforeAfterEach
    with Logging
    with PerformanceLogger {
  sequential
  step(appConfig.start)
  step(deleteAll())

  lazy val valintarekisteri =
    new ValintarekisteriService(singleConnectionValintarekisteriDb, hakukohdeRecordService)

  "Store and read huge sijoittelu fast" in {
    skipped("Use this test only locally for performance tuning")
    val wrapper = time("create test data") {
      createHugeSijoittelu(12345L, HakuOid("11.22.33.44.55.66"), 50)
    }
    time("Store sijoittelu") { singleConnectionValintarekisteriDb.storeSijoittelu(wrapper) }
    //time("Get sijoittelu") { valintarekisteri.getSijoitteluajoDTO("11.22.33.44.55.66", "12345") }
    getSijoittelu("11.22.33.44.55.66")
    true must beTrue
  }
  "Reading latest huge sijoitteluajo is not timing out" in {
    skipped("Use this test only locally for performance tuning")
    val numberOfSijoitteluajot = 15
    val wrapper = time("create test data") {
      createHugeSijoittelu(12345L, HakuOid("11.22.33.44.55.66"), 40)
    }
    (12345L to (12345L + numberOfSijoitteluajot - 1)).foreach(sijoitteluajoId => {
      wrapper.sijoitteluajo.setSijoitteluajoId(sijoitteluajoId)
      wrapper.hakukohteet.foreach(_.setSijoitteluajoId(sijoitteluajoId))
      time(s"Store sijoittelu ${sijoitteluajoId}") {
        singleConnectionValintarekisteriDb.storeSijoittelu(wrapper)
      }
    })
    val (sijoitteluajo, hakukohteet) = getSijoittelu("11.22.33.44.55.66")
    compareSijoitteluWrapperToEntity(
      wrapper,
      sijoitteluajo,
      hakukohteet
    )
    true must beTrue
  }

  def getSijoittelu(hakuOid: String) = {
    val sijoitteluajo = time("Get latest sijoitteluajo") {
      valintarekisteri.getLatestSijoitteluajo(hakuOid)
    }
    val hakukohteet = time("Get hakukohteet") {
      valintarekisteri.getSijoitteluajonHakukohteet(sijoitteluajo.getSijoitteluajoId)
    }
    (sijoitteluajo, hakukohteet)
  }

  override protected def before: Unit = {
    deleteAll()
  }
  override protected def after: Unit = {
    deleteAll()
  }

  step(deleteAll())
}
