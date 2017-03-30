package fi.vm.sade.valintatulosservice

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{Hyvaksymiskirje, HyvaksymiskirjePatch, HyvaksymiskirjeRepository, SessionRepository}
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.{NoContent, Ok}

class HyvaksymiskirjeServlet(hyvaksymiskirjeRepository: HyvaksymiskirjeRepository,
                             hakuService: HakuService,
                             val sessionRepository: SessionRepository,
                             authorizer: OrganizationHierarchyAuthorizer,
                             audit: Audit)
                            (implicit val swagger: Swagger)
  extends VtsServletBase with CasAuthenticatedServlet {

  override val applicationName = Some("auth/hyvaksymiskirje")
  override val applicationDescription = "Hyväksymiskirjeiden REST API"

  private def parseHakukohdeOid: String = params.getOrElse("hakukohdeOid", throw new IllegalArgumentException("URL parametri hakukohdeOid on pakollinen."))

  val hyvaksymiskirjeSwagger: OperationBuilder = (apiOperation[List[Hyvaksymiskirje]]("hyväksymiskirjeet")
    summary "Hyväksymiskirjeet"
    parameter queryParam[String]("hakukohdeOid").description("Hakukohteen OID")
    )
  get("/", operation(hyvaksymiskirjeSwagger)) {
    contentType = formats("json")
    implicit val authenticated = authenticate
    authorize(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)
    val hakukohdeOid = parseHakukohdeOid
    val hakukohde = hakuService.getHakukohde(hakukohdeOid).fold(throw _, h => h)
    authorizer.checkAccess(auditInfo.session._2, hakukohde.tarjoajaOids, Set(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)).fold(throw _, x => x)
    val r = hyvaksymiskirjeRepository.getHyvaksymiskirjeet(hakukohdeOid)
    audit.log(auditInfo.user, HyvaksymiskirjeidenLuku,
      new Target.Builder().setField("hakukohde", hakukohdeOid).build(),
      new Changes.Builder().build()
    )
    Ok(r)
    Ok(hyvaksymiskirjeRepository.getHyvaksymiskirjeet(hakukohdeOid))
  }

  val hyvaksymiskirjeMuokkausSwagger: OperationBuilder = (apiOperation[Unit]("hyväksymiskirjeiden muokkaus")
    summary "Muokkaa hyväksymiskirjeitä"
    parameter bodyParam[List[HyvaksymiskirjePatch]].description("Muutokset hyväksymiskirjeisiin").required
    )
  post("/", operation(hyvaksymiskirjeMuokkausSwagger)) {
    contentType = formats("json")
    implicit val authenticated = authenticate
    authorize(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)
    val xs = parsedBody.extract[List[HyvaksymiskirjePatch]].toSet
    xs.map(_.hakukohdeOid).foreach(hakukohdeOid => {
      val hakukohde = hakuService.getHakukohde(hakukohdeOid).fold(throw _, h => h)
      authorizer.checkAccess(auditInfo.session._2, hakukohde.tarjoajaOids, Set(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)).fold(throw _, x => x)
    })
    hyvaksymiskirjeRepository.update(xs)
    xs.foreach(h => {
      audit.log(auditInfo.user, HyvaksymiskirjeidenMuokkaus,
        new Target.Builder()
          .setField("henkilö", h.henkiloOid)
          .setField("hakukohde", h.hakukohdeOid)
          .build(),
        h match {
          case HyvaksymiskirjePatch(_, _, None) =>
            new Changes.Builder().removed("lähetetty", null).build()
          case HyvaksymiskirjePatch(_, _, Some(lahetetty)) =>
            new Changes.Builder().updated("lähetetty", null, lahetetty.toString).build()
        }
      )
    })
    NoContent()
  }
}
