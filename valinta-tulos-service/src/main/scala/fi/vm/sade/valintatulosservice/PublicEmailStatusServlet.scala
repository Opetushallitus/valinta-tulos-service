package fi.vm.sade.valintatulosservice

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakukohdeOid}
import fi.vm.sade.valintatulosservice.vastaanottomeili.MailPoller
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder

class PublicEmailStatusServlet(
  mailPoller: MailPoller,
  val sessionRepository: SessionRepository,
  audit: Audit
)(implicit val swagger: Swagger)
    extends VtsServletBase
    with CasAuthenticatedServlet {

  protected val applicationDescription = "Julkinen vastaanottosähköpostin REST API"

  lazy val getVastaanottopostiSentForHakemus: OperationBuilder = (apiOperation[Unit]("getSentAt")
    summary "Palauttaa niiden hakemuksien oidit joille on lähetetty tai yritetty lähettää vastaanottomaili"
    parameter queryParam[String]("hakukohdeOid")
    tags "vastaanottoposti")

  get("/", operation(getVastaanottopostiSentForHakemus)) {
    contentType = formats("json")
    implicit val authenticated: Authenticated = authenticate
    authorize(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)
    val hakukohdeOid: HakukohdeOid = parseHakukohdeOid.fold(throw _, x => x)
    val builder = new Target.Builder()
      .setField("hakukohdeoid", hakukohdeOid.toString)
    audit.log(
      auditInfo.user,
      VastaanottoPostitietojenLuku,
      builder.build(),
      new Changes.Builder().build()
    )
    mailPoller.getOidsOfApplicationsWithSentOrResolvedMailStatus(hakukohdeOid)
  }
}
