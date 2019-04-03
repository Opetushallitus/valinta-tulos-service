package fi.vm.sade.valintatulosemailer

import fi.vm.sade.utils.tcp.PortChecker
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.webapp.WebAppContext

object ValintaTulosServiceWarRunner {
  val valintatulosPort: Int = sys.props.getOrElse("valintatulos.port", PortChecker.findFreeLocalPort.toString).toInt
  System.setProperty("ValintaTulosServiceWarRunner.port", valintatulosPort.toString)
}

class ValintaTulosServiceWarRunner(profile: Option[String] = None) {
  val valintatulosservice: WebAppContext = {
    System.setProperty("valintatulos.profile", "it")
    val context = new WebAppContext()
    context.setContextPath("/valinta-tulos-service")
    context.setWar("target/valinta-tulos-service.war")
    context
  }

  val server = new Server(ValintaTulosServiceWarRunner.valintatulosPort)
  server.setHandler(valintatulosservice)

  def start(): Server = {
    sys.addShutdownHook {
      stop()
    }
    server.start()
    server
  }

  def stop(): Unit = server.stop()
}
