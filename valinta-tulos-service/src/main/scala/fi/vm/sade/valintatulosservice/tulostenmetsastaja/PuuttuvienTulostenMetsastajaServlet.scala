package fi.vm.sade.valintatulosservice.tulostenmetsastaja

import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakuOid, Oids}
import fi.vm.sade.valintatulosservice.{CasAuthenticatedServlet, VtsServletBase}
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.Ok
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder

class PuuttuvienTulostenMetsastajaServlet(valintarekisteriDb: ValintarekisteriDb, hakemusRepository: HakemusRepository, virkailijaBaseUrl: String)
                                         (implicit val swagger: Swagger) extends VtsServletBase with CasAuthenticatedServlet {
  override implicit val jsonFormats: Formats = DefaultFormats ++ Oids.getSerializers()
  override val applicationName = Some("auth/puuttuvat")
  override val applicationDescription = "REST API puuttuvien tuloksien etsimiseen"
  override val sessionRepository: SessionRepository = valintarekisteriDb

  private val puuttuvatTuloksetService = new PuuttuvatTuloksetService(valintarekisteriDb, hakemusRepository, virkailijaBaseUrl)


  val puuttuvatTuloksetHaulleSwagger: OperationBuilder = (apiOperation[HaunPuuttuvatTulokset]("puuttuvien tulosten haku")
    summary "Etsi sellaiset hakemuksilta löytyvät hakutoiveet, joille ei löydy tulosta valintarekisteristä"
    parameter queryParam[String]("hakuOid").description("haun OID")
    )
  get("/", operation(puuttuvatTuloksetHaulleSwagger)) {
    implicit val authenticated = authenticate
    authorize(Role.SIJOITTELU_CRUD_OPH)
    Ok(puuttuvatTuloksetService.find(parseHakuOid))
  }

  private def parseHakuOid: HakuOid = HakuOid(params.getOrElse("hakuOid",
    throw new IllegalArgumentException("URL-parametri hakuOid on pakollinen.")))
}
