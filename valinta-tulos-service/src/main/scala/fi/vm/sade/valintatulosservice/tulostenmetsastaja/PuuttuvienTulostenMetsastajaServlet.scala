package fi.vm.sade.valintatulosservice.tulostenmetsastaja

import fi.vm.sade.security.AuthorizationFailedException
import fi.vm.sade.valintatulosservice.hakemus.{HakemusRepository, HakuAppRepository}
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakuOid, Oids}
import fi.vm.sade.valintatulosservice.{Authenticated, CasAuthenticatedServlet, UrlSerializer, VtsServletBase}
import org.json4s.Formats
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.{ActionResult, Forbidden, Found, Ok}

class PuuttuvienTulostenMetsastajaServlet(valintarekisteriDb: ValintarekisteriDb,
                                          hakemusRepository: HakuAppRepository,
                                          virkailijaBaseUrl: String)
                                         (implicit val swagger: Swagger) extends VtsServletBase with CasAuthenticatedServlet {
  override implicit val jsonFormats: Formats = JsonFormats.jsonFormats ++ Oids.getSerializers() ++ Seq(new UrlSerializer)
  override val applicationName = Some("auth/puuttuvat")
  override val applicationDescription = "REST API puuttuvien tuloksien etsimiseen"
  override val sessionRepository: SessionRepository = valintarekisteriDb

  private val puuttuvatTuloksetService = new PuuttuvatTuloksetService(valintarekisteriDb, hakemusRepository, virkailijaBaseUrl)

  val puuttuvatTuloksetHaulleSwagger: OperationBuilder = (apiOperation[HaunPuuttuvat[HakukohteenPuuttuvat]]("puuttuvien tulosten haku")
    summary "Etsi sellaiset hakemuksilta löytyvät hakutoiveet, joille ei löydy tulosta valintarekisteristä"
    parameter queryParam[String]("hakuOid").description("haun OID")
    )
  get("/", operation(puuttuvatTuloksetHaulleSwagger)) {
    tarkistaOikeudet()
    Ok(puuttuvatTuloksetService.kokoaPuuttuvatTulokset(parseHakuOid))
  }

  val puuttuvatTuloksetHauilleTaustallaSwagger: OperationBuilder = (apiOperation[TaustapaivityksenTila]("puuttuvien tulosten haku taustalla")
    summary "Etsi sellaiset hakemuksilta löytyvät hakutoiveet, joille ei löydy tulosta valintarekisteristä, ja tallenna tulos kirjanpitoon"
    parameter bodyParam[Seq[String]]("hakuOids").description("Hakujen OIDit"))
  post("/", operation(puuttuvatTuloksetHauilleTaustallaSwagger)) {
    tarkistaOikeudet()
    val hakuOids = parsedBody.extract[Seq[String]].map(HakuOid)
    logger.info(s"Haetaan hakuOideille $hakuOids")

    Ok(puuttuvatTuloksetService.haeJaTallenna(hakuOids))
  }

  val hakuListaSwagger: OperationBuilder = (apiOperation[Seq[HaunTiedotListalle]]("Yhteenveto kaikista hauista")
    summary "Listaa kaikki haut ja yhteenveto niiden puuttuvista tiedoista")
  get("/yhteenveto", operation(hakuListaSwagger)) {
    tarkistaOikeudet()
    Ok(puuttuvatTuloksetService.findSummary())
  }

  val haunPuuttuvatSwagger: OperationBuilder = (apiOperation[Seq[TarjoajanPuuttuvat[HakukohteenPuuttuvatSummary]]]("Yksittäisen organisaation puuttuvat")
    summary "Organisaation puuttuvien tulosten määrät hakukohteittain"
    parameter pathParam[String]("hakuOid").description("Haun OID"))
  get("/haku/:hakuOid", operation(haunPuuttuvatSwagger)) {
    tarkistaOikeudet()
    Ok(puuttuvatTuloksetService.findMissingResultsByOrganisation(HakuOid(params("hakuOid"))))
  }

  val paivitaKaikkiSwagger : OperationBuilder = (apiOperation[TaustapaivityksenTila]("Käynnistetään puuttuvien tuloksien haku kaikille hauille")
      parameter bodyParam[Boolean]("paivitaMyosOlemassaolevat").description("Päivitetäänkö myös hauille, joilta löytyy jo tieto puuttuvista"))
  post("/paivitaKaikki", operation(paivitaKaikkiSwagger)) {
    tarkistaOikeudet()
    var paivitaMyosOlemassaolevat = (parsedBody \ "paivitaMyosOlemassaolevat").extract[Boolean]
    logger.info("Käynnistetään puuttuvien tulosten etsiminen " + (if (paivitaMyosOlemassaolevat) {
        "kaikille hauille."
      } else {
        "hauille, joilta ei löydy tietoa puuttuvista."
      }))
    Ok(puuttuvatTuloksetService.haeJaTallennaKaikki(paivitaMyosOlemassaolevat))
  }

  val taustapaivityksenTilaSwagger : OperationBuilder = apiOperation[TaustapaivityksenTila]("Lue taustapäivityksen tila")
  get("/taustapaivityksenTila", operation(taustapaivityksenTilaSwagger)) {
    tarkistaOikeudet()
    Ok(puuttuvatTuloksetService.haeTaustapaivityksenTila)
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
