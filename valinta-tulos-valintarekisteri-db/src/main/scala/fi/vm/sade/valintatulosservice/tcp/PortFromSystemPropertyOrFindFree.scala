package fi.vm.sade.valintatulosservice.tcp

class PortFromSystemPropertyOrFindFree(systemPropertyName: String) extends PortChooser {
  lazy val chosenPort: Int = System.getProperty(systemPropertyName, PortChecker.findFreeLocalPort.toString).toInt
}
