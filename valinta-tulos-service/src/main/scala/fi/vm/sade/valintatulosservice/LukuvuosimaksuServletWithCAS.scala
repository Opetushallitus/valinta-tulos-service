package fi.vm.sade.valintatulosservice

import java.util.Date

import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.lukuvuosimaksut.LukuvuosimaksuMuutos
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, Lukuvuosimaksu, Maksuntila}
import org.json4s.DefaultFormats
import org.json4s.ext.EnumNameSerializer
import org.scalatra.swagger.Swagger
import org.scalatra.{InternalServerError, NoContent, Ok}

import scala.util.Try

class LukuvuosimaksuServletWithCAS(lukuvuosimaksuService: LukuvuosimaksuService, val sessionRepository: SessionRepository,
                                   hakuService: HakuService,
                                   authorizer: OrganizationHierarchyAuthorizer)
                                  (implicit val swagger: Swagger, appConfig: VtsAppConfig)
  extends VtsServletBase with CasAuthenticatedServlet {

  implicit val defaultFormats = DefaultFormats + new EnumNameSerializer(Maksuntila)

  override protected def applicationDescription: String = "Lukuvuosimaksut authenticated REST API"

  protected def authenticatedPersonOid: String = {
    implicit val authenticated = authenticate
    authorize(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)
    authenticate.session.personOid
  }

  get("/:hakukohdeOid") {
    implicit val authenticated = authenticate
    val muokkaaja = authenticatedPersonOid
    val hakukohdeOid = hakukohdeOidParam

    val hakukohde = hakuService.getHakukohde(hakukohdeOid).fold(throw _, h => h)
    authorizer.checkAccessWithHakukohderyhmat(auditInfo.session._2, hakukohde.organisaatioOiditAuktorisointiin,
      Set(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD), hakukohdeOid).fold(throw _, x => x)

    val lukuvuosimaksus = lukuvuosimaksuService.getLukuvuosimaksut(hakukohdeOid, auditInfo)
    Ok(lukuvuosimaksus)
  }


  post("/:hakukohdeOid") {
    implicit val authenticated = authenticate

    val muokkaaja = authenticatedPersonOid

    val hakukohdeOid = hakukohdeOidParam

    Try(parsedBody.extract[List[LukuvuosimaksuMuutos]]).getOrElse(Nil) match {
      case lukuvuosimaksuMuutokset if lukuvuosimaksuMuutokset.nonEmpty =>
        val lukuvuosimaksut = lukuvuosimaksuMuutokset.map(m => {
          Lukuvuosimaksu(m.personOid, hakukohdeOid, m.maksuntila, muokkaaja, new Date)
        })
        lukuvuosimaksut.map(_.hakukohdeOid).foreach(hakukohdeOid => {
          val hakukohde = hakuService.getHakukohde(hakukohdeOid).fold(throw _, h => h)
          authorizer.checkAccessWithHakukohderyhmat(auditInfo.session._2, hakukohde.organisaatioOiditAuktorisointiin,
            Set(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD), hakukohdeOid).fold(throw _, x => x)
        })
        lukuvuosimaksuService.updateLukuvuosimaksut(lukuvuosimaksut, auditInfo)
        NoContent()

      case _ =>
        InternalServerError("No 'lukuvuosimaksuja' in request body!")

    }
  }

  private def hakukohdeOidParam: HakukohdeOid = {
    HakukohdeOid(Try(params("hakukohdeOid")).toOption.filter(!_.isEmpty)
      .getOrElse(throw new RuntimeException("HakukohdeOid is mandatory!")))
  }
}
