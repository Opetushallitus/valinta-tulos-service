package fi.vm.sade.valintatulosservice

import java.util.Date
import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.lukuvuosimaksut.LukuvuosimaksuMuutos
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, Lukuvuosimaksu, Maksuntila}
import org.json4s.Formats
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.{InternalServerError, NoContent, Ok}

import scala.util.Try

// This is an ugly solution to the problem that Scalatra does not support enums in Swagger
case class LukuvuosimaksuForSwagger(personOid: String, hakukohdeOid: String, maksuntila: String, muokkaaja: String, luotu: Date)

class LukuvuosimaksuServletWithCAS(lukuvuosimaksuService: LukuvuosimaksuService, val sessionRepository: SessionRepository,
                                   hakuService: HakuService,
                                   authorizer: OrganizationHierarchyAuthorizer)
                                  (implicit val swagger: Swagger, appConfig: VtsAppConfig)
  extends VtsServletBase with CasAuthenticatedServlet with AuditInfoParameter {

  implicit val defaultFormats: Formats = JsonFormats.jsonFormats + new Scala213EnumNameSerializer(Maksuntila)

  override protected def applicationDescription: String = "Lukuvuosimaksujen rajapinnat (CAS-autentikoitu)"

  protected def authenticatedPersonOid(implicit authenticated: Authenticated): String = {
    authorize(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)
    authenticated.session.personOid
  }

  val lukuvuosimaksutBulkReadSwagger: OperationBuilder = (apiOperation[List[LukuvuosimaksuForSwagger]]("HakukohteenLukuvuosimaksutietojenTallennus")
    summary "Hakukohteen lukuvuosimaksutietojen hakeminen useille hakukohteille, annetulla audit-infolla"
    parameter bodyParam[LukuvuosimaksuBulkReadRequest].required
    tags "lukuvuosimaksu")
  post("/read/bulk", operation(lukuvuosimaksutBulkReadSwagger)) {
    implicit val authenticated: Authenticated = authenticate
    authorize(Role.VALINTATULOSSERVICE_CRUD_OPH)

    val maksuRequest: LukuvuosimaksuBulkReadRequest = parsedBody.extract[LukuvuosimaksuBulkReadRequest]
    Ok(lukuvuosimaksuService.getLukuvuosimaksut(maksuRequest.hakukohdeOids.toSet, getAuditInfo(maksuRequest)))
  }

  val lukuvuosimaksutWriteSwagger: OperationBuilder = (apiOperation[List[LukuvuosimaksuForSwagger]]("HakukohteenLukuvuosimaksutietojenTallennus")
    summary "Hakukohteen lukuvuosimaksutietojen tallennus hakukohteille anne"
    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen OID")
    parameter bodyParam[LukuvuosimaksuBulkReadRequest].required
    tags "lukuvuosimaksu")
  post("/write/:hakukohdeOid", operation(lukuvuosimaksutWriteSwagger)) {
    implicit val authenticated: Authenticated = authenticate
    authorize(Role.VALINTATULOSSERVICE_CRUD_OPH)

    val hakukohdeOid = hakukohdeOidParam

    val lukuvuosimaksuRequest = parsedBody.extract[LukuvuosimaksuRequest]

    val auditInfo = getAuditInfo(lukuvuosimaksuRequest)

    lukuvuosimaksuRequest.lukuvuosimaksuMuutokset match {
      case lukuvuosimaksuMuutokset if lukuvuosimaksuMuutokset.nonEmpty =>
        val lukuvuosimaksut = lukuvuosimaksuMuutokset.map(m => {
          Lukuvuosimaksu(m.personOid, hakukohdeOid, m.maksuntila, auditInfo.session._2.personOid, new Date)
        })
        lukuvuosimaksuService.updateLukuvuosimaksut(lukuvuosimaksut, auditInfo)

        NoContent()
      case _ =>
        InternalServerError("No 'lukuvuosimaksuja' in request body!")
    }
  }

  val lukuvuosimaksutHakukohteelleSwagger: OperationBuilder = (apiOperation[List[LukuvuosimaksuForSwagger]]("HakukohteenLukuvuosimaksutietojenHakeminen")
    summary "Hakukohteen lukuvuosimaksutietojen hakeminen"
    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen OID")
    tags "lukuvuosimaksu")
  get("/:hakukohdeOid", operation(lukuvuosimaksutHakukohteelleSwagger)) {
    implicit val authenticated: Authenticated = authenticate
    val muokkaaja = authenticatedPersonOid
    val hakukohdeOid = hakukohdeOidParam

    val hakukohde = hakuService.getHakukohde(hakukohdeOid).fold(throw _, h => h)
    authorizer.checkAccessWithHakukohderyhmat(auditInfo.session._2, hakukohde.organisaatioOiditAuktorisointiin,
      Set(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD), hakukohdeOid).fold(throw _, x => x)

    val lukuvuosimaksus: Seq[Lukuvuosimaksu] = lukuvuosimaksuService.getLukuvuosimaksut(hakukohdeOid, auditInfo)
    Ok(lukuvuosimaksus)
  }

  val lukuvuosimaksutHakukohteelleTallennusSwagger: OperationBuilder = (apiOperation[List[LukuvuosimaksuForSwagger]]("HakukohteenLukuvuosimaksutietojenTallennus")
    summary "Hakukohteen lukuvuosimaksutietojen tallennus"
    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen OID")
    tags "lukuvuosimaksu")
  post("/:hakukohdeOid", operation(lukuvuosimaksutHakukohteelleTallennusSwagger)) {
    implicit val authenticated: Authenticated = authenticate

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
