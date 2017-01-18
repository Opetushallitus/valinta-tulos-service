package fi.vm.sade.valintatulosservice

import java.time.Instant
import java.util.concurrent.TimeUnit

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.security.{Role, Session}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValinnantulosRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

import scala.concurrent.duration.Duration

class ValinnantulosService(valinnantulosRepository: ValinnantulosRepository) extends Logging {

  def storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid: String,
                                               valinnantulokset: List[Valinnantulos],
                                               ifUnmodifiedSince: Instant,
                                               session: Session): List[ValinnantulosUpdateStatus] = {
    val vanhatValinnantulokset = getValinnantuloksetGroupedByHakemusOid(valintatapajonoOid)
    val tarjoajaOid = valinnantulosRepository.getTarjoajaForHakukohde(vanhatValinnantulokset.head._2._2.hakukohdeOid)
    valinnantulokset.map(uusiValinnantulos => {
      vanhatValinnantulokset.get(uusiValinnantulos.hakemusOid) match {
        case Some((_, vanhaValinnantulos)) if !uusiValinnantulos.hasChange(vanhaValinnantulos) => Right()
        case Some((lastModified, _)) if lastModified.isAfter(ifUnmodifiedSince) => {
          logger.warn(s"Hakemus ${uusiValinnantulos.hakemusOid} valintatapajonossa $valintatapajonoOid " +
            s"on muuttunut $lastModified lukemisajan $ifUnmodifiedSince jälkeen.")
          Left(ValinnantulosUpdateStatus(409, s"Not unmodified since ${ifUnmodifiedSince}", uusiValinnantulos.valintatapajonoOid, uusiValinnantulos.hakemusOid))
        }
        case Some((_, vanhaValinnantulos)) => validateMuutos(vanhaValinnantulos, uusiValinnantulos, session, tarjoajaOid) match {
          case x if x.isRight => updateValinnantulos(valintatapajonoOid, vanhaValinnantulos, uusiValinnantulos, session.personOid)
          case x if x.isLeft => x
        }
        case None => {
          logger.warn(s"Hakemuksen ${uusiValinnantulos.hakemusOid} valinnan tulosta ei löydy " +
            s"valintatapajonosta $valintatapajonoOid.")
          Left(ValinnantulosUpdateStatus(404, s"Not found", uusiValinnantulos.valintatapajonoOid, uusiValinnantulos.hakemusOid))
        }
      }
    }).map{
      case Left(x) => Some(x)
      case _ => None
    }.flatten
  }

  private def getValinnantuloksetGroupedByHakemusOid(valintatapajonoOid: String): Map[String, (Instant, Valinnantulos)] = {
    valinnantulosRepository.runBlocking(
      valinnantulosRepository.getValinnantuloksetForValintatapajono(valintatapajonoOid),
      Duration(1, TimeUnit.SECONDS)
    ).map(v => v._2.hakemusOid -> v).toMap
  }

  private def updateValinnantulos(valintatapajonoOid: String,
                                  vanha: Valinnantulos,
                                  uusi: Valinnantulos,
                                  muokkaaja: String): Either[ValinnantulosUpdateStatus, Unit] = {
    logger.info(s"Käyttäjä ${muokkaaja} muokkasi " +
      s"hakemuksen ${uusi.hakemusOid} valinnan tulosta valintatapajonossa $valintatapajonoOid " +
      s"vastaanottotilasta ${vanha.vastaanottotila} tilaan ${uusi.vastaanottotila} ja " +
      s"ilmoittautumistilasta ${vanha.ilmoittautumistila} tilaan ${uusi.ilmoittautumistila}.")
    valinnantulosRepository.runBlocking(
      valinnantulosRepository.storeIlmoittautuminen(vanha.henkiloOid,
        Ilmoittautuminen(vanha.hakukohdeOid, uusi.ilmoittautumistila, muokkaaja, "selite")),
      Duration(1, TimeUnit.SECONDS)
    )
    Right()
  }

  private def validateMuutos(vanha: Valinnantulos, uusi: Valinnantulos, session: Session, tarjoajaOid: String): Either[ValinnantulosUpdateStatus, Unit] = {
    for {
      valinnantila <- validateValinnantila(vanha, uusi, session, tarjoajaOid).right
      ehdollisestiHyvaksyttavissa <- validateEhdollisestiHyvaksyttavissa(vanha, uusi, session, tarjoajaOid).right
      julkaistavissa <- validateJulkaistavissa(vanha, uusi, session, tarjoajaOid).right
      hyvaksyttyVarasijalta <- validateHyvaksyttyVarasijalta(vanha, uusi, session, tarjoajaOid).right
      hyvaksyPeruuntunut <- validateHyvaksyPeruuntunut(vanha, uusi, session, tarjoajaOid).right
      //TODO vastaanotto <- validateVastaanotto(vanha, uusi, session, tarjoajaOid).right
      ilmoittautumistila <- validateIlmoittautumistila(vanha, uusi, session, tarjoajaOid).right
    } yield ilmoittautumistila
  }

  private def validateValinnantila(vanha: Valinnantulos, uusi: Valinnantulos, session: Session, tarjoajaOid: String): Either[ValinnantulosUpdateStatus, Unit] =
    uusi.valinnantila match {
      case vanha.valinnantila => Right()
      case _ => Left(ValinnantulosUpdateStatus(403, s"Valinnantilan muutos ei ole sallittu", uusi.valintatapajonoOid, uusi.hakemusOid))
    }

  private def validateEhdollisestiHyvaksyttavissa(vanha: Valinnantulos, uusi: Valinnantulos, session: Session, tarjoajaOid: String): Either[ValinnantulosUpdateStatus, Unit] =
    uusi.ehdollisestiHyvaksyttavissa match {
      case vanha.ehdollisestiHyvaksyttavissa => Right()
      case _ if allowOrgUpdate(session, tarjoajaOid) => Right()
      case _ => Left(ValinnantulosUpdateStatus(403, s"Ehdollisesti hyväksyttävissä -arvon muuttaminen ei ole sallittua", uusi.valintatapajonoOid, uusi.hakemusOid))
    }

  private def validateJulkaistavissa(vanha: Valinnantulos, uusi: Valinnantulos, session: Session, tarjoajaOid: String): Either[ValinnantulosUpdateStatus, Unit] =
    (uusi.julkaistavissa, uusi.vastaanottotila) match {
      case (vanha.julkaistavissa, _) => Right()
      case (false, vastaanotto) if List(MerkitseMyohastyneeksi, Poista).contains(vastaanotto) => Right()
      case (true, _) => Right()
      case (_, _) => Left(ValinnantulosUpdateStatus(403, s"Julkaistavissa-arvon muuttaminen ei ole sallittua", uusi.valintatapajonoOid, uusi.hakemusOid))
  }

  private def validateHyvaksyttyVarasijalta(vanha: Valinnantulos, uusi: Valinnantulos, session: Session, tarjoajaOid: String): Either[ValinnantulosUpdateStatus, Unit] =
    (uusi.hyvaksyttyVarasijalta, uusi.valinnantila) match {
      case (vanha.hyvaksyttyVarasijalta, _) => Right()
      case (true, Varalla) if (allowOphUpdate(session) || allowMusiikkiUpdate(session, tarjoajaOid)) => Right()
      case (false, _) if (allowOphUpdate(session) || allowMusiikkiUpdate(session, tarjoajaOid)) => Right()
      case (_, _) => Left(ValinnantulosUpdateStatus(403, s"Hyväksytty varasijalta -arvon muuttaminen ei ole sallittua", uusi.valintatapajonoOid, uusi.hakemusOid))
  }

  private def validateHyvaksyPeruuntunut(vanha: Valinnantulos, uusi: Valinnantulos, session: Session, tarjoajaOid: String): Either[ValinnantulosUpdateStatus, Unit] =
    (uusi.hyvaksyPeruuntunut, uusi.valinnantila, uusi.julkaistavissa) match {
      case (vanha.hyvaksyPeruuntunut, _, _) => Right()
      case (_, Hyvaksytty, false) if vanha.hyvaksyPeruuntunut && allowPeruuntuneidenHyvaksynta(session) => Right()
      case (_, Peruuntunut, false) if allowPeruuntuneidenHyvaksynta(session) => Right()
      case (_, _, _) => Left(ValinnantulosUpdateStatus(403, s"HyväksyPeruuntunut value cannot be changed", uusi.valintatapajonoOid, uusi.hakemusOid))
  }

  private def validateIlmoittautumistila(vanha: Valinnantulos, uusi: Valinnantulos, session: Session, tarjoajaOid: String): Either[ValinnantulosUpdateStatus, Unit] =
    (uusi.ilmoittautumistila, uusi.vastaanottotila) match {
      case (vanha.ilmoittautumistila, _) => Right()
      case (_, VastaanotaSitovasti) => Right()
      case (_, _) => Left(ValinnantulosUpdateStatus(403, s"Ilmoittautumista ei voida muuttaa", uusi.valintatapajonoOid, uusi.hakemusOid))
  }

  private def allowPeruuntuneidenHyvaksynta(session:Session) = session.hasAnyRole(Set(Role.SIJOITTELU_PERUUNTUNEIDEN_HYVAKSYNTA_OPH))
  private def allowOphUpdate(session:Session) = session.hasAnyRole(Set(Role.SIJOITTELU_CRUD_OPH))
  private def allowOrgUpdate(session:Session, tarjoajaOid:String) = session.hasAnyRole(Set(Role.sijoitteluCrudOrg(tarjoajaOid), Role.sijoitteluUpdateOrg(tarjoajaOid)))
  private def allowMusiikkiUpdate(session:Session, tarjoajaOid:String) = session.hasAnyRole(Set(Role.musiikkialanValintaToinenAste(tarjoajaOid)))

}
