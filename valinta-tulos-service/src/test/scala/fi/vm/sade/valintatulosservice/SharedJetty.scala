package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.config.VtsAppConfig

object SharedJetty {
   private lazy val jettyLauncher = new JettyLauncher(VtsAppConfig.vtsMockPort, Some("it"))

   def port = jettyLauncher.port

   def start {
     jettyLauncher.start
   }
 }
