package fi.vm.sade.valintatulosservice.tcp

class ChooseFreePort extends PortChooser {
  lazy val chosenPort = PortChecker.findFreeLocalPort
}
