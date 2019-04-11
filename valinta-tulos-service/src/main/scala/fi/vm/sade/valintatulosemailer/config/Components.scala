package fi.vm.sade.valintatulosemailer.config

import fi.vm.sade.groupemailer.{GroupEmailComponent, GroupEmailService}
import fi.vm.sade.valintatulosemailer.config.EmailerRegistry.StubbedGroupEmail
import fi.vm.sade.valintatulosemailer.{Mailer, MailerComponent}


trait Components extends GroupEmailComponent with MailerComponent with EmailerConfigComponent {
  val settings: EmailerConfig

  private def configureGroupEmailService: GroupEmailService = this match {
    case _: StubbedGroupEmail => new FakeGroupEmailService
    case _ => new RemoteGroupEmailService(settings, "valinta-tulos-emailer")
  }

  override val groupEmailService: GroupEmailService = configureGroupEmailService

  override val mailer: Mailer = new MailerImpl
}
