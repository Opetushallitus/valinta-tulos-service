package fi.vm.sade.valintatulosemailer

import fi.vm.sade.utils.slf4j.Logging
import org.scalatra.{Ok, ScalatraServlet}


class EmailerServlet(emailerService: EmailerService) extends ScalatraServlet with Logging {

  post("/run") {
    println("EmailerServlet POST called")
    emailerService.run()
    Ok()
  }
}
