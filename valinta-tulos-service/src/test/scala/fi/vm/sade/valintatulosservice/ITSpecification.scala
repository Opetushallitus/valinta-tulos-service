package fi.vm.sade.valintatulosservice
import org.specs2.mutable.Specification
import org.specs2.specification.Step
import org.specs2.specification.core.Fragments

trait ITSpecification extends Specification with ITSetup {
  sequential

  override def map(fs: => Fragments): Fragments = {
    Step(appConfig.start) ^ super.map(fs)
  }
}
