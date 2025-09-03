package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.logging.Logging
import org.scalatra.ScalatraServlet

class BuildInfoServlet extends ScalatraServlet with Logging {

  get("/") {
    "valinta-tulos-service"
  }

  notFound {
    // remove content type in case it was set through an action
    contentType = null
    serveStaticResource() getOrElse resourceNotFound()
  }

  error {
    case e => {
      logger.error(request.getMethod + " " + requestPath, e);
      e.printStackTrace()
      response.setStatus(500)
      "500 Internal Server Error"
    }
  }
}
