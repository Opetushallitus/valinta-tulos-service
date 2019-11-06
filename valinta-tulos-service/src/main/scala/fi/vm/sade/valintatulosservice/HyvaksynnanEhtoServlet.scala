package fi.vm.sade.valintatulosservice

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ehdollisestihyvaksyttavissa.{GoneException, HyvaksynnanEhto, HyvaksynnanEhtoRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakukohdeOid, ValintatapajonoOid}
import org.scalatra._
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder

class HyvaksynnanEhtoServlet(hyvaksynnanEhtoRepository: HyvaksynnanEhtoRepository,
                             hakuService: HakuService,
                             hakemusRepository: HakemusRepository,
                             authorizer: OrganizationHierarchyAuthorizer,
                             audit: Audit,
                             val sessionRepository: SessionRepository)
                            (implicit val swagger: Swagger)
  extends VtsServletBase with CasAuthenticatedServlet {
  override val applicationDescription = "Hyväksynnän ehto REST API"

  error({
    case e: GoneException =>
      Gone(body = Map("error" -> e.getMessage))
  })

  val hyvaksynnanEhtoHakukohteessaSwagger: OperationBuilder =
    (apiOperation[HyvaksynnanEhto]("hyvaksynnanEhtoHakukohteessa")
      summary "Hyväksynnän ehto hakukohteessa"
      parameter pathParam[String]("hakemusOid").description("Hakemuksen OID").required
      parameter pathParam[String]("hakukohdeOid").description("Hakukohteen OID").required
      tags "hyvaksynnan-ehto")
  get("/:hakemusOid/hakukohteet/:hakukohdeOid", operation(hyvaksynnanEhtoHakukohteessaSwagger)) {
    contentType = formats("json")
    val hakemusOid = parseHakemusOid.fold(throw _, x => x)
    val hakukohdeOid = parseHakukohdeOid.fold(throw _, x => x)

    implicit val authenticated: Authenticated = authenticate
    authorize(hakukohdeOid, Set(Role.ATARU_HAKEMUS_READ, Role.ATARU_HAKEMUS_CRUD, Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD))

    checkIsHakutoive(hakemusOid, hakukohdeOid)

    val response = hyvaksynnanEhtoRepository.runBlocking(
      hyvaksynnanEhtoRepository.hyvaksynnanEhtoHakukohteessa(hakemusOid, hakukohdeOid))

    auditLogRead(hakemusOid, hakukohdeOid)

    response match {
      case Some((ehto, lastModified)) =>
        Ok(body = ehto, headers = Map("Last-Modified" -> createLastModifiedHeader(lastModified)))
      case None =>
        NotFound(body = Map("error" -> "Not Found"))
    }
  }

  val hyvaksynnanEhdotValintatapajonoissaSwagger: OperationBuilder =
    (apiOperation[Map[ValintatapajonoOid, HyvaksynnanEhto]]("hyvaksynnanEhdotValintatapajonoissa")
      summary "Hyväksynnän ehdot hakukohteen valintatapajonoissa"
      parameter pathParam[String]("hakemusOid").description("Hakemuksen OID").required
      parameter pathParam[String]("hakukohdeOid").description("Hakukohteen OID").required
      tags "hyvaksynnan-ehto")
  get("/:hakemusOid/hakukohteet/:hakukohdeOid/valintatapajonot", operation(hyvaksynnanEhdotValintatapajonoissaSwagger)) {
    contentType = formats("json")
    val hakemusOid = parseHakemusOid.fold(throw _, x => x)
    val hakukohdeOid = parseHakukohdeOid.fold(throw _, x => x)

    implicit val authenticated: Authenticated = authenticate
    authorize(hakukohdeOid, Set(Role.ATARU_HAKEMUS_READ, Role.ATARU_HAKEMUS_CRUD, Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD))

    checkIsHakutoive(hakemusOid, hakukohdeOid)

    val response = hyvaksynnanEhtoRepository.runBlocking(
      hyvaksynnanEhtoRepository.hyvaksynnanEhdotValintatapajonoissa(hakemusOid, hakukohdeOid))

    auditLogRead(hakemusOid, hakukohdeOid)

    response match {
      case Nil => Ok(body = Map.empty)
      case ehdot => Ok(body = ehdot.map(t => t._1 -> t._2).toMap, headers = Map("Last-Modified" -> createLastModifiedHeader(ehdot.map(_._3).max)))
    }
  }

  val putHyvaksynnanEhtoHakukohteessaSwagger: OperationBuilder = (
    apiOperation[HyvaksynnanEhto]("muokkaaHyvaksynnanEhtoaHakukohteessa")
      summary "Muokkaa hyväksynnän ehtoa hakukohteessa"
      parameter pathParam[String]("hakemusOid").description("Hakemuksen OID").required
      parameter pathParam[String]("hakukohdeOid").description("Hakukohteen OID").required
      parameter headerParam[String]("If-Unmodified-Since").description(s"Aikaleima RFC 1123 määrittelemässä muodossa $RFC1123sample").optional
      parameter headerParam[String]("If-None-Match").description(s"'*' mikäli uuden tietueen tallennus").optional
      parameter bodyParam[HyvaksynnanEhto].description("Ehdollisen hyväksynnän ehto").required
      tags "hyvaksynnan-ehto")
  put("/:hakemusOid/hakukohteet/:hakukohdeOid", operation(putHyvaksynnanEhtoHakukohteessaSwagger)) {
    contentType = formats("json")
    val hakemusOid = parseHakemusOid.fold(throw _, x => x)
    val hakukohdeOid = parseHakukohdeOid.fold(throw _, x => x)
    val ehto = parsedBody.extract[HyvaksynnanEhto]
    val ifUnmodifiedSince = parseIfUnmodifiedSince match {
      case Right(ifUnmodifiedSince) => Some(ifUnmodifiedSince)
      case Left(_: NoSuchElementException) =>
        parseIfNoneMatch match {
          case Right("*") =>
            None
          case Right(s) =>
            throw new IllegalArgumentException(s"Odottamaton otsakkeen If-None-Match arvo '$s'.")
          case Left(_: NoSuchElementException) =>
            throw new IllegalArgumentException("Otsake If-Unmodified-Since tai If-None-Match on pakollinen.")
          case Left(tt) =>
            throw tt
        }
      case Left(t) =>
        throw t
    }

    implicit val authenticated: Authenticated = authenticate
    authorize(hakukohdeOid, Set(Role.ATARU_HAKEMUS_CRUD, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD))

    checkIsHakutoive(hakemusOid, hakukohdeOid)

    val ilmoittaja = auditInfo.session._2.personOid
    val response = hyvaksynnanEhtoRepository.runBlocking(
      ifUnmodifiedSince match {
        case Some(ius) =>
          hyvaksynnanEhtoRepository.updateHyvaksynnanEhtoHakukohteessa(hakemusOid, hakukohdeOid, ehto, ilmoittaja, ius)
        case None =>
          hyvaksynnanEhtoRepository.insertHyvaksynnanEhtoHakukohteessa(hakemusOid, hakukohdeOid, ehto, ilmoittaja)
      })
    val isUpdate = ifUnmodifiedSince.isDefined

    auditLogPut(hakemusOid, hakukohdeOid, ehto, isUpdate)

    if (isUpdate) {
      Ok(body = response._1, headers = Map("Last-Modified" -> createLastModifiedHeader(response._2)))
    } else {
      Created(body = response._1, headers = Map("Last-Modified" -> createLastModifiedHeader(response._2)))
    }
  }

  val deleteHyvaksynnanEhtoHakukohteessaSwagger: OperationBuilder = (
    apiOperation[Unit]("poistaHyvaksynnanEhtoHakukohteessa")
      summary "Poista hyväksynnän ehto hakukohteessa"
      parameter pathParam[String]("hakemusOid").description("Hakemuksen OID").required
      parameter pathParam[String]("hakukohdeOid").description("Hakukohteen OID").required
      parameter headerParam[String]("If-Unmodified-Since").description(s"Aikaleima RFC 1123 määrittelemässä muodossa $RFC1123sample").required
      tags "hyvaksynnan-ehto")
  delete("/:hakemusOid/hakukohteet/:hakukohdeOid", operation(deleteHyvaksynnanEhtoHakukohteessaSwagger)) {
    contentType = formats("json")
    val hakemusOid = parseHakemusOid.fold(throw _, x => x)
    val hakukohdeOid = parseHakukohdeOid.fold(throw _, x => x)
    val ifUnmodifiedSince = parseIfUnmodifiedSince.fold(throw _, x => x)

    checkIsHakutoive(hakemusOid, hakukohdeOid)

    implicit val authenticated: Authenticated = authenticate
    authorize(hakukohdeOid, Set(Role.ATARU_HAKEMUS_CRUD, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD))

    val ehto = hyvaksynnanEhtoRepository.runBlocking(
      hyvaksynnanEhtoRepository.deleteHyvaksynnanEhtoHakukohteessa(hakemusOid, hakukohdeOid, ifUnmodifiedSince))

    auditLogDelete(hakemusOid, hakukohdeOid, ehto)

    NoContent()
  }

  private def authorize(hakukohdeOid: HakukohdeOid, roles: Set[Role])(implicit authenticated: Authenticated): Unit = {
    authorize(roles.toSeq: _*)
    val hakukohde = hakuService.getHakukohde(hakukohdeOid).fold(throw _, x => x)
    authorizer.checkAccess(authenticated.session, hakukohde.organisaatioOiditAuktorisointiin, roles).fold(throw _, x => x)
  }

  private def checkIsHakutoive(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid): Unit = {
    val hakemus = hakemusRepository.findHakemus(hakemusOid).fold(throw _, x => x)
    if (!hakemus.toiveet.exists(_.oid == hakukohdeOid)) {
      throw new IllegalArgumentException(s"Hakukohde $hakukohdeOid ei ole hakemuksen $hakemusOid hakutoive")
    }
  }

  private def auditLogRead(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid)(implicit authenticated: Authenticated): Unit = {
    audit.log(
      auditInfo.user,
      HyvaksynnanEhtoLuku,
      new Target.Builder()
        .setField("hakemus", hakemusOid.toString)
        .setField("hakukohde", hakukohdeOid.toString)
        .build(),
      new Changes.Builder().build())
  }

  private def auditLogPut(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, ehto: HyvaksynnanEhto, isUpdate: Boolean)(implicit authenticated: Authenticated): Unit = {
    audit.log(
      auditInfo.user,
      if (isUpdate) {
        HyvaksynnanEhtoPaivitys
      } else {
        HyvaksynnanEhtoTallennus
      },
      new Target.Builder()
        .setField("hakemus", hakemusOid.toString)
        .setField("hakukohde", hakukohdeOid.toString)
        .build(),
      new Changes.Builder()
        .added("koodi", ehto.koodi)
        .added("fi", ehto.fi)
        .added("sv", ehto.sv)
        .added("en", ehto.en)
        .build())
  }

  private def auditLogDelete(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, ehto: HyvaksynnanEhto)(implicit authenticated: Authenticated): Unit = {
    audit.log(
      auditInfo.user,
      HyvaksynnanEhtoPoisto,
      new Target.Builder()
        .setField("hakemus", hakemusOid.toString)
        .setField("hakukohde", hakukohdeOid.toString)
        .build(),
      new Changes.Builder()
        .removed("koodi", ehto.koodi)
        .removed("fi", ehto.fi)
        .removed("sv", ehto.sv)
        .removed("en", ehto.en)
        .build())
  }
}
