package fi.vm.sade.valintatulosservice.config

import fi.oph.viestinvalitys.{ClientBuilder, ViestinvalitysClient}
import fi.vm.sade.valintatulosservice.config.EmailerRegistry.StubbedViestinvalitys
import fi.vm.sade.valintatulosservice.vastaanottomeili.{FakeViestinvalitysClient, Mailer, MailerComponent}


trait Components extends MailerComponent with EmailerConfigComponent {
  val settings: EmailerConfig

  private def actualClient: ViestinvalitysClient = {
    ClientBuilder.viestinvalitysClientBuilder()
      .withEndpoint(settings.viestinvalitysEndpoint)
      .withUsername(settings.securitySettings.casUsername)
      .withPassword(settings.securitySettings.casPassword)
      .withCasEndpoint(settings.securitySettings.casUrl)
      .withCallerId(settings.callerId)
      .build()
  }

  private def configureViestinvalitysClient: ViestinvalitysClient = this match {
    case _: StubbedViestinvalitys => new FakeViestinvalitysClient
    case _ => actualClient
  }

  val viestinvalitysClient: ViestinvalitysClient = configureViestinvalitysClient

  override val mailer: Mailer = new MailerImpl
}
