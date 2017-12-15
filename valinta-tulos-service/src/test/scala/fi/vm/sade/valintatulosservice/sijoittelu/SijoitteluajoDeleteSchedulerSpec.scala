package fi.vm.sade.valintatulosservice.sijoittelu

import fi.vm.sade.valintatulosservice.valintarekisteri.db.DeleteSijoitteluRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakuOid
import org.junit.runner.RunWith
import org.specs2.matcher.MustThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.mock.mockito.{MockitoMatchers, MockitoStubs}
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope

@RunWith(classOf[JUnitRunner])
class SijoitteluajoDeleteSchedulerSpec extends Specification with MockitoMatchers with MockitoStubs {

  trait Mocks extends Mockito with Scope with MustThrownExpectations {
    val repository = mock[DeleteSijoitteluRepository]
    val scheduler = new SijoitteluajoDeleteScheduler(repository, 23, 3)

    repository.acquireLockForSijoitteluajoCleaning(55) returns Seq(true)
    repository.clearLockForSijoitteluajoCleaning(55) returns Seq(true)
    repository.listHakuAndSijoitteluAjoCount() returns Seq((HakuOid("1"),3), (HakuOid("2"),5), (HakuOid("3"),6))
    repository.findSijoitteluAjotSkippingFirst(HakuOid("2"),3) returns Seq(1l,2l)
    repository.findSijoitteluAjotSkippingFirst(HakuOid("3"),3) returns Seq(3l,4l,5l)

    def checkConditions() = {
      there was one (repository).acquireLockForSijoitteluajoCleaning(55)
      there was one (repository).listHakuAndSijoitteluAjoCount()
      there was two (repository).findSijoitteluAjotSkippingFirst(any[HakuOid],anyInt)
      there was two (repository).deleteSijoitteluajot(any[HakuOid],any[Seq[Long]])
      there was one (repository).deleteSijoitteluajot(HakuOid("2"),Seq(1l,2l))
      there was one (repository).deleteSijoitteluajot(HakuOid("3"),Seq(3l,4l,5l))
      there was one (repository).clearLockForSijoitteluajoCleaning(55)
    }
  }

  "SijoitteluajoDeleteScheduler" should {
    "delete latest sijoitteluajot" in new Mocks {
      scheduler.task.run()
      checkConditions()
    }
    "ignore failing delete" in new Mocks {
      repository.deleteSijoitteluajot(HakuOid("2"),Seq(1l,2l)) throws(new RuntimeException("foo"))
      scheduler.task.run()
      checkConditions()
    }
  }
}
