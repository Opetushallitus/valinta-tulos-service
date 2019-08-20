package fi.vm.sade.valintatulosservice

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakukohdeOid}
import fi.vm.sade.valintatulosservice.vastaanottomeili.MailPoller
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder

class PublicEmailStatusServlet(mailPoller: MailPoller,
                               val sessionRepository: SessionRepository,
                               audit: Audit)
                              (implicit val swagger: Swagger)
  extends VtsServletBase
    with CasAuthenticatedServlet {

  protected val applicationDescription = "Julkinen vastaanottosähköpostin REST API"

  lazy val getVastaanottopostiSentForHakemus: OperationBuilder = (apiOperation[Unit]("getSentAt")
    summary "Palauttaa niiden hakemuksien oidit joille on lähetetty tai yritetty lähettää vastaanottomaili"
    parameter queryParam[String]("hakukohdeOid"))

  get("/", operation(getVastaanottopostiSentForHakemus)) {
    contentType = formats("json")
    implicit val authenticated: Authenticated = authenticate
    authorize(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)
    val hakukohdeOid: HakukohdeOid = parseHakukohdeOid.fold(throw _, x => x)
    val builder= new Target.Builder()
      .setField("hakukohdeoid", hakukohdeOid.toString)
    audit.log(auditInfo.user, VastaanottoPostitietojenLuku, builder.build(), new Changes.Builder().build())
    mailPoller.getOidsOfApplicationsWithSentOrResolvedMailStatus(hakukohdeOid)
  }

  protected def parseHakukohdeOid: Either[Throwable, HakukohdeOid] = {
    params.get("hakukohdeOid").fold[Either[Throwable, HakukohdeOid]](Left(new IllegalArgumentException("Query parametri hakukohde OID on pakollinen.")))(s => Right(HakukohdeOid(s)))
  }

  protected def parseHakemusOid: Either[Throwable, HakemusOid] = {
    params.get("hakemusOid").fold[Either[Throwable, HakemusOid]](Left(new IllegalArgumentException("URL parametri hakemus OID on pakollinen.")))(s => Right(HakemusOid(s)))
  }
}
