package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.hakemus.HakemusFixtures
import fi.vm.sade.valintatulosservice.json.JsonFormats
import org.scalatra.test.HttpComponentsClient
import org.specs2.mutable.Specification
import org.specs2.specification.{Fragments, Step}

trait ServletSpecification extends Specification with ITSetup with TimeWarp with HttpComponentsClient {
  sequential

  def baseUrl = "http://localhost:" + SharedJetty.port + "/valinta-tulos-service"
  implicit val formats = JsonFormats.jsonFormats
  override lazy val hakemusFixtureImporter = HakemusFixtures()

  override def map(fs: => Fragments) = {
    Step(SharedJetty.start) ^ super.map(fs)
  }

  def postJSON[T](path: String, body: String, headers: Map[String, String] = Map.empty)(block: => T): T = {
    post(path, body.getBytes("UTF-8"), Map("Content-type" -> "application/json") ++ headers)(block)
  }

  def patchJSON[T](path: String, body: String, headers: Map[String, String] = Map.empty)(block: => T): T = {
    patch(path, body.getBytes("UTF-8"), Map("Content-type" -> "application/json") ++ headers)(block)
  }
}

