package fi.vm.sade.valintatulosservice


import fi.vm.sade.auditlog.{Audit, Changes, Operation, Target}
import fi.vm.sade.security.{EiRajausta, OrganisaatiorajausResolver, RajattuOrganisaatioihin}
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.HakijaPaginationObject
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.streamingresults.{HakemustenTulosHakuLock, StreamingValintatulosService}
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, HakuOid}
import org.scalatra.swagger._


object PublicValintatulosServlet {
  private val authenticatedAttribute = "fi.vm.sade.valintatulosservice.PublicValintatulosServlet.authenticated"
}

class PublicValintatulosServlet(audit: Audit,
                                valintatulosService: ValintatulosService,
                                streamingValintatulosService: StreamingValintatulosService,
                                vastaanottoService: VastaanottoService,
                                ilmoittautumisService: IlmoittautumisService,
                                valintarekisteriDb: ValintarekisteriDb,
                                val sessionRepository: SessionRepository,
                                hakemustenTulosHakuLock: HakemustenTulosHakuLock,
                                hakuService: HakuService,
                                organisaatiorajausResolver: OrganisaatiorajausResolver) (override implicit val swagger: Swagger, appConfig: VtsAppConfig)
  extends ValintatulosServlet(valintatulosService,
    streamingValintatulosService,
    vastaanottoService,
    ilmoittautumisService,
    valintarekisteriDb,
    hakemustenTulosHakuLock,
  "valintatulos-public")(swagger, appConfig) with CasAuthenticatedServlet {

  protected val applicationDescription = "Julkinen valintatulosten REST API"

  /**
   * Roolit, joiden organisaatioliitännäiset variantit määräävät kutsujan organisaatiorajauksen. Sama rooli, jota
   * /cas/haku-polun CAS-suodatin edellyttää.
   */
  private val organisaatiorajauksenRoolit: Set[Role] = appConfig.securityContext.requiredRoles

  override protected def rajaaOrganisaatioon(hakuOid: HakuOid, tulokset: HakijaPaginationObject, vainHyvaksytyt: Boolean): HakijaPaginationObject = {
    if (!appConfig.settings.casHakuOrganisaatiorajausEnabled) {
      tulokset
    } else {
      implicit val authenticated: Authenticated = authenticate
      organisaatiorajausResolver.resolve(authenticated.session, organisaatiorajauksenRoolit) match {
        case EiRajausta => tulokset
        case rajaus: RajattuOrganisaatioihin =>
          val hakukohteidenOrganisaatiot = hakuService.getHaunHakukohteidenOrganisaatiotAuktorisointiin(hakuOid).fold(
            t => throw new IllegalStateException(s"Haun $hakuOid hakukohteiden haku organisaatiorajausta varten epäonnistui", t),
            x => x)
          val tuntemattomat = scala.collection.mutable.Set.empty[HakukohdeOid]
          val rajatut = SijoittelunTulostenOrganisaatiorajaus.rajaa(
            tulokset,
            oid => hakukohteidenOrganisaatiot.get(oid) match {
              case Some(organisaatiot) => rajaus.salliiHakukohteen(oid, organisaatiot)
              case None =>
                tuntemattomat += oid
                // Tuntemattoman hakukohteen voi silti sallia hakukohderyhmän perusteella
                rajaus.salliiHakukohteen(oid, Set.empty)
            },
            vainHyvaksytyt)
          if (tuntemattomat.nonEmpty) {
            logger.warn(s"Haun $hakuOid tuloksissa on hakukohteita, joiden organisaatioita ei saatu selville, ne evätään organisaatiorajauksessa: ${tuntemattomat.mkString(", ")}")
          }
          val ennen = Option(tulokset.getResults).map(_.size).getOrElse(0)
          val jalkeen = rajatut.getResults.size
          if (ennen != jalkeen) {
            logger.info(s"Organisaatiorajaus pudotti haun $hakuOid tuloksista ${ennen - jalkeen} hakijaa $ennen hakijasta käyttäjälle ${authenticated.session.personOid}")
          }
          rajatut
      }
    }
  }

  // Sama pyyntö autentikoidaan sekä rajausta että auditlokitusta varten; haetaan sessio kannasta vain kerran
  override protected def authenticate: Authenticated = {
    Option(request.getAttribute(PublicValintatulosServlet.authenticatedAttribute)).map(_.asInstanceOf[Authenticated]).getOrElse {
      val a = super.authenticate
      request.setAttribute(PublicValintatulosServlet.authenticatedAttribute, a)
      a
    }
  }

  override def auditLog(auditParams: Map[String, String], auditOperation: Operation): Unit = {
    implicit val authenticated = authenticate
    val credentials: AuditInfo = auditInfo
    val builder= new Target.Builder()
    auditParams.foreach(p => builder.setField(p._1,p._2))
    audit.log(auditInfo.user, auditOperation, builder.build(), new Changes.Builder().build())
  }

  override def auditLogChanged(auditParams: Map[String, String], auditOperation: Operation, changedParams: Map[String, String], changeOperation: String): Unit = {
    implicit val authenticated = authenticate
    val credentials: AuditInfo = auditInfo
    val builder = new Target.Builder()
    auditParams.foreach(p => builder.setField(p._1,p._2))
    val changesBuilder = new Changes.Builder()

    if (changeOperation.equals("added")) {
      changedParams.foreach(p => changesBuilder.added(p._1, p._2))
    } else if (changeOperation.equals("removed")) {
      changedParams.foreach(p => changesBuilder.removed(p._1, p._2))
    } else {
      changedParams.foreach(p => changesBuilder.updated(p._1, None.toString, p._2))
    }
    changedParams.foreach(p => changesBuilder.added(p._1, p._2))
    audit.log(auditInfo.user, auditOperation, builder.build(), changesBuilder.build())
  }
}
