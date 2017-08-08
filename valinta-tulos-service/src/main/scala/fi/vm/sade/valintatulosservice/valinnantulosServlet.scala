package fi.vm.sade.valintatulosservice
import java.net.InetAddress
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.util.UUID

import fi.vm.sade.security.{AuthorizationFailedException, LdapUserService}
import fi.vm.sade.sijoittelu.tulos.dto.{HakemuksenTila, IlmoittautumisTila, ValintatuloksenTila}
import fi.vm.sade.valintatulosservice.security.{Role, Session}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HyvaksymiskirjePatch, SessionRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.scalatra._
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger._

import scala.util.{Failure, Success, Try}

trait ValinnantulosServletBase extends VtsServletBase {
  protected def ilmoittautumistilaModelProperty(mp: ModelProperty) = {
    ModelProperty(DataType.String, mp.position, required = true, allowableValues = AllowableValues(IlmoittautumisTila.values().toList.map(_.toString)))
  }

  protected def valinnantilaModelProperty(mp: ModelProperty) = {
    ModelProperty(DataType.String, mp.position, required = true, allowableValues = AllowableValues(HakemuksenTila.values().toList.map(_.toString)))
  }

  protected def vastaanottotilaModelProperty(mp: ModelProperty) = {
    ModelProperty(DataType.String, mp.position, required = true, allowableValues = AllowableValues(ValintatuloksenTila.values().toList.map(_.toString)))
  }

  protected def parseValintatapajonoOid: Either[Throwable, ValintatapajonoOid] = {
    params.get("valintatapajonoOid").fold[Either[Throwable, ValintatapajonoOid]](Left(new IllegalArgumentException("URL parametri valintatapajono OID on pakollinen.")))(s => Right(ValintatapajonoOid(s)))
  }

  protected def parseHakukohdeOid: Either[Throwable, HakukohdeOid] = {
    params.get("hakukohdeOid").fold[Either[Throwable, HakukohdeOid]](Left(new IllegalArgumentException("URL parametri hakukohde OID on pakollinen.")))(s => Right(HakukohdeOid(s)))
  }

  protected def parseMandatoryParam(paramName:String): String = {
    params.getOrElse(paramName, throw new IllegalArgumentException(s"Parametri ${paramName} on pakollinen."))
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
}

class ValinnantulosServlet(valinnantulosService: ValinnantulosService,
                           val valintatulosService: ValintatulosService,
                           val sessionRepository: SessionRepository)
                          (implicit val swagger: Swagger)
  extends ValinnantulosServletBase with CasAuthenticatedServlet with DeadlineDecorator {

  override val applicationName = Some("auth/valinnan-tulos")
  override val applicationDescription = "Valinnantuloksen REST API"

  val valinnantulosSwagger: OperationBuilder = (apiOperation[List[Valinnantulos]]("valinnantulos")
    summary "Valinnantulos"
    parameter queryParam[String]("valintatapajonoOid").description("Valintatapajonon OID")
    parameter queryParam[String]("hakukohdeOid").description("Hakukohteen OID")
    )
  models.update("Valinnantulos", models("Valinnantulos").copy(properties = models("Valinnantulos").properties.map {
    case ("ilmoittautumistila", mp) => ("ilmoittautumistila", ilmoittautumistilaModelProperty(mp))
    case ("valinnantila", mp) => ("valinnantila", valinnantilaModelProperty(mp))
    case p => p
  }))
  get("/", operation(valinnantulosSwagger)) {
    contentType = formats("json")
    implicit val authenticated = authenticate
    authorize(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)
    parseValintatapajonoOid.right.map(valinnantulosService.getValinnantuloksetForValintatapajono(_, auditInfo))
      .left.flatMap(_ => parseHakukohdeOid.right.map(valinnantulosService.getValinnantuloksetForHakukohde(_, auditInfo)))
      .fold(throw _, {
        case Some((lastModified, valinnantulokset)) =>
          val hakuOid: Option[String] = params.get("hakuOid")
          // if hakuOid was specified, decorate with deadlines
          val tulokset: Set[Valinnantulos] =
            if (hakuOid.isDefined && valinnantulokset.nonEmpty) {
              val hakukohdeOid = valinnantulokset.head.hakukohdeOid
              decorateValinnantuloksetWithDeadlines(HakuOid(hakuOid.get), hakukohdeOid, valinnantulokset)
            } else {
              valinnantulokset
            }
          Ok(body = tulokset, headers = Map("Last-Modified" -> createLastModifiedHeader(lastModified)))
        case None =>
          Ok(List())
      })
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
    case ("vastaanottotila", mp) => ("vastaanottotila", vastaanottotilaModelProperty(mp))
    case p => p
  }))
  patch("/:valintatapajonoOid", operation(valinnantulosMuutosSwagger)) {
    contentType = formats("json")
    implicit val authenticated = authenticate
    authorize(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)
    val erillishaku = parseErillishaku
    val valintatapajonoOid = parseValintatapajonoOid.fold(throw _, x => x)
    val ifUnmodifiedSince: Instant = getIfUnmodifiedSince
    val valinnantulokset = parsedBody.extract[List[Valinnantulos]]
    Ok(
      valinnantulosService.storeValinnantuloksetAndIlmoittautumiset(
        valintatapajonoOid, valinnantulokset, Some(ifUnmodifiedSince), auditInfo, erillishaku.getOrElse(false))
    )
  }
}

