package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakukohdeOid}
import fi.vm.sade.valintatulosservice.vastaanottomeili.MailPollerAdapter
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder

class PublicEmailStatusServlet(mailPoller: MailPollerAdapter,
                               val sessionRepository: SessionRepository)
                              (implicit val swagger: Swagger)
  extends VtsServletBase
    with CasAuthenticatedServlet {

  override def applicationName = Some("auth/vastaanottoposti")
  protected val applicationDescription = "Julkinen vastaanottosähköpostin REST API"

  lazy val getVastaanottopostiSentForHakemus: OperationBuilder = (apiOperation[Unit]("getSentAt")
    summary "Palauttaa niiden hakemuksien oidit joille on lähetetty tai yritetty lähettää vastaanottomaili"
    parameter queryParam[String]("hakukohdeOid"))

  get("/", operation(getVastaanottopostiSentForHakemus)) {
    contentType = formats("json")
    implicit val authenticated: Authenticated = authenticate
    authorize(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)
    mailPoller.getOidsOfApplicationsWithSentOrResolvedMailStatus(parseHakukohdeOid.fold(throw _, x => x))
  }

  lazy val deleteVastaanottoposti: OperationBuilder = (apiOperation[Unit]("deleteMailEntry")
    summary "Poistaa hakemuksen mailin tilan uudelleenlähetystä varten"
    parameter pathParam[String]("hakemusOid"))

  delete("/:hakemusOid", operation(deleteVastaanottoposti)) {
    contentType = formats("json")
    implicit val authenticated: Authenticated = authenticate
    authorize(Role.SIJOITTELU_CRUD)
    val hakemusOid: HakemusOid = parseHakemusOid.fold(throw _, x => x)
    logger.info(s"Removing viestinnan_ohjaus entries for hakemus $hakemusOid to enable re-sending of emails.")
    mailPoller.deleteMailEntries(hakemusOid)
  }

  protected def parseHakukohdeOid: Either[Throwable, HakukohdeOid] = {
    params.get("hakukohdeOid").fold[Either[Throwable, HakukohdeOid]](Left(new IllegalArgumentException("Query parametri hakukohde OID on pakollinen.")))(s => Right(HakukohdeOid(s)))
  }

  protected def parseHakemusOid: Either[Throwable, HakemusOid] = {
    params.get("hakemusOid").fold[Either[Throwable, HakemusOid]](Left(new IllegalArgumentException("URL parametri hakemus OID on pakollinen.")))(s => Right(HakemusOid(s)))
  }
}
