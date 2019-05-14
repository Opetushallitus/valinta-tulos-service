package fi.vm.sade.valintatulosservice.vastaanottomeili

import fi.vm.sade.utils.tcp.PortChecker
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.webapp.WebAppContext

object ValintaTulosServiceWarRunner {
  val valintatulosPort: Int = sys.props.getOrElse("valintatulos.port", PortChecker.findFreeLocalPort.toString).toInt
  System.setProperty("ValintaTulosServiceWarRunner.port", valintatulosPort.toString)
}
