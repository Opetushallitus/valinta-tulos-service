package fi.vm.sade.valintatulosservice.tulostenmetsastaja

import fi.vm.sade.security.AuthorizationFailedException
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakuOid, Oids}
import fi.vm.sade.valintatulosservice.{Authenticated, CasAuthenticatedServlet, VtsServletBase}
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.{ActionResult, Forbidden, Found, Ok}
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
    tarkistaOikeudet()
    Ok(puuttuvatTuloksetService.find(parseHakuOid))
  }

  val puuttuvatTuloksetHauilleTaustallaSwagger: OperationBuilder = (apiOperation[String]("puuttuvien tulosten haku taustalla")
    summary "Etsi sellaiset hakemuksilta löytyvät hakutoiveet, joille ei löydy tulosta valintarekisteristä, ja tallenna tulos kirjanpitoon"
    parameter bodyParam[Seq[String]]("hakuOids").description("Hakujen OIDit"))
  post("/", operation(puuttuvatTuloksetHauilleTaustallaSwagger)) {
    //tarkistaOikeudet()
    val hakuOids = parsedBody.extract[Seq[String]].map(HakuOid)
    logger.info(s"Haetaan hakuOideille $hakuOids")
    val messages = hakuOids.map(puuttuvatTuloksetService.haeJaTallenna)
    Ok(Map("message" -> messages))
  }

  val hakuListaSwagger: OperationBuilder = (apiOperation[Seq[HaunTiedotListalle]]("Yhteenveto kaikista hauista")
    summary "Listaa kaikki haut ja yhteenveto niiden puuttuvista tiedoista")
  get("/yhteenveto", operation(hakuListaSwagger)) {
    // tarkistaOikeudet()
    Ok(puuttuvatTuloksetService.findSummary())
  }

  private def parseHakuOid: HakuOid = HakuOid(params.getOrElse("hakuOid",
    throw new IllegalArgumentException("URL-parametri hakuOid on pakollinen.")))


  private def tarkistaOikeudet(): Option[ActionResult] = {
    implicit val authenticated: Authenticated = try {
      authenticate
    } catch {
      case e: AuthorizationFailedException =>
        return Some(Found(location = "/cas/login", reason = "Kirjaudu ensin sisään"))
    }

    try {
      authorize(Role.SIJOITTELU_CRUD_OPH)
    } catch {
      case e: AuthorizationFailedException =>
        return Some(Forbidden(reason = "Ei riittäviä oikeuksia"))
    }
    None
  }
}
