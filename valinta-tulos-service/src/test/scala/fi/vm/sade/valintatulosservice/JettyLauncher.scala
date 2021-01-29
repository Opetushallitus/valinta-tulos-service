package fi.vm.sade.valintatulosservice

import fi.vm.sade.utils.tcp.PortChecker
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.webapp.WebAppContext

object JettyLauncher {
  def main(args: Array[String]) {
    System.setProperty("valintatulos.it.postgres.port", PortChecker.findFreeLocalPort.toString)
    new JettyLauncher(System.getProperty("valintatulos.port","8097").toInt).start.join
  }
}

class JettyLauncher(val port: Int, profile: Option[String] = None) {
  val server = new Server(port)
  val context = new WebAppContext()
  context.setResourceBase("src/main/webapp")
  context.setContextPath("/valinta-tulos-service")
  context.setDescriptor("src/main/webapp/WEB-INF/web.xml")
  profile.foreach (context.setAttribute("valintatulos.profile", _))
  server.setHandler(context)

  def start = {
    server.start
    server
  }


  def withJetty[T](block: => T) = {
    val server = start
    try {
      block
    } finally {
      server.stop
    }
  }
}
