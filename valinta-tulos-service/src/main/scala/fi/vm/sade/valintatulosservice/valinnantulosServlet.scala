package fi.vm.sade.valintatulosservice
import java.net.InetAddress
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

import fi.vm.sade.security.AuthorizationFailedException
import fi.vm.sade.sijoittelu.tulos.dto.{HakemuksenTila, IlmoittautumisTila, ValintatuloksenTila}
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.kayttooikeus.KayttooikeusUserDetailsService
import fi.vm.sade.valintatulosservice.security.{Role, Session}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HyvaksymiskirjePatch, SessionRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.scalatra._
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger._

import scala.util.{Failure, Try}

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

  protected def parseMandatoryParam(paramName:String): String = {
    params.getOrElse(paramName, throw new IllegalArgumentException(s"Parametri ${paramName} on pakollinen."))
  }

  protected def parseIfUnmodifiedSince(appConfig: VtsAppConfig): Option[Instant] = request.headers.get(appConfig.settings.headerIfUnmodifiedSince) match {
    case Some(s) =>
      Try(Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(s))) match {
        case x if x.isSuccess => Some(x.get)
        case Failure(e) => throw new IllegalArgumentException(s"Ei voitu jäsentää otsaketta ${appConfig.settings.headerIfUnmodifiedSince} muodossa $RFC1123sample.", e)
      }
    case None => None
  }

  protected def getIfUnmodifiedSince(appConfig: VtsAppConfig): Instant = parseIfUnmodifiedSince(appConfig: VtsAppConfig) match {
    case Some(s) => s
    case None => throw new IllegalArgumentException(s"Otsake ${appConfig.settings.headerIfUnmodifiedSince} on pakollinen.")
  }
}

class ValinnantulosServlet(valinnantulosService: ValinnantulosService,
                           val valintatulosService: ValintatulosService,
                           val sessionRepository: SessionRepository,
                           appConfig: VtsAppConfig)
                          (implicit val swagger: Swagger)
  extends ValinnantulosServletBase with CasAuthenticatedServlet with DeadlineDecorator {

  override val applicationDescription = "Valinnantuloksen REST API"

  val valinnantulosSwagger: OperationBuilder = (apiOperation[List[Valinnantulos]]("valinnantulos")
    summary "Valinnantulos"
    parameter queryParam[String]("valintatapajonoOid").description("Valintatapajonon OID")
    parameter queryParam[String]("hakukohdeOid").description("Hakukohteen OID")
    tags "valinnan-tulos")
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
          Ok(body = tulokset, headers = Map(appConfig.settings.headerLastModified -> createLastModifiedHeader(lastModified)))
        case None =>
          Ok(List())
      })
  }

  val valinnantuloksetHakemukselleSwagger: OperationBuilder = (apiOperation[List[ValinnantulosWithTilahistoria]]("valinnantuloksetHakemukselle")
    summary "Valinnantulos yksittäiselle hakemukselle"
    parameter queryParam[String]("hakemusOid").description("Hakemuksen OID")
    tags "valinnan-tulos")
  models.update("Valinnantulos", models("Valinnantulos").copy(properties = models("Valinnantulos").properties.map {
    case ("ilmoittautumistila", mp) => ("ilmoittautumistila", ilmoittautumistilaModelProperty(mp))
    case ("valinnantila", mp) => ("valinnantila", valinnantilaModelProperty(mp))
    case ("vastaanottotila", mp) => ("vastaanottotila", vastaanottotilaModelProperty(mp))
    case p => p
  }))
  get("/hakemus/", operation(valinnantuloksetHakemukselleSwagger)) {
    contentType = formats("json")
    implicit val authenticated = authenticate
    val hakemusOid = parseHakemusOid.fold(throw _, x => x)
    valinnantulosService.getValinnantuloksetForHakemus(hakemusOid, auditInfo) match {
      case Some((lastModified, valinnantulokset)) =>
        Ok(body = valinnantulokset, headers = Map(appConfig.settings.headerLastModified -> createLastModifiedHeader(lastModified)))
      case None =>
        Ok(body = List.empty)
    }
  }

  val valinnantulosMuutosSwagger: OperationBuilder = (apiOperation[Unit]("muokkaaValinnantulosta")
    summary "Muokkaa valinnantulosta"
    parameter pathParam[String]("valintatapajonoOid").description("Valintatapajonon OID")
    parameter headerParam[String](appConfig.settings.headerIfUnmodifiedSince).description(s"Aikaleima RFC 1123 määrittelemässä muodossa $RFC1123sample").required
    parameter bodyParam[List[Valinnantulos]].description("Muutokset valinnan tulokseen").required
    tags "valinnan-tulos")
  models.update("Valinnantulos", models("Valinnantulos").copy(properties = models("Valinnantulos").properties.map {
    case ("ilmoittautumistila", mp) => ("ilmoittautumistila", ilmoittautumistilaModelProperty(mp))
    case ("valinnantila", mp) => ("valinnantila", valinnantilaModelProperty(mp))
    case ("vastaanottotila", mp) => ("vastaanottotila", vastaanottotilaModelProperty(mp))
    case p => p
  }))
  patch("/:valintatapajonoOid", operation(valinnantulosMuutosSwagger)) {
    contentType = formats("json")
    implicit val authenticated = authenticate
    authorize(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD, Role.ATARU_KEVYT_VALINTA_CRUD)
    val valintatapajonoOid = parseValintatapajonoOid.fold(throw _, x => x)
    val ifUnmodifiedSince: Instant = getIfUnmodifiedSince(appConfig)
    val valinnantulokset = parsedBody.extract[List[Valinnantulos]]
    Ok(
      valinnantulosService.storeValinnantuloksetAndIlmoittautumiset(
        valintatapajonoOid, valinnantulokset, Some(ifUnmodifiedSince), auditInfo)
    )
  }
}

