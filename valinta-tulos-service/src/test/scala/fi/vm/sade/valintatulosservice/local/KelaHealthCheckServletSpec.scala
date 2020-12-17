package fi.vm.sade.valintatulosservice.local

import java.util.UUID

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.ServletSpecification
import fi.vm.sade.valintatulosservice.security.{CasSession, Role, ServiceTicket}
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class KelaHealthCheckServletSpec extends ServletSpecification with Logging {
  "GET /health-check/kela" should {
    "Palauttaa OK responsessa, kun rajapinta toimii" in {
      val session: CasSession = CasSession(
        ServiceTicket("KelaHealthCheckServletSpecServiceTicket"),
        "1.2.246.562.24.78412307527",
        Set(Role("APP_VALINTATULOSSERVICE_KELA_READ"))
      )
      val sessionId: UUID = singleConnectionValintarekisteriDb.store(session)

      val response = get(s"health-check/kela", Seq.empty, Map("Cookie" -> s"session=$sessionId")) {
        status must_== 200
        body.isEmpty mustEqual false
        body.startsWith("OK") mustEqual true
      }

      singleConnectionValintarekisteriDb.delete(sessionId)

      response
    }
  }
}
