package fi.vm.sade.valintatulosservice
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.util.UUID

import fi.vm.sade.security.{AuthenticationFailedException, AuthorizationFailedException}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.security.{Role, Session}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{EiTehty, SijoitteluajonIlmoittautumistila, Vastaanottotila}
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger.{Swagger, SwaggerSupport}

import scala.util.{Failure, Try}

case class ValinnanTulos(hakemusOid: String, vastaanottotila: String, ilmoittautumistila: SijoitteluajonIlmoittautumistila)

case class ValinnanTulosPatch(hakemusOid: String, vastaanottotila: String, ilmoittautumistila: SijoitteluajonIlmoittautumistila)

class ValinnanTulosServlet(valintatulosService: ValintatulosService,
                           ilmoittautumisService: IlmoittautumisService,
                           sessionRepository: SessionRepository)
                          (implicit val swagger: Swagger)
  extends ScalatraServlet with JacksonJsonSupport with SwaggerSupport with Logging with JsonFormats {

  override val applicationName = Some("auth/valinnan-tulos")
  override val applicationDescription = "Valinnan tuloksen REST API"

  error {
    case e: AuthenticationFailedException =>
      logger.warn("authentication failed", e)
      Forbidden("error" -> "Forbidden")
    case e: AuthorizationFailedException =>
      logger.warn("authorization failed", e)
      Forbidden("error" -> "Forbidden")
    case e: IllegalArgumentException =>
      logger.warn("bad request", e)
      BadRequest("error" -> s"Bad request. ${e.getMessage}")
    case e: Throwable =>
      logger.error("internal server error", e)
      InternalServerError("error" -> "Internal server error")
  }

  private def getSession: Session = {
    cookies.get("session").map(UUID.fromString).flatMap(sessionRepository.get)
      .getOrElse(throw new AuthenticationFailedException)
  }

  private def parseIfUnmodifiedSince: Instant = {
    request.headers.get("If-Unmodified-Since") match {
      case Some(s) =>
        Try(Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(s))).recoverWith {
          case e => Failure(new IllegalArgumentException(s"Ei voitu jäsentää otsaketta If-Unmodified-Since muodossa $sample.", e))
        }.get
      case None => throw new IllegalArgumentException("Otsake If-Unmodified-Since on pakollinen.")
    }
  }

  private def parseValintatapajonoOid: String = {
    params.getOrElse("valintatapajonoOid", throw new IllegalArgumentException("URL parametri Valintatapajono OID on pakollinen."))
  }

  private def renderHttpDate(instant: Instant): String = {
    DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(instant, ZoneId.of("GMT")))
  }

  val valinnanTulosSwagger: OperationBuilder = (apiOperation[List[ValinnanTulos]]("valinnanTulos")
    summary "Valinnan tulos"
    parameter pathParam[String]("valintatapajonoOid").description("Valintatapajonon OID")
    )
  get("/:valintatapajonoOid", operation(valinnanTulosSwagger)) {
    contentType = formats("json")
    val session = getSession
    if (!session.hasAnyRole(Set(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD))) {
      throw new AuthorizationFailedException()
    }
    val valintatapajonoOid = parseValintatapajonoOid
    val vastaanottotilat = valintatulosService.findValintaTuloksetForVirkailijaWithoutTilaHakijalle(valintatapajonoOid)
      .map(p => p._1 -> (p._2, p._3)).toMap
    val ilmoittautumistilat = ilmoittautumisService.getIlmoittautumistilat(valintatapajonoOid).right.get
      .map(p => p._1 -> (p._2, p._3)).toMap
    val lastModified = (vastaanottotilat.values.map(_._2) ++ ilmoittautumistilat.values.map(_._2)).max
    Ok(
      body = (vastaanottotilat.keySet ++ ilmoittautumistilat.keySet)
        .map(hakemusOid => ValinnanTulos(
          hakemusOid,
          vastaanottotilat.getOrElse(hakemusOid, (Vastaanottotila.kesken, null))._1.toString,
          ilmoittautumistilat.getOrElse(hakemusOid, (EiTehty, null))._1
        )),
      headers = Map("Last-Modified" -> renderHttpDate(lastModified))
    )
  }

  val sample = renderHttpDate(Instant.EPOCH)
  val valinnanTuloksenMuutosSwagger: OperationBuilder = (apiOperation[Unit]("muokkaaValinnanTulosta")
    summary "Muokkaa valinnan tulosta"
    parameter pathParam[String]("valintatapajonoOid").description("Valintatapajonon OID")
    parameter headerParam[String]("If-Unmodified-Since").description(s"Aikaleima RFC 1123 määrittelemässä muodossa $sample").required
    parameter bodyParam[List[ValinnanTulosPatch]].description("Muutokset valinnan tulokseen").required
    )
  patch("/:valintatapajonoOid", operation(valinnanTuloksenMuutosSwagger)) {
    contentType = null
    val session = getSession
    if (!session.hasAnyRole(Set(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD))) {
      throw new AuthorizationFailedException()
    }
    val valintatapajonoOid = parseValintatapajonoOid
    val ifUnmodifiedSince = parseIfUnmodifiedSince
    val vastaanottotilat = valintatulosService.findValintaTuloksetForVirkailijaWithoutTilaHakijalle(valintatapajonoOid)
      .map(p => p._1 -> (p._2, p._3)).toMap
    val ilmoittautumistilat = ilmoittautumisService.getIlmoittautumistilat(valintatapajonoOid).right.get
      .map(p => p._1 -> (p._2, p._3)).toMap
    parsedBody.extract[List[ValinnanTulosPatch]].foreach(muutos => {
      val (edellinenVastaanottotila, vLastModified) = vastaanottotilat.getOrElse(muutos.hakemusOid, (Vastaanottotila.kesken, Instant.MIN))
      val (edellinenIlmoittautumistila, iLastModified) = ilmoittautumistilat.getOrElse(muutos.hakemusOid, (EiTehty, Instant.MIN))
      val lastModified = List(vLastModified, iLastModified).max.truncatedTo(ChronoUnit.SECONDS)
      if (lastModified.isAfter(ifUnmodifiedSince)) {
        logger.warn(s"Hakemus ${muutos.hakemusOid} valintatapajonossa $valintatapajonoOid " +
          s"on muuttunut $lastModified lukemisajan $ifUnmodifiedSince jälkeen.")
      }
      logger.info(s"Käyttäjä ${session.personOid} muokkasi " +
        s"hakemuksen ${muutos.hakemusOid} valinnan tulosta valintatapajonossa $valintatapajonoOid " +
        s"vastaanottotilasta $edellinenVastaanottotila tilaan ${muutos.vastaanottotila} ja " +
        s"ilmoittautumistilasta $edellinenIlmoittautumistila tilaan ${muutos.ilmoittautumistila}")
    })
    NoContent()
  }
}
