package fi.vm.sade.valintatulosservice

import java.time.Instant

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ehdollisestihyvaksyttavissa.{GoneException, HakemuksenEhdotJaHistoriat, HakutoiveenEhtoJaMuutoshistoria, HyvaksynnanEhto, HyvaksynnanEhtoRepository, Versio}
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

  val hyvaksynnanEhdotHakukohteessaSwagger: OperationBuilder =
    (apiOperation[Map[HakemusOid, HyvaksynnanEhto]]("hyvaksynnanEhdotHakukohteessa")
      summary "Hyväksynnän ehdot hakukohteessa"
      parameter pathParam[String]("hakukohdeOid").description("Hakukohteen OID").required
      tags "hyvaksynnan-ehto")
  get("/hakukohteessa/:hakukohdeOid", operation(hyvaksynnanEhdotHakukohteessaSwagger)) {
    contentType = formats("json")
    val hakukohdeOid = parseHakukohdeOid.fold(throw _, x => x)

    implicit val authenticated: Authenticated = authenticate
    authorize(hakukohdeOid, Set(Role.ATARU_HAKEMUS_READ, Role.ATARU_HAKEMUS_CRUD, Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD))

    val response = hyvaksynnanEhtoRepository.runBlocking(
      hyvaksynnanEhtoRepository.hyvaksynnanEhtoHakukohteessa(hakukohdeOid))

    response.foreach(r => auditLogRead(r._1, Set(hakukohdeOid)))

    response match {
      case Nil => Ok(body = Map.empty)
      case ehdot => Ok(body = ehdot.map(t => t._1 -> t._2).toMap, headers = Map("Last-Modified" -> createLastModifiedHeader(ehdot.map(_._3).max)))
    }
  }

  val hyvaksynnanEhtoHakukohteessaSwagger: OperationBuilder =
    (apiOperation[HyvaksynnanEhto]("hyvaksynnanEhtoHakukohteessa")
      summary "Hakemuksen hyväksynnän ehto hakukohteessa"
      parameter pathParam[String]("hakemusOid").description("Hakemuksen OID").required
      parameter pathParam[String]("hakukohdeOid").description("Hakukohteen OID").required
      tags "hyvaksynnan-ehto")
  get("/hakukohteessa/:hakukohdeOid/hakemus/:hakemusOid", operation(hyvaksynnanEhtoHakukohteessaSwagger)) {
    contentType = formats("json")
    val hakemusOid = parseHakemusOid.fold(throw _, x => x)
    val hakukohdeOid = parseHakukohdeOid.fold(throw _, x => x)

    implicit val authenticated: Authenticated = authenticate
    authorize(hakemusOid, hakukohdeOid, Set(Role.ATARU_HAKEMUS_READ, Role.ATARU_HAKEMUS_CRUD, Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD))

    val response = hyvaksynnanEhtoRepository.runBlocking(
      hyvaksynnanEhtoRepository.hyvaksynnanEhtoHakukohteessa(hakemusOid, hakukohdeOid))

    auditLogRead(hakemusOid, Set(hakukohdeOid))

    response match {
      case Some((ehto, lastModified)) =>
        Ok(body = ehto, headers = Map("Last-Modified" -> createLastModifiedHeader(lastModified)))
      case None =>
        NotFound(body = Map("error" -> "Not Found"))
    }
  }

  val hyvaksynnanEhdotHakemukselleSwagger: OperationBuilder =
    (apiOperation[HakemuksenEhdotJaHistoriat]("hyvaksynnanEhdotHakemukselle")
      summary "Hakemuksen hyväksynnän ehto ja tilahistoriat hakemukselle"
      parameter pathParam[String]("hakemusOid").description("Hakemuksen OID").required
      tags "hyvaksynnan-ehto")
  get("/hakemukselle/:hakemusOid", operation(hyvaksynnanEhdotHakemukselleSwagger)) {
    contentType = formats("json")
    val hakemusOid = parseHakemusOid.fold(throw _, x => x)

    implicit val authenticated: Authenticated = authenticate

    val hakutoiveet: Set[HakukohdeOid] = hakemusRepository.findHakemus(hakemusOid).fold(throw _, x => x)
      .toiveet.map(_.oid).toSet
    authorize(hakutoiveet, Set(Role.ATARU_HAKEMUS_READ, Role.ATARU_HAKEMUS_CRUD, Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD))

    val result = hakutoiveet.map(toive => {
      val (suoraEhto, ehtoJonoille: Map[ValintatapajonoOid, HyvaksynnanEhto], lastModified) = {
        try {
          val ehto = hyvaksynnanEhtoRepository.runBlocking(
            hyvaksynnanEhtoRepository.hyvaksynnanEhtoHakukohteessa(hakemusOid, toive))
          (ehto.map(e => e._1), Map.empty, ehto.map(e => e._2))
        } catch {
          case _: GoneException =>
            val ehdotJonoittain: Seq[(ValintatapajonoOid, HyvaksynnanEhto, Instant)] = hyvaksynnanEhtoRepository.runBlocking(
              hyvaksynnanEhtoRepository.hyvaksynnanEhdotValintatapajonoissa(hakemusOid, toive))
            val lastModified: Option[Instant] = if (ehdotJonoittain.nonEmpty) Some(ehdotJonoittain.map(_._3).max) else None
            (None, ehdotJonoittain.map(t => t._1 -> t._2).toMap, lastModified)
          case e: Exception =>
            logger.error(s"Jokin meni pieleen hyväksynnän ehtojen haussa hakemuksen ${hakemusOid.toString} hakutoiveelle ${toive.toString}: $e")
            throw e
        }
      }
      val historia = hyvaksynnanEhtoRepository.runBlocking(
        hyvaksynnanEhtoRepository.hyvaksynnanEhtoHakukohteessaMuutoshistoria(hakemusOid, toive))

      HakutoiveenEhtoJaMuutoshistoria(toive, suoraEhto, ehtoJonoille, historia, lastModified.map(createLastModifiedHeader))
    }).toList

    val response = HakemuksenEhdotJaHistoriat(hakemusOid, result)

    auditLogRead(hakemusOid, hakutoiveet)

    response match {
      case result: HakemuksenEhdotJaHistoriat =>
        Ok(body = result)
    }
  }

  val hyvaksynnanEhdotValintatapajonoissaSwagger: OperationBuilder =
    (apiOperation[Map[ValintatapajonoOid, Map[HakemusOid, HyvaksynnanEhto]]]("hyvaksynnanEhdotValintatapajonoissa")
      summary "Hyväksynnän ehdot hakukohteen valintatapajonoissa"
      parameter pathParam[String]("hakukohdeOid").description("Hakukohteen OID").required
      tags "hyvaksynnan-ehto")
  get("/valintatapajonoissa/:hakukohdeOid", operation(hyvaksynnanEhdotValintatapajonoissaSwagger)) {
    contentType = formats("json")
    val hakukohdeOid = parseHakukohdeOid.fold(throw _, x => x)

    implicit val authenticated: Authenticated = authenticate
    authorize(hakukohdeOid, Set(Role.ATARU_HAKEMUS_READ, Role.ATARU_HAKEMUS_CRUD, Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD))

    val response = hyvaksynnanEhtoRepository.runBlocking(
      hyvaksynnanEhtoRepository.hyvaksynnanEhdotValintatapajonoissa(hakukohdeOid))

    response.foreach(r => auditLogRead(r._1, Set(hakukohdeOid)))

    response match {
      case Nil => Ok(body = Map.empty)
      case ehdot => Ok(
        body = ehdot.groupBy(_._2).mapValues(_.map(t => t._1 -> t._3).toMap),
        headers = Map("Last-Modified" -> createLastModifiedHeader(ehdot.map(_._4).max)))
    }
  }

  val hakemuksenHyvaksynnanEhdotValintatapajonoissaSwagger: OperationBuilder =
    (apiOperation[Map[ValintatapajonoOid, HyvaksynnanEhto]]("hakemuksenHyvaksynnanEhdotValintatapajonoissa")
      summary "Hakemuksen hyväksynnän ehdot hakukohteen valintatapajonoissa"
      parameter pathParam[String]("hakukohdeOid").description("Hakukohteen OID").required
      parameter pathParam[String]("hakemusOid").description("Hakemuksen OID").required
      tags "hyvaksynnan-ehto")
  get("/valintatapajonoissa/:hakukohdeOid/hakemus/:hakemusOid", operation(hakemuksenHyvaksynnanEhdotValintatapajonoissaSwagger)) {
    contentType = formats("json")
    val hakukohdeOid = parseHakukohdeOid.fold(throw _, x => x)
    val hakemusOid = parseHakemusOid.fold(throw _, x => x)

    implicit val authenticated: Authenticated = authenticate
    authorize(hakemusOid, hakukohdeOid, Set(Role.ATARU_HAKEMUS_READ, Role.ATARU_HAKEMUS_CRUD, Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD))

    val response = hyvaksynnanEhtoRepository.runBlocking(
      hyvaksynnanEhtoRepository.hyvaksynnanEhdotValintatapajonoissa(hakemusOid, hakukohdeOid))

    auditLogRead(hakemusOid, Set(hakukohdeOid))

    response match {
      case Nil => Ok(body = Map.empty)
      case ehdot => Ok(body = ehdot.map(t => t._1 -> t._2).toMap, headers = Map("Last-Modified" -> createLastModifiedHeader(ehdot.map(_._3).max)))
    }
  }

  val putHyvaksynnanEhtoHakukohteessaSwagger: OperationBuilder = (
    apiOperation[HyvaksynnanEhto]("muokkaaHyvaksynnanEhtoaHakukohteessa")
      summary "Muokkaa hyväksynnän ehtoa hakukohteessa"
      parameter pathParam[String]("hakukohdeOid").description("Hakukohteen OID").required
      parameter pathParam[String]("hakemusOid").description("Hakemuksen OID").required
      parameter headerParam[String]("If-Unmodified-Since").description(s"Aikaleima RFC 1123 määrittelemässä muodossa $RFC1123sample").optional
      parameter headerParam[String]("If-None-Match").description(s"'*' mikäli uuden tietueen tallennus").optional
      parameter bodyParam[HyvaksynnanEhto].description("Ehdollisen hyväksynnän ehto").required
      tags "hyvaksynnan-ehto")
  put("/hakukohteessa/:hakukohdeOid/hakemus/:hakemusOid", operation(putHyvaksynnanEhtoHakukohteessaSwagger)) {
    contentType = formats("json")
    val hakukohdeOid = parseHakukohdeOid.fold(throw _, x => x)
    val hakemusOid = parseHakemusOid.fold(throw _, x => x)
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
    authorize(hakemusOid, hakukohdeOid, Set(Role.ATARU_HAKEMUS_CRUD, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD))

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
      parameter pathParam[String]("hakukohdeOid").description("Hakukohteen OID").required
      parameter pathParam[String]("hakemusOid").description("Hakemuksen OID").required
      parameter headerParam[String]("If-Unmodified-Since").description(s"Aikaleima RFC 1123 määrittelemässä muodossa $RFC1123sample").required
      tags "hyvaksynnan-ehto")
  delete("/hakukohteessa/:hakukohdeOid/hakemus/:hakemusOid", operation(deleteHyvaksynnanEhtoHakukohteessaSwagger)) {
    contentType = formats("json")
    val hakukohdeOid = parseHakukohdeOid.fold(throw _, x => x)
    val hakemusOid = parseHakemusOid.fold(throw _, x => x)
    val ifUnmodifiedSince = parseIfUnmodifiedSince.fold(throw _, x => x)

    implicit val authenticated: Authenticated = authenticate
    authorize(hakemusOid, hakukohdeOid, Set(Role.ATARU_HAKEMUS_CRUD, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD))

    val ehto = hyvaksynnanEhtoRepository.runBlocking(
      hyvaksynnanEhtoRepository.deleteHyvaksynnanEhtoHakukohteessa(hakemusOid, hakukohdeOid, ifUnmodifiedSince))

    auditLogDelete(hakemusOid, hakukohdeOid, ehto)

    NoContent()
  }

  val hyvaksynnanEhtoHakukohteessaMuutoshistoriaSwagger: OperationBuilder =
    (apiOperation[List[Versio[HyvaksynnanEhto]]]("hyvaksynnanEhtoHakukohteessaMuutoshistoria")
      summary "Hakemuksen hyväksynnän ehto hakukohteessa -muutoshistoria"
      parameter pathParam[String]("hakukohdeOid").description("Hakukohteen OID").required
      parameter pathParam[String]("hakemusOid").description("Hakemuksen OID").required
      tags "hyvaksynnan-ehto")
  get("/muutoshistoria/hakukohteessa/:hakukohdeOid/hakemus/:hakemusOid", operation(hyvaksynnanEhtoHakukohteessaMuutoshistoriaSwagger)) {
    contentType = formats("json")
    val hakemusOid = parseHakemusOid.fold(throw _, x => x)
    val hakukohdeOid = parseHakukohdeOid.fold(throw _, x => x)

    implicit val authenticated: Authenticated = authenticate
    authorize(hakemusOid, hakukohdeOid, Set(Role.ATARU_HAKEMUS_READ, Role.ATARU_HAKEMUS_CRUD, Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD))

    val response = hyvaksynnanEhtoRepository.runBlocking(
      hyvaksynnanEhtoRepository.hyvaksynnanEhtoHakukohteessaMuutoshistoria(hakemusOid, hakukohdeOid))

    auditLogRead(hakemusOid, Set(hakukohdeOid))

    Ok(response)
  }

  private def authorize(hakukohdeOid: HakukohdeOid, roles: Set[Role])(implicit authenticated: Authenticated): Unit = {
    authorize(roles.toSeq: _*)
    val hakukohde = hakuService.getHakukohde(hakukohdeOid).fold(throw _, x => x)
    authorizer.checkAccessWithHakukohderyhmat(authenticated.session, hakukohde.organisaatioOiditAuktorisointiin, roles, hakukohdeOid).fold(throw _, x => x)
  }

  private def authorize(hakutoiveet: Set[HakukohdeOid], roles: Set[Role])(implicit authenticated: Authenticated): Unit = {
    authorize(roles.toSeq: _*)
    val organisaatiot = hakutoiveet.map(toive => hakuService.getHakukohde(toive).fold(throw _, x => x)).flatMap(_.organisaatioOiditAuktorisointiin)
    authorizer.checkAccessWithHakukohderyhmatForAtLeastOneHakukohde(authenticated.session, organisaatiot, roles, hakutoiveet).fold(throw _, x => x)
  }

  private def authorize(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, roles: Set[Role])(implicit authenticated: Authenticated): Unit = {
    authorize(roles.toSeq: _*)
    (for {
      hakemus <- hakemusRepository.findHakemus(hakemusOid).right
      hakutoiveCheck <- (if (hakemus.toiveet.exists(_.oid == hakukohdeOid)) {
        Right(hakemus)
      } else {
        Left(new IllegalArgumentException(s"Hakukohde $hakukohdeOid ei ole hakemuksen $hakemusOid hakutoive"))
      }).right
      hakukohteet <- MonadHelper.sequence(hakemus.toiveet.map(h => hakuService.getHakukohde(h.oid))).right
      authorized <- authorizer.checkAccessWithHakukohderyhmat(authenticated.session, hakukohteet.flatMap(_.organisaatioOiditAuktorisointiin).toSet, roles, hakukohdeOid).right
    } yield authorized).fold(throw _, x => x)
  }

  private def auditLogRead(hakemusOid: HakemusOid, hakukohdeOids: Set[HakukohdeOid])(implicit authenticated: Authenticated): Unit = {
    audit.log(
      auditInfo.user,
      HyvaksynnanEhtoLuku,
      new Target.Builder()
        .setField("hakemus", hakemusOid.toString)
        .setField("hakukohde", hakukohdeOids.map(_.toString).mkString(", "))
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
