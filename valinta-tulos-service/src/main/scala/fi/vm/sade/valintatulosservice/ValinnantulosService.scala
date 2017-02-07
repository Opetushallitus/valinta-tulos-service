package fi.vm.sade.valintatulosservice

import java.time.Instant

import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, OhjausparametritService}
import fi.vm.sade.valintatulosservice.security.{Role, Session}
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValinnantulosRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

import scala.util.{Failure, Success, Try}

class ValinnantulosService(valinnantulosRepository: ValinnantulosRepository,
                           authorizer:OrganizationHierarchyAuthorizer,
                           hakuService: HakuService,
                           ohjausparametritService: OhjausparametritService,
                           appConfig: VtsAppConfig) extends Logging {

  def getValinnantuloksetForValintatapajono(valintatapajonoOid: String): List[(Instant, Valinnantulos)] = {
    valinnantulosRepository.getValinnantuloksetForValintatapajono(valintatapajonoOid)
  }

  def storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid: String,
                                               valinnantulokset: List[Valinnantulos],
                                               ifUnmodifiedSince: Instant,
                                               session: Session): List[ValinnantulosUpdateStatus] = {
    val vanhatValinnantulokset = getValinnantuloksetGroupedByHakemusOid(valintatapajonoOid)
    val tarjoajaOid = vanhatValinnantulokset.headOption.map(x => valinnantulosRepository.getTarjoajaForHakukohde(x._2._2.hakukohdeOid)).getOrElse("")
    val hakuOid = vanhatValinnantulokset.headOption.map(x => valinnantulosRepository.getHakuForHakukohde(x._2._2.hakukohdeOid)).getOrElse("")

    if(!vanhatValinnantulokset.isEmpty) {
      authorizer.checkAccess(session, tarjoajaOid, List(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)) match {
        case Failure(e) => {
          logger.warn(s"Käyttäjällä ${session.personOid} ei ole oikeuksia päivittää valinnantuloksia valintatapajonossa $valintatapajonoOid")
          throw e
        }
        case Success(x) => x
      }
    }

    valinnantulokset.map(uusiValinnantulos => {
      vanhatValinnantulokset.get(uusiValinnantulos.hakemusOid) match {
        case Some((_, vanhaValinnantulos)) if !uusiValinnantulos.hasChanged(vanhaValinnantulos) => Right()
        case Some((lastModified, _)) if lastModified.isAfter(ifUnmodifiedSince) => {
          logger.warn(s"Hakemus ${uusiValinnantulos.hakemusOid} valintatapajonossa $valintatapajonoOid " +
            s"on muuttunut $lastModified lukemisajan $ifUnmodifiedSince jälkeen.")
          Left(ValinnantulosUpdateStatus(409, s"Hakemus on muuttunut lukemisajan ${ifUnmodifiedSince} jälkeen", uusiValinnantulos.valintatapajonoOid, uusiValinnantulos.hakemusOid))
        }
        case Some((_, vanhaValinnantulos)) => validateMuutos(vanhaValinnantulos, uusiValinnantulos, session, tarjoajaOid, hakuOid) match {
          case x if x.isRight => updateValinnantulos(valintatapajonoOid, vanhaValinnantulos, uusiValinnantulos, session.personOid, ifUnmodifiedSince)
          case x if x.isLeft => x
        }
        case None => {
          logger.warn(s"Hakemuksen ${uusiValinnantulos.hakemusOid} valinnan tulosta ei löydy " +
            s"valintatapajonosta $valintatapajonoOid.")
          Left(ValinnantulosUpdateStatus(404, s"Valinnantulosta ei löydy", uusiValinnantulos.valintatapajonoOid, uusiValinnantulos.hakemusOid))
        }
      }
    }).map{
      case Left(x) => Some(x)
      case _ => None
    }.flatten
  }

  private def getValinnantuloksetGroupedByHakemusOid(valintatapajonoOid: String): Map[String, (Instant, Valinnantulos)] = {
    valinnantulosRepository.getValinnantuloksetForValintatapajono(valintatapajonoOid).map(v => v._2.hakemusOid -> v).toMap
  }

  private def updateValinnantulos(valintatapajonoOid: String,
                                  vanha: Valinnantulos,
                                  uusi: Valinnantulos,
                                  muokkaaja: String,
                                  ifUnmodifiedSince: Instant): Either[ValinnantulosUpdateStatus, Unit] = {
    logger.info(s"Käyttäjä ${muokkaaja} muokkasi " +
      s"hakemuksen ${uusi.hakemusOid} valinnan tulosta valintatapajonossa $valintatapajonoOid " +
      s"vastaanottotilasta ${vanha.vastaanottotila} tilaan ${uusi.vastaanottotila} ja " +
      s"ilmoittautumistilasta ${vanha.ilmoittautumistila} tilaan ${uusi.ilmoittautumistila}.")

    val operations = List(
      Option(uusi.hasOhjausChanged(vanha)).collect{ case true => valinnantulosRepository.storeValinnantuloksenOhjaus(
        uusi.getValinnantuloksenOhjauksenMuutos(vanha, muokkaaja, "Virkailijan tallennus"), Some(ifUnmodifiedSince))},
      Option(uusi.ilmoittautumistila != vanha.ilmoittautumistila).collect{ case true => valinnantulosRepository.storeIlmoittautuminen(
        vanha.henkiloOid, Ilmoittautuminen(vanha.hakukohdeOid, uusi.ilmoittautumistila, muokkaaja, "Virkailijan tallennus"), Some(ifUnmodifiedSince))}
    ).flatten

    Try(valinnantulosRepository.runBlockingTransactionally(
      slick.dbio.DBIO.seq(operations: _*)
    )) match {
      case Success(_) => Right()
      case Failure(t) =>
        logger.warn(s"Valinnantuloksen $uusi tallennus epäonnistui", t)
        Left(ValinnantulosUpdateStatus(500, s"Valinnantuloksen tallennus epäonnistui", valintatapajonoOid, uusi.hakemusOid))
    }
  }

  private def validateMuutos(vanha: Valinnantulos, uusi: Valinnantulos, session: Session, tarjoajaOid: String, hakuOid:String): Either[ValinnantulosUpdateStatus, Unit] = {
    for {
      valinnantila <- validateValinnantila(vanha, uusi, session, tarjoajaOid).right
      ehdollisestiHyvaksyttavissa <- validateEhdollisestiHyvaksyttavissa(vanha, uusi, session, tarjoajaOid).right
      julkaistavissa <- validateJulkaistavissa(vanha, uusi, session, tarjoajaOid, hakuOid).right
      hyvaksyttyVarasijalta <- validateHyvaksyttyVarasijalta(vanha, uusi, session, tarjoajaOid).right
      hyvaksyPeruuntunut <- validateHyvaksyPeruuntunut(vanha, uusi, session, tarjoajaOid).right
      //TODO vastaanotto <- validateVastaanotto(vanha, uusi, session, tarjoajaOid).right
      ilmoittautumistila <- validateIlmoittautumistila(vanha, uusi, session, tarjoajaOid).right
    } yield ilmoittautumistila
  }

  //TODO different statuses for authorization fails / conflicts

  private def validateValinnantila(vanha: Valinnantulos, uusi: Valinnantulos, session: Session, tarjoajaOid: String): Either[ValinnantulosUpdateStatus, Unit] =
    uusi.valinnantila match {
      case vanha.valinnantila => Right()
      case _ => Left(ValinnantulosUpdateStatus(403, s"Valinnantilan muutos ei ole sallittu", uusi.valintatapajonoOid, uusi.hakemusOid))
    }

  private def validateEhdollisestiHyvaksyttavissa(vanha: Valinnantulos, uusi: Valinnantulos, session: Session, tarjoajaOid: String): Either[ValinnantulosUpdateStatus, Unit] =
    uusi.ehdollisestiHyvaksyttavissa match {
      case None | vanha.ehdollisestiHyvaksyttavissa => Right()
      case _ if allowOrgUpdate(session, tarjoajaOid) => Right()
      case _ => Left(ValinnantulosUpdateStatus(401, s"Käyttäjällä ${session.personOid} ei ole oikeuksia hyväksyä ehdollisesti", uusi.valintatapajonoOid, uusi.hakemusOid))
    }

  private def validateJulkaistavissa(vanha: Valinnantulos, uusi: Valinnantulos, session: Session, tarjoajaOid: String, hakuOid:String): Either[ValinnantulosUpdateStatus, Unit] =
    (uusi.julkaistavissa, uusi.vastaanottotila) match {
      case (None, _) | (vanha.julkaistavissa, _) => Right()
      case (Some(false), vastaanotto) if List(MerkitseMyohastyneeksi, Poista).contains(vastaanotto) => Right()
      case (Some(false), _) => Left(ValinnantulosUpdateStatus(409, s"Valinnantulosta ei voida merkitä ei-julkaistavaksi, koska sillä on vastaanotto", uusi.valintatapajonoOid, uusi.hakemusOid))
      case (Some(true), _) if allowJulkaistavissaUpdate(session, hakuOid) => Right()
      case (_, _) => Left(ValinnantulosUpdateStatus(401, s"Käyttäjällä ${session.personOid} ei ole oikeuksia julkaista valinnantulosta", uusi.valintatapajonoOid, uusi.hakemusOid))
  }

  private def allowJulkaistavissaUpdate(session:Session, hakuOid:String): Boolean = {
    def ophCrudAccess() = authorizer.checkAccess(session, appConfig.settings.rootOrganisaatioOid, List(Role.SIJOITTELU_CRUD)).isSuccess

    def valintaesitysHyvaksyttavissa(ohjausparametrit: Ohjausparametrit) = ohjausparametrit.valintaesitysHyvaksyttavissa match {
      case None => ophCrudAccess
      case Some(valintaesitysHyvaksyttavissa) if valintaesitysHyvaksyttavissa.isAfterNow => ophCrudAccess
      case Some(_) => true
    }

    def ohjausparametrit = ohjausparametritService.ohjausparametrit(hakuOid).right.toOption match {
      case None => throw new RuntimeException(s"Haulle ${hakuOid} ei löydy ohjausparametreja.")
      case Some(ohjausparametritOption) if ohjausparametritOption.isEmpty => true
      case Some(ohjausparametritOption) => valintaesitysHyvaksyttavissa(ohjausparametritOption.get)
    }

    def korkeakouluhaku() = hakuService.getHaku(hakuOid).right.toOption match {
      case None => throw new RuntimeException(s"Hakua ${hakuOid} ei löytynyt Tarjonnasta.")
      case Some(haku) if haku.korkeakoulu => true
      case Some(haku) => ohjausparametrit
    }

    korkeakouluhaku
  }

  private def validateHyvaksyttyVarasijalta(vanha: Valinnantulos, uusi: Valinnantulos, session: Session, tarjoajaOid: String): Either[ValinnantulosUpdateStatus, Unit] =
    (uusi.hyvaksyttyVarasijalta, uusi.valinnantila) match {
      case (None, _) | (vanha.hyvaksyttyVarasijalta, _) => Right()
      case (Some(true), Varalla) if (allowOphUpdate(session) || allowMusiikkiUpdate(session, tarjoajaOid)) => Right()
      case (Some(true), x) if x != Varalla => Left(ValinnantulosUpdateStatus(409, s"Ei voida hyväksyä varasijalta", uusi.valintatapajonoOid, uusi.hakemusOid))
      case (Some(false), _) if (allowOphUpdate(session) || allowMusiikkiUpdate(session, tarjoajaOid)) => Right()
      case (_, _) => Left(ValinnantulosUpdateStatus(401, s"Käyttäjällä ${session.personOid} ei ole oikeuksia hyväksyä varasijalta", uusi.valintatapajonoOid, uusi.hakemusOid))
  }

  private def validateHyvaksyPeruuntunut(vanha: Valinnantulos, uusi: Valinnantulos, session: Session, tarjoajaOid: String): Either[ValinnantulosUpdateStatus, Unit] =
    (uusi.hyvaksyPeruuntunut, uusi.valinnantila, isJulkaistavissa(vanha, uusi)) match {
      case (None, _, _) | (vanha.hyvaksyPeruuntunut, _, _) => Right()
      case (_, Hyvaksytty, false) if vanha.hyvaksyPeruuntunut == Some(true) => allowPeruuntuneidenHyvaksynta(session, tarjoajaOid, uusi)
      case (_, Peruuntunut, false) => allowPeruuntuneidenHyvaksynta(session, tarjoajaOid, uusi)
      case (_, _, _) => Left(ValinnantulosUpdateStatus(409, s"Hyväksy peruuntunut -arvoa ei voida muuttaa valinnantulokselle", uusi.valintatapajonoOid, uusi.hakemusOid))
  }

  private def isJulkaistavissa(vanha: Valinnantulos, uusi: Valinnantulos): Boolean =
    (uusi.julkaistavissa, vanha.julkaistavissa) match {
      case (None, Some(true)) | (Some(true), _) => true
      case (_, _) => false
  }

  private def validateIlmoittautumistila(vanha: Valinnantulos, uusi: Valinnantulos, session: Session, tarjoajaOid: String): Either[ValinnantulosUpdateStatus, Unit] =
    (uusi.ilmoittautumistila, uusi.vastaanottotila) match {
      case (vanha.ilmoittautumistila, _) => Right()
      case (_, VastaanotaSitovasti) => Right()
      case (_, _) => Left(ValinnantulosUpdateStatus(409, s"Ilmoittautumista ei voida muuttaa, koska vastaanotto ei ole sitova", uusi.valintatapajonoOid, uusi.hakemusOid))
  }

  private def allowPeruuntuneidenHyvaksynta(session:Session, tarjoajaOid:String, uusi: Valinnantulos): Either[ValinnantulosUpdateStatus, Unit] =
    authorizer.checkAccess(session, tarjoajaOid, List(Role.SIJOITTELU_PERUUNTUNEIDEN_HYVAKSYNTA_OPH)) match {
      case Failure(e) => Left(ValinnantulosUpdateStatus(401, s"Käyttäjällä ${session.personOid} ei ole oikeuksia hyväksyä peruuntunutta", uusi.valintatapajonoOid, uusi.hakemusOid))
      case Success(_) => Right()
    }

  private def allowOphUpdate(session:Session) = session.hasAnyRole(Set(Role.SIJOITTELU_CRUD_OPH))
  private def allowOrgUpdate(session:Session, tarjoajaOid:String) = session.hasAnyRole(Set(Role.sijoitteluCrudOrg(tarjoajaOid), Role.sijoitteluUpdateOrg(tarjoajaOid)))
  private def allowMusiikkiUpdate(session:Session, tarjoajaOid:String) = session.hasAnyRole(Set(Role.musiikkialanValintaToinenAste(tarjoajaOid)))

}
