package fi.vm.sade.valintatulosservice.config

import fi.vm.sade.groupemailer.{GroupEmailComponent, GroupEmailService}
import fi.vm.sade.valintatulosservice.config.EmailerRegistry.StubbedGroupEmail
import fi.vm.sade.valintatulosservice.vastaanottomeili.{Mailer, MailerComponent}

trait Components extends GroupEmailComponent with MailerComponent with EmailerConfigComponent {
  val settings: EmailerConfig

  private def configureGroupEmailService: GroupEmailService =
    this match {
      case _: StubbedGroupEmail => new FakeGroupEmailService
      case _                    => new RemoteGroupEmailService(settings, "valinta-tulos-emailer")
    }

  override val groupEmailService: GroupEmailService = configureGroupEmailService

  override val mailer: Mailer = new MailerImpl
}