class ErillishakuServlet(valinnantulosService: ValinnantulosService, hyvaksymiskirjeService: HyvaksymiskirjeService, userDetailsService: KayttooikeusUserDetailsService, appConfig: VtsAppConfig)
  (implicit val swagger: Swagger) extends ValinnantulosServletBase with AuditInfoParameter  {

  override val applicationDescription = "Erillishaun valinnantuloksen REST API"

  private def getSession(auditSession: AuditSessionRequest): (UUID, Session) = (UUID.randomUUID(), getAuditSession(auditSession))

  private def getAuditSession(s:AuditSessionRequest) = fi.vm.sade.valintatulosservice.security.AuditSession(s.personOid, s.roles.map(Role(_)).toSet)

  private def parseHyvaksymiskirjeet = params.get("hyvaksymiskirjeet").exists(_.equalsIgnoreCase("true"))

  protected def getAuditInfo(uid:String, inetAddress:String, userAgent:String) = {
    (for {
      user <- userDetailsService.getUserByUsername(uid).right
    } yield {
      AuditInfo(
        (UUID.randomUUID(), fi.vm.sade.valintatulosservice.security.AuditSession(user.oid, user.roles)),
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
    parameter headerParam[String](appConfig.settings.headerIfUnmodifiedSince).description(s"Aikaleima RFC 1123 määrittelemässä muodossa $RFC1123sample").required
    parameter bodyParam[List[ValinnantulosRequest]].description("Muutokset valinnan tulokseen ja kirjautumistieto").required
    tags "erillishaku-valinnan-tulos")
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
    val ifUnmodifiedSince: Option[Instant] = parseIfUnmodifiedSince(appConfig)
    val valinnantulokset = parsedBody.extract[ValinnantulosRequest].valinnantulokset
    val storeValinnantulosResult = valinnantulosService.storeValinnantuloksetAndIlmoittautumiset(
      valintatapajonoOid, valinnantulokset, ifUnmodifiedSince, auditInfo)
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
    tags "erillishaku-valinnan-tulos")
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

        Ok(body = response, headers = Map(appConfig.settings.headerLastModified -> createLastModifiedHeader(lastModified)))
      }
    }
  }
}

case class ValinnantulosWithHyvaksymiskirje()
case class ValinnantulosRequest(valinnantulokset:List[Valinnantulos], auditSession: AuditSessionRequest) extends RequestWithAuditSession
