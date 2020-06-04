package fi.vm.sade.valintatulosservice.valintarekisteri

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterAll

trait ITSpecification extends Specification with ITSetup with BeforeAfterAll {
  sequential

  override def beforeAll: Unit = {
    appConfig.start
  }

  override def afterAll: Unit = {
    singleConnectionValintarekisteriDb.db.shutdown
  }
}
