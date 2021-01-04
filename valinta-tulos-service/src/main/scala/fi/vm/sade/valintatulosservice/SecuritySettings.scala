package fi.vm.sade.valintatulosservice

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config

import scala.concurrent.duration.Duration

class SecuritySettings(c: Config) {
  val casUrl = c.getString("cas.url")
  val casServiceIdentifier = c.getString("valinta-tulos-service.cas.service")
  val casUsername = c.getString("valinta-tulos-service.cas.username")
  val casPassword = c.getString("valinta-tulos-service.cas.password")
  val casKelaUsername = c.getString("valinta-tulos-service.cas.kela.username")
  val casKelaPassword = c.getString("valinta-tulos-service.cas.kela.password")
  val casValidateServiceTicketTimeout =
    Duration(
      c.getInt("valinta-tulos-service.cas.validate-service-ticket.timeout.seconds"),
      TimeUnit.SECONDS
    )
  val kelaVastaanototTestihetu = c.getString("valinta-tulos-service.kela.vastaanotot.testihetu")

  val requiredRoles = List("APP_VALINTATULOSSERVICE_CRUD")
}