class ErillishakuServlet(valinnantulosService: ValinnantulosService, hyvaksymiskirjeService: HyvaksymiskirjeService, ldapUserService: LdapUserService)
  (implicit val swagger: Swagger) extends ValinnantulosServletBase with AuditInfoParameter  {

  override val applicationName = Some("erillishaku/valinnan-tulos")
  override val applicationDescription = "Erillishaun valinnantuloksen REST API"

  private def getSession(auditSession: AuditSessionRequest): (UUID, Session) = (UUID.randomUUID(), getAuditSession(auditSession))

  private def getAuditSession(s:AuditSessionRequest) = fi.vm.sade.valintatulosservice.security.AuditSession(s.personOid, s.roles.map(Role(_)).toSet)

  private def parseHyvaksymiskirjeet = params.get("hyvaksymiskirjeet").exists(_.equalsIgnoreCase("true"))

  protected def getAuditInfo(uid:String, inetAddress:String, userAgent:String) = {
    (for {
      user <- ldapUserService.getLdapUser(uid).right
    } yield {
      AuditInfo(
        (UUID.randomUUID(), fi.vm.sade.valintatulosservice.security.AuditSession(user.oid, user.roles.map(Role(_)).toSet)),
        InetAddress.getByName(inetAddress),
        userAgent
      )
    }) match {
      case Right(auditInfo) => auditInfo
      case Left(failure) => throw failure
    }
  }

  val erillishaunValinnantulosMuutosSwagger: OperationBuilder = (apiOperation[Unit]("muokkaaValinnantulosta")
    summary "Muokkaa erillishaun valinnantulosta"
    parameter pathParam[String]("valintatapajonoOid").description("Valintatapajonon OID")
    parameter headerParam[String]("If-Unmodified-Since").description(s"Aikaleima RFC 1123 määrittelemässä muodossa $sample").required
    parameter bodyParam[List[ValinnantulosRequest]].description("Muutokset valinnan tulokseen ja kirjautumistieto").required
  )
  models.update("Valinnantulos", models("Valinnantulos").copy(properties = models("Valinnantulos").properties.map {
    case ("ilmoittautumistila", mp) => ("ilmoittautumistila", ilmoittautumistilaModelProperty(mp))
    case ("valinnantila", mp) => ("valinnantila", valinnantilaModelProperty(mp))
    case ("vastaanottotila", mp) => ("vastaanottotila", vastaanottotilaModelProperty(mp))
    case p => p
  }))
  post("/:valintatapajonoOid", operation(erillishaunValinnantulosMuutosSwagger)) {
    contentType = formats("json")
    val valinnantulosRequest: ValinnantulosRequest = parsedBody.extract[ValinnantulosRequest]
    val auditInfo = getAuditInfo(valinnantulosRequest)
    if (!auditInfo.session._2.hasAnyRole(Set(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD))) {
      throw new AuthorizationFailedException()
    }
    val valintatapajonoOid = parseValintatapajonoOid.fold(throw _, x => x)
    val ifUnmodifiedSince: Option[Instant] = parseIfUnmodifiedSince
    val valinnantulokset = parsedBody.extract[ValinnantulosRequest].valinnantulokset
    val storeValinnantulosResult = valinnantulosService.storeValinnantuloksetAndIlmoittautumiset(
      valintatapajonoOid, valinnantulokset, ifUnmodifiedSince, auditInfo, true)
    Try(hyvaksymiskirjeService.updateHyvaksymiskirjeet(
      valinnantulokset.map(v => HyvaksymiskirjePatch(v.henkiloOid, v.hakukohdeOid, v.hyvaksymiskirjeLahetetty)).toSet, auditInfo)) match {
        case Failure(e) => logger.warn("Virhe hyväksymiskirjeiden lähetyspäivämäärien päivityksessä", e)
        case x if x.isSuccess => Unit
    }
    Ok(storeValinnantulosResult)
  }

  val erillishaunValinnantulosSwagger: OperationBuilder = (apiOperation[List[Valinnantulos]]("valinnantulos")
    summary "Erillishaun valinnantulos"
    parameter pathParam[String]("valintatapajonoOid").description("Valintatapajonon OID")
    parameter queryParam[String]("uid").description("Audit-käyttäjän uid")
    parameter queryParam[String]("inetAddress").description("Audit-käyttäjän inetAddress")
    parameter queryParam[String]("userAgent").description("Audit-käyttäjän userAgent")
    parameter queryParam[Boolean]("hyvaksymiskirjeet").description("Palauta hyväksymiskirjeiden lähetyspäivämäärät")
  )
  models.update("Valinnantulos", models("Valinnantulos").copy(properties = models("Valinnantulos").properties.map {
    case ("ilmoittautumistila", mp) => ("ilmoittautumistila", ilmoittautumistilaModelProperty(mp))
    case ("valinnantila", mp) => ("valinnantila", valinnantilaModelProperty(mp))
    case p => p
  }))
  get("/:valintatapajonoOid", operation(erillishaunValinnantulosSwagger)) {
    contentType = formats("json")
    val auditInfo = getAuditInfo(
      parseMandatoryParam("uid"),
      parseMandatoryParam("inetAddress"),
      parseMandatoryParam("userAgent")
    )
    if (!auditInfo.session._2.hasAnyRole(Set(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD))) {
      throw new AuthorizationFailedException()
    }
    val valintatapajonoOid = parseValintatapajonoOid.fold(throw _, x => x)

    valinnantulosService.getValinnantuloksetForValintatapajono(valintatapajonoOid, auditInfo) match {
      case None => Ok(List())
      case Some((lastModified, valinnantulokset)) => {

        lazy val hakukohdeOid = valinnantulokset.head.hakukohdeOid
        lazy val hyvaksymiskirjeet = hyvaksymiskirjeService.getHyvaksymiskirjeet(hakukohdeOid, auditInfo)

        def findLahetetty(henkiloOid:String) = hyvaksymiskirjeet.find(_.henkiloOid == henkiloOid).map(_.lahetetty)
        def mergeTuloksetJaKirjeet = valinnantulokset.map(v => v.copy(hyvaksymiskirjeLahetetty = findLahetetty(v.henkiloOid)))

        val isHyvaksymiskirjeet = !valinnantulokset.isEmpty && parseHyvaksymiskirjeet
        val response = if (isHyvaksymiskirjeet) mergeTuloksetJaKirjeet else valinnantulokset

        Ok(body = response, headers = Map("Last-Modified" -> createLastModifiedHeader(lastModified)))
      }
    }
  }
}

case class ValinnantulosWithHyvaksymiskirje()
case class ValinnantulosRequest(valinnantulokset:List[Valinnantulos], auditSession: AuditSessionRequest) extends RequestWithAuditSession
