package fi.vm.sade.valintatulosservice
import java.net.InetAddress
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.util.UUID

import fi.vm.sade.security.{AuthenticationFailedException, AuthorizationFailedException}
import fi.vm.sade.sijoittelu.tulos.dto.{HakemuksenTila, IlmoittautumisTila}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.security.{Role, Session}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger._

import scala.util.{Failure, Try}

case class ValinnantulosUpdateStatus(status:Int, message:String, valintatapajonoOid:String, hakemusOid:String)

class ValinnantulosServlet(valinnantulosService: ValinnantulosService,
                           sessionRepository: SessionRepository)
                          (implicit val swagger: Swagger)
  extends ScalatraServlet with JacksonJsonSupport with SwaggerSupport with Logging with JsonFormats {

  override val applicationName = Some("auth/valinnan-tulos")
  override val applicationDescription = "Valinnantuloksen REST API"

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

  private def ilmoittautumistilaModelProperty(mp: ModelProperty) = {
    ModelProperty(DataType.String, mp.position, required = true, allowableValues = AllowableValues(IlmoittautumisTila.values().toList.map(_.toString)))
  }

  private def valinnantilaModelProperty(mp: ModelProperty) = {
    ModelProperty(DataType.String, mp.position, required = true, allowableValues = AllowableValues(HakemuksenTila.values().toList.map(_.toString)))
  }

  private def getSession: (UUID, Session) = {
    cookies.get("session").map(UUID.fromString).flatMap(id => sessionRepository.get(id).map((id, _)))
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

  private def parseAuditInfo(session: (UUID, Session)): AuditInfo = {
    AuditInfo(
      session,
      InetAddress.getByName(request.headers.get("X-Forwarded-For").getOrElse({
        logger.warn("X-Forwarded-For was not set. Are we not running behind a load balancer?")
        request.getRemoteAddr
      })),
      request.headers.get("User-Agent").getOrElse(throw new IllegalArgumentException("Otsake User-Agent on pakollinen."))
    )
  }

  val valinnantulosSwagger: OperationBuilder = (apiOperation[List[Valinnantulos]]("valinnantulos")
    summary "Valinnantulos"
    parameter pathParam[String]("valintatapajonoOid").description("Valintatapajonon OID")
    )
  models.update("Valinnantulos", models("Valinnantulos").copy(properties = models("Valinnantulos").properties.map {
    case ("ilmoittautumistila", mp) => ("ilmoittautumistila", ilmoittautumistilaModelProperty(mp))
    case ("valinnantila", mp) => ("valinnantila", valinnantilaModelProperty(mp))
    case p => p
  }))
  get("/:valintatapajonoOid", operation(valinnantulosSwagger)) {
    contentType = formats("json")
    val (id, session) = getSession
    val auditInfo = parseAuditInfo((id, session))
    if (!session.hasAnyRole(Set(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD))) {
      throw new AuthorizationFailedException()
    }
    val valintatapajonoOid = parseValintatapajonoOid
    val valinnanTulokset = valinnantulosService.getValinnantuloksetForValintatapajono(valintatapajonoOid, auditInfo)
    Ok(
      body = valinnanTulokset.map(_._2),
      headers = if (valinnanTulokset.nonEmpty) Map("Last-Modified" -> renderHttpDate(valinnanTulokset.map(_._1).max)) else Map()
    )
  }

  val sample = renderHttpDate(Instant.EPOCH)
  val valinnantulosMuutosSwagger: OperationBuilder = (apiOperation[Unit]("muokkaaValinnantulosta")
    summary "Muokkaa valinnantulosta"
    parameter pathParam[String]("valintatapajonoOid").description("Valintatapajonon OID")
    parameter headerParam[String]("If-Unmodified-Since").description(s"Aikaleima RFC 1123 määrittelemässä muodossa $sample").required
    parameter bodyParam[List[Valinnantulos]].description("Muutokset valinnan tulokseen").required
    )
  models.update("Valinnantulos", models("Valinnantulos").copy(properties = models("Valinnantulos").properties.map {
    case ("ilmoittautumistila", mp) => ("ilmoittautumistila", ilmoittautumistilaModelProperty(mp))
    case ("valinnantila", mp) => ("valinnantila", valinnantilaModelProperty(mp))
    case p => p
  }))
  patch("/:valintatapajonoOid", operation(valinnantulosMuutosSwagger)) {
    contentType = formats("json")
    val (id, session) = getSession
    val auditInfo = parseAuditInfo((id, session))
    if (!session.hasAnyRole(Set(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD))) {
      throw new AuthorizationFailedException()
    }
    val valintatapajonoOid = parseValintatapajonoOid
    val ifUnmodifiedSince: Instant = parseIfUnmodifiedSince
    val valinnantulokset = parsedBody.extract[List[Valinnantulos]]
    Ok(
      valinnantulosService.storeValinnantuloksetAndIlmoittautumiset(
        valintatapajonoOid, valinnantulokset, ifUnmodifiedSince, auditInfo)
    )
  }
}
