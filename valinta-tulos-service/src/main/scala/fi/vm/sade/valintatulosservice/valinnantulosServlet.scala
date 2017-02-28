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

import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

trait ValinnantulosServletBase extends ScalatraServlet with JacksonJsonSupport with SwaggerSupport with Logging with JsonFormats {
  protected def ilmoittautumistilaModelProperty(mp: ModelProperty) = {
    ModelProperty(DataType.String, mp.position, required = true, allowableValues = AllowableValues(IlmoittautumisTila.values().toList.map(_.toString)))
  }

  protected def valinnantilaModelProperty(mp: ModelProperty) = {
    ModelProperty(DataType.String, mp.position, required = true, allowableValues = AllowableValues(HakemuksenTila.values().toList.map(_.toString)))
  }

  protected def parseValintatapajonoOid: String = {
    params.getOrElse("valintatapajonoOid", throw new IllegalArgumentException("URL parametri Valintatapajono OID on pakollinen."))
  }

  protected def parseErillishaku: Option[Boolean] = Try(params.get("erillishaku").map(_.toBoolean)) match {
    case Success(erillishaku) => erillishaku
    case Failure(e) => throw new IllegalArgumentException("Parametri erillishaku pitää olla true tai false", e)
  }

  protected def createLastModifiedHeader(instant: Instant): String = {
    //- system_time range in database is of form ["2017-02-28 13:40:02.442277+02",)
    //- RFC-1123 date-time format used in headers has no millis
    //- if Last-Modified/If-Unmodified-Since header is set to 2017-02-28 13:40:02, it will never be inside system_time range
    //-> this is why we wan't to set it to 2017-02-28 13:40:03 instead
    renderHttpDate(instant.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusSeconds(1))
  }

  protected def renderHttpDate(instant: Instant): String = {
    DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(instant, ZoneId.of("GMT")))
  }

  val sample = renderHttpDate(Instant.EPOCH)
  protected def parseIfUnmodifiedSince: Option[Instant] = request.headers.get("If-Unmodified-Since") match {
    case Some(s) =>
      Try(Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(s))) match {
        case x if x.isSuccess => Some(x.get)
        case Failure(e) => throw new IllegalArgumentException(s"Ei voitu jäsentää otsaketta If-Unmodified-Since muodossa $sample.", e)
      }
    case None => None
  }

  protected def getIfUnmodifiedSince: Instant = parseIfUnmodifiedSince match {
    case Some(s) => s
    case None => throw new IllegalArgumentException("Otsake If-Unmodified-Since on pakollinen.")
  }

  protected def parseAuditInfo(session: (UUID, Session)): AuditInfo = {
    AuditInfo(
      session,
      InetAddress.getByName(request.headers.get("X-Forwarded-For").getOrElse({
        logger.warn("X-Forwarded-For was not set. Are we not running behind a load balancer?")
        request.getRemoteAddr
      })),
      request.headers.get("User-Agent").getOrElse(throw new IllegalArgumentException("Otsake User-Agent on pakollinen."))
    )
  }
}

