package fi.vm.sade.valintatulosemailer

import fi.vm.sade.valintatulosemailer.config.EmailerRegistry
import fi.vm.sade.valintatulosemailer.config.EmailerRegistry.IT
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class MailerTest extends Specification {
  val registry: IT = EmailerRegistry.fromString("it").asInstanceOf[IT]

  "Mailer divides batch correctly" should {
    "divides job into 4 batches and confirms all of them" in {
      val batches = registry.mailer.sendMail
      batches.size mustEqual 4 // 3 languages and 1 extra ilmoitus over batch size
      registry.lastConfirmedAmount mustEqual registry.maxResults
    }
  }
}
