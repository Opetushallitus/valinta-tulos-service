package fi.vm.sade.cas

import fi.vm.sade.valintatulosservice.config.AppConfig.LocalTestingWithTemplatedVars
import org.specs2.mutable.Specification

class CasClientIntegrationTest extends Specification {
  val appConfig = new LocalTestingWithTemplatedVars("../deploy/vars/environments/oph_vars.yml")
  val client = new CasClient("https://test-virkailija.oph.ware.fi/cas")
  val someService = appConfig.settings.config.getString("tarjonta-service.url")
  val casUsername = appConfig.settings.config.getString("valinta-tulos-service.cas.username")
  val casPassword = appConfig.settings.config.getString("valinta-tulos-service.cas.password")
  val ticketRequest: CasTicketRequest = CasTicketRequest(someService, casUsername, casPassword)

  "get service ticket from CAS" in {
    val ticket = client.getServiceTicket(ticketRequest)
    ticket.isDefined must_== true
  }

  "validate ticket" in {
    "invalid ticket" in {
      val ticket = CasTicket("lol", "asdf")
      val response = client.validateServiceTicket(ticket)
      response.success must_== false
    }
    "valid ticket" in {
      val ticket = client.getServiceTicket(ticketRequest).get
      val response = client.validateServiceTicket(CasTicket(someService, ticket))
      response.success must_== true
    }
  }
}