class ValinnantulosServlet(valinnantulosService: ValinnantulosService,
                           sessionRepository: SessionRepository)
                          (implicit val swagger: Swagger)
  extends ValinnantulosServletBase {

  override val applicationName = Some("auth/valinnan-tulos")
  override val applicationDescription = "Valinnantuloksen REST API"

  error {
    case e: AuthenticationFailedException =>
      logger.warn("authentication failed", e)
      Unauthorized("error" -> "Unauthorized")
    case e: AuthorizationFailedException =>
      logger.warn("authorization failed", e)
      Forbidden("error" -> "Forbidden")
    case e: IllegalArgumentException =>
      logger.warn("bad request", e)
      BadRequest("error" -> s"Bad request. ${e.getMessage}")
    case e: IllegalStateException =>
      logger.error("internal server error", e)
      InternalServerError("error" -> "Internal server error")
    case e: Throwable =>
      logger.error("internal server error", e)
      InternalServerError("error" -> "Internal server error")
  }

  private def getSession: (UUID, Session) = {
    cookies.get("session").map(UUID.fromString).flatMap(id => sessionRepository.get(id).map((id, _)))
      .getOrElse(throw new AuthenticationFailedException)
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
      headers = if (valinnanTulokset.nonEmpty) Map("Last-Modified" -> createLastModifiedHeader(valinnanTulokset.map(_._1).max)) else Map()
    )
  }

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
    val erillishaku = parseErillishaku
    val valintatapajonoOid = parseValintatapajonoOid
    val ifUnmodifiedSince: Instant = getIfUnmodifiedSince
    val valinnantulokset = parsedBody.extract[List[Valinnantulos]]
    Ok(
      valinnantulosService.storeValinnantuloksetAndIlmoittautumiset(
        valintatapajonoOid, valinnantulokset, Some(ifUnmodifiedSince), auditInfo, erillishaku.getOrElse(false))
    )
  }
}

class ErillishakuServlet(valinnantulosService: ValinnantulosService)(implicit val swagger: Swagger)
  extends ValinnantulosServletBase {

  override val applicationName = Some("erillishaku/valinnan-tulos")
  override val applicationDescription = "Erillishaun valinnantuloksen REST API"

  private def getSession(auditSession: AuditSession): (UUID, Session) = (UUID.randomUUID(), getAuditSession(auditSession))

  private def getAuditSession(s:AuditSession) = fi.vm.sade.valintatulosservice.security.AuditSession(s.personOid, s.roles.map(Role(_)).toSet)

  private def parseAuditSession = parsedBody.extract[ValinnantulosRequest].auditSession

  protected def getAuditInfo = {
    val auditSession = parseAuditSession
    AuditInfo(
      getSession(auditSession),
      InetAddress.getByName(auditSession.inetAddress),
      auditSession.userAgent
    )
  }

  val erillishaunValinnantulosMuutosSwagger: OperationBuilder = (apiOperation[Unit]("muokkaaValinnantulosta")
    summary "Muokkaa erillishaun valinnantulosta"
    parameter pathParam[String]("valintatapajonoOid").description("Valintatapajonon OID")
    parameter headerParam[String]("If-Unmodified-Since").description(s"Aikaleima RFC 1123 määrittelemässä muodossa $sample").required
    parameter bodyParam[List[ValinnantulosRequest]].description("Muutokset valinnan tulokseen ja kirjautumistieto").required
    )
  /*models.update("Valinnantulos", models("Valinnantulos").copy(properties = models("Valinnantulos").properties.map {
    case ("ilmoittautumistila", mp) => ("ilmoittautumistila", ilmoittautumistilaModelProperty(mp))
    case ("valinnantila", mp) => ("valinnantila", valinnantilaModelProperty(mp))
    case p => p
  }))*/
  post("/:valintatapajonoOid", operation(erillishaunValinnantulosMuutosSwagger)) {
    contentType = formats("json")
    val auditInfo = getAuditInfo
    if (!auditInfo.session._2.hasAnyRole(Set(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD))) {
      throw new AuthorizationFailedException()
    }
    val valintatapajonoOid = parseValintatapajonoOid
    val ifUnmodifiedSince: Option[Instant] = parseIfUnmodifiedSince
    val valinnantulokset = parsedBody.extract[ValinnantulosRequest].valinnantulokset
    Ok(
      valinnantulosService.storeValinnantuloksetAndIlmoittautumiset(
        valintatapajonoOid, valinnantulokset, ifUnmodifiedSince, auditInfo, true)
    )
  }
}

case class AuditSession(personOid:String, roles:List[String], userAgent:String, inetAddress:String)
case class ValinnantulosRequest(valinnantulokset:List[Valinnantulos], auditSession:AuditSession)
