package fi.vm.sade.valintatulosservice


import fi.vm.sade.auditlog.{Audit, Changes, Operation, Target}
import fi.vm.sade.security.{EiRajausta, OrganisaatiorajausResolver, RajattuOrganisaatioihin}
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.HakijaPaginationObject
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.domain.Hakemuksentulos
import fi.vm.sade.valintatulosservice.security.{Role, Session}
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

  private val organisaatiorajausKaytossaKuvaus: String =
    "Kun asetus valinta-tulos-service.cas-haku.organisaatiorajaus.enabled on päällä, tulos rajataan kutsujan " +
      "organisaatioiden (aliorganisaatioineen) ja hakukohderyhmien hakukohteisiin. Juuriorganisaation oikeudella " +
      "tulosta ei rajata. "

  override protected def hyvaksyttyjenListauksenOrganisaatiorajausKuvaus: String =
    organisaatiorajausKaytossaKuvaus + "Muiden organisaatioiden hakutoiveet poistetaan hakijoilta, ja hakija " +
      "palautetaan vain, jos hänellä on hyväksyntä jäljelle jäävissä hakutoiveissa."

  override protected def hakemuksenTuloksenOrganisaatiorajausKuvaus: String =
    organisaatiorajausKaytossaKuvaus + "Muiden organisaatioiden hakutoiveet poistetaan, ja jos yhtään hakutoivetta " +
      "ei jää jäljelle, vastataan 404. "

  override protected def rajaaOrganisaatioon(hakuOid: HakuOid, tulokset: HakijaPaginationObject, vainHyvaksytyt: Boolean): HakijaPaginationObject =
    organisaatiorajauksella(hakuOid, tulokset) { (salliHakukohde, session) =>
      val rajatut = SijoittelunTulostenOrganisaatiorajaus.rajaa(tulokset, salliHakukohde, vainHyvaksytyt)
      val ennen = Option(tulokset.getResults).map(_.size).getOrElse(0)
      val jalkeen = rajatut.getResults.size
      if (ennen != jalkeen) {
        logger.info(s"Organisaatiorajaus pudotti haun $hakuOid tuloksista ${ennen - jalkeen} hakijaa $ennen hakijasta käyttäjälle ${session.personOid}")
      }
      rajatut
    }

  override protected def rajaaOrganisaatioon(tulos: Hakemuksentulos): Option[Hakemuksentulos] =
    organisaatiorajauksella(tulos.hakuOid, Option(tulos)) { (salliHakukohde, session) =>
      val rajattu = HakemuksenTuloksenOrganisaatiorajaus.rajaa(tulos, salliHakukohde)
      val ennen = tulos.hakutoiveet.size
      val jalkeen = rajattu.map(_.hakutoiveet.size).getOrElse(0)
      if (ennen != jalkeen) {
        logger.info(s"Organisaatiorajaus pudotti hakemuksen ${tulos.hakemusOid} tuloksesta ${ennen - jalkeen} hakutoivetta $ennen hakutoiveesta käyttäjälle ${session.personOid}")
      }
      rajattu
    }

  /**
   * Suorittaa rajauksen, jos se on käytössä eikä kutsujalla ole juuriorganisaation oikeutta. Rajaukselle annetaan
   * funktio, joka kertoo, saako kutsuja nähdä haun hakukohteen tulokset. Hakukohteet, joiden organisaatioita ei
   * saada selville, evätään, ellei hakukohderyhmä salli niitä.
   */
  private def organisaatiorajauksella[T](hakuOid: HakuOid, tulokset: T)(rajaa: (HakukohdeOid => Boolean, Session) => T): T = {
    if (!appConfig.settings.casHakuOrganisaatiorajausEnabled) {
      tulokset
    } else {
      val session = authenticate.session
      organisaatiorajausResolver.resolve(session, organisaatiorajauksenRoolit) match {
        case EiRajausta => tulokset
        case rajaus: RajattuOrganisaatioihin =>
          val hakukohteidenOrganisaatiot = hakuService.getHaunHakukohteidenOrganisaatiotAuktorisointiin(hakuOid).fold(
            t => throw new IllegalStateException(s"Haun $hakuOid hakukohteiden haku organisaatiorajausta varten epäonnistui", t),
            x => x)
          val tuntemattomat = scala.collection.mutable.Set.empty[HakukohdeOid]
          val rajatut = rajaa(
            oid => hakukohteidenOrganisaatiot.get(oid) match {
              case Some(organisaatiot) => rajaus.salliiHakukohteen(oid, organisaatiot)
              case None =>
                tuntemattomat += oid
                // Tuntemattoman hakukohteen voi silti sallia hakukohderyhmän perusteella
                rajaus.salliiHakukohteen(oid, Set.empty)
            },
            session)
          if (tuntemattomat.nonEmpty) {
            logger.warn(s"Haun $hakuOid tuloksissa on hakukohteita, joiden organisaatioita ei saatu selville, ne evätään organisaatiorajauksessa: ${tuntemattomat.mkString(", ")}")
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
