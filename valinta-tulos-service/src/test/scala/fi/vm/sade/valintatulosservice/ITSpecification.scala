package fi.vm.sade.valintatulosservice
import org.specs2.mutable.Specification
import org.specs2.specification.{AfterAll, Step}
import org.specs2.specification.core.Fragments

trait ITSpecification extends Specification with ITSetup with AfterAll {
  sequential

  override def map(fs: => Fragments): Fragments = {
    Step(appConfig.start) ^ super.map(fs)
  }

  override def afterAll() = {
    singleConnectionValintarekisteriDb.db.shutdown
  }
}
