package fi.vm.sade.valintatulosservice.valinnantulos

import java.time.Instant

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.sijoittelu.domain.{EhdollisenHyvaksymisenEhtoKoodi, ValintatuloksenTila}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.ohjausparametrit.Ohjausparametrit
import fi.vm.sade.valintatulosservice.security.{Role, Session}
import fi.vm.sade.valintatulosservice.tarjonta.Haku
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.{AuditInfo, ValinnantuloksenMuokkaus}
import slick.dbio.DBIO

import scala.concurrent.ExecutionContext.Implicits.global

class SijoittelunValinnantulosStrategy(auditInfo: AuditInfo,
                                       tarjoajaOids: Set[String],
                                       haku: Haku,
                                       hakukohdeOid: HakukohdeOid,
                                       ohjausparametrit: Option[Ohjausparametrit],
                                       authorizer: OrganizationHierarchyAuthorizer,
                                       appConfig: VtsAppConfig,
                                       valinnantulosRepository: ValinnantulosRepository with HakijaVastaanottoRepository,
                                       ifUnmodifiedSince: Instant,
                                       audit: Audit) extends ValinnantulosStrategy with Logging {
  private val session = auditInfo.session._2

  lazy val vastaanottoValidator = new SijoittelunVastaanottoValidator(haku, hakukohdeOid, ohjausparametrit, valinnantulosRepository)

  def hasChange(uusi:Valinnantulos, vanha:Valinnantulos) = (uusi.hasChanged(vanha) || uusi.hasOhjausChanged(vanha) || uusi.hasEhdollisenHyvaksynnanEhtoChanged(vanha))

  def validate(uusi: Valinnantulos, vanhaOpt: Option[Valinnantulos], ifUnmodifiedSince: Option[Instant]): DBIO[Either[ValinnantulosUpdateStatus, Unit]] = {
    if (vanhaOpt.isEmpty) {
      logger.warn(s"Hakemuksen ${uusi.hakemusOid} valinnan tulosta ei löydy " +
        s"valintatapajonosta ${uusi.valintatapajonoOid}.")
      DBIO.successful(Left(ValinnantulosUpdateStatus(404, s"Valinnantulosta ei löydy", uusi.valintatapajonoOid, uusi.hakemusOid)))
    } else {
      val vanha = vanhaOpt.get

      def validateMuutos(): Either[ValinnantulosUpdateStatus, Unit] = {
        for {
          valinnantila <- validateValinnantila().right
          vastaanottotila <- validateVastaanottoTila().right
          julkaistavissa <- validateJulkaistavissa().right
          _ <- validateEhdollisestiHyvaksytty.right
          hyvaksyttyVarasijalta <- validateHyvaksyttyVarasijalta().right
          hyvaksyPeruuntunut <- validateHyvaksyPeruuntunut().right
          ilmoittautumistila <- validateIlmoittautumistila().right
        } yield ilmoittautumistila
      }

      def validateVastaanottoTila(): Either[ValinnantulosUpdateStatus, Unit] = (uusi.vastaanottotila, uusi.ilmoittautumistila) match {
        case  (_, _) if uusi.vastaanottotila != ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI && !List(EiIlmoittautunut,EiTehty).contains(uusi.ilmoittautumistila) =>
        //case  (_, Lasna | LasnaSyksy | LasnaKokoLukuvuosi) if (uusi.vastaanottotila != ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI) =>
          Left(ValinnantulosUpdateStatus(409, s"Vastaanottoa ei voi poistaa, koska ilmoittautuminen on tehty", uusi.valintatapajonoOid, uusi.hakemusOid))
        case (_, _) => Right()
      }

      def validateValinnantila() = uusi.valinnantila match {
        case vanha.valinnantila => Right()
        case _ => Left(ValinnantulosUpdateStatus(403, s"Valinnantilan muutos ei ole sallittu", uusi.valintatapajonoOid, uusi.hakemusOid))
      }

      def validateJulkaistavissa() = (uusi.julkaistavissa, uusi.vastaanottotila) match {
        case (Some(false), vastaanotto) if vastaanotto == ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI && !List(EiIlmoittautunut,EiTehty).contains(uusi.ilmoittautumistila) =>
          Left(ValinnantulosUpdateStatus(409, s"Valinnantulosta ei voida merkitä ei-julkaistavaksi, koska sen ilmoittautumistila on " + uusi.ilmoittautumistila, uusi.valintatapajonoOid, uusi.hakemusOid))
        case (vanha.julkaistavissa, _) => Right()
        case (Some(false), vastaanotto) if vastaanotto != ValintatuloksenTila.KESKEN =>
          Left(ValinnantulosUpdateStatus(409, s"Valinnantulosta ei voida merkitä ei-julkaistavaksi, koska sen vastaanottotila on $vastaanotto", uusi.valintatapajonoOid, uusi.hakemusOid))
        case (_, _) => allowJulkaistavissaUpdate()
      }

      def allowJulkaistavissaUpdate(): Either[ValinnantulosUpdateStatus, Unit] = {
        (haku, ohjausparametrit) match {
          case (h, _) if h.korkeakoulu => Right()
          case (_, None) => Right()
          case (_, Some(o)) if o.valintaesitysHyvaksyttavissa.exists(_.isBeforeNow) => Right()
          case (_, _) => authorizer.checkAccess(session, appConfig.settings.rootOrganisaatioOid, Set(Role.SIJOITTELU_CRUD)).left.map(_ =>
            ValinnantulosUpdateStatus(401, s"Käyttäjällä ${session.personOid} ei ole oikeuksia julkaista valinnantulosta", uusi.valintatapajonoOid, uusi.hakemusOid)
          )
        }
      }

      def validateEhdollisestiHyvaksytty: Either[ValinnantulosUpdateStatus, Unit] = (uusi.ehdollisestiHyvaksyttavissa, uusi.ehdollisenHyvaksymisenEhtoKoodi) match {
        case (Some(true), None) =>
          Left(ValinnantulosUpdateStatus(409, "Valinnantulos on ehdollisesti hyväksyttävissä, mutta ehtoa ei ole annettu.", uusi.valintatapajonoOid, uusi.hakemusOid))
        case (_, Some(EhdollisenHyvaksymisenEhtoKoodi.EHTO_MUU)) if uusi.ehdollisenHyvaksymisenEhtoFI.forall(_.isEmpty) =>
          Left(ValinnantulosUpdateStatus(409, "Valinnantuloksen ehdollisen hyväksynnän suomenkielistä ehtoa ei ole annettu.", uusi.valintatapajonoOid, uusi.hakemusOid))
        case (_, Some(EhdollisenHyvaksymisenEhtoKoodi.EHTO_MUU)) if uusi.ehdollisenHyvaksymisenEhtoSV.forall(_.isEmpty) =>
          Left(ValinnantulosUpdateStatus(409, "Valinnantuloksen ehdollisen hyväksynnän ruotsinkielistä ehtoa ei ole annettu.", uusi.valintatapajonoOid, uusi.hakemusOid))
        case (_, Some(EhdollisenHyvaksymisenEhtoKoodi.EHTO_MUU)) if uusi.ehdollisenHyvaksymisenEhtoEN.forall(_.isEmpty) =>
          Left(ValinnantulosUpdateStatus(409, "Valinnantuloksen ehdollisen hyväksynnän englanninkielistä ehtoa ei ole annettu.", uusi.valintatapajonoOid, uusi.hakemusOid))
        case _ => Right()
      }

      def validateHyvaksyttyVarasijalta() = (uusi.hyvaksyttyVarasijalta, uusi.valinnantila) match {
        case (None, _) | (vanha.hyvaksyttyVarasijalta, _) => Right()
        case (Some(true), Varalla) if (allowOphUpdate(session) || allowMusiikkiUpdate(session, tarjoajaOids)) => Right()
        case (Some(true), x) if x != Varalla => Left(ValinnantulosUpdateStatus(409, s"Ei voida hyväksyä varasijalta", uusi.valintatapajonoOid, uusi.hakemusOid))
        case (Some(false), _) if (allowOphUpdate(session) || allowMusiikkiUpdate(session, tarjoajaOids)) => Right()
        case (_, _) => Left(ValinnantulosUpdateStatus(401, s"Käyttäjällä ${session.personOid} ei ole oikeuksia hyväksyä varasijalta", uusi.valintatapajonoOid, uusi.hakemusOid))
      }

      def validateHyvaksyPeruuntunut() = (uusi.hyvaksyPeruuntunut, uusi.valinnantila, isJulkaistavissa()) match {
        case (None, _, _) | (vanha.hyvaksyPeruuntunut, _, _) => Right()
        case (_, Hyvaksytty, false) if vanha.hyvaksyPeruuntunut == Some(true) => allowPeruuntuneidenHyvaksynta()
        case (_, Peruuntunut, false) => allowPeruuntuneidenHyvaksynta()
        case (_, _, _) => Left(ValinnantulosUpdateStatus(409, s"Hyväksy peruuntunut -arvoa ei voida muuttaa valinnantulokselle", uusi.valintatapajonoOid, uusi.hakemusOid))
      }

      def isJulkaistavissa(): Boolean =
        (uusi.julkaistavissa, vanha.julkaistavissa) match {
          case (None, Some(true)) | (Some(true), _) => true
          case (_, _) => false
        }

      def validateIlmoittautumistila() = (uusi.ilmoittautumistila, uusi.vastaanottotila) match {
        case (vanha.ilmoittautumistila, _) => Right()
        case (Lasna | LasnaSyksy | LasnaKokoLukuvuosi, _) if (uusi.vastaanottotila != ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI) =>
          Left(ValinnantulosUpdateStatus(409, s"Ilmoittautumista ei voida tallentaa, koska vastaanotto ei ole sitova", uusi.valintatapajonoOid, uusi.hakemusOid))
        case (EiTehty, _) => Right()
        case (_, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI) => Right()
        case (_, _) => Left(ValinnantulosUpdateStatus(409, s"Ilmoittautumista ei voida muuttaa, koska vastaanotto ei ole sitova", uusi.valintatapajonoOid, uusi.hakemusOid))
      }

      def allowPeruuntuneidenHyvaksynta() = authorizer.checkAccess(session, tarjoajaOids, Set(Role.SIJOITTELU_PERUUNTUNEIDEN_HYVAKSYNTA_OPH))
        .left.map(_ => ValinnantulosUpdateStatus(401, s"Käyttäjällä ${session.personOid} ei ole oikeuksia hyväksyä peruuntunutta", uusi.valintatapajonoOid, uusi.hakemusOid))

      def allowOphUpdate(session: Session) = session.hasAnyRole(Set(Role.SIJOITTELU_CRUD_OPH))

      def allowMusiikkiUpdate(session: Session, tarjoajaOids: Set[String]) =
        authorizer.checkAccess(session, tarjoajaOids, Set(Role.VALINTAKAYTTAJA_MUSIIKKIALA)).isRight

      validateMuutos().fold(
        e => DBIO.successful(Left(e)),
        _ => vastaanottoValidator.validateVastaanotto(uusi, vanhaOpt)
      )
    }
  }

  final val selite = "Virkailijan tallennus"

  def save(uusi: Valinnantulos, vanhaOpt: Option[Valinnantulos], ifUnModifiedSince: Option[Instant]): DBIO[Unit] = {
    val muokkaaja = session.personOid
    val vanha = vanhaOpt.getOrElse(throw new IllegalStateException(s"Vain valinnantuloksen muokkaus sallittu haussa ${haku.oid}"))
    val updateOhjaus = if (uusi.hasOhjausChanged(vanha)) {
      valinnantulosRepository.updateValinnantuloksenOhjaus(
        uusi.getValinnantuloksenOhjauksenMuutos(vanha, muokkaaja, selite), Some(ifUnmodifiedSince))
    } else {
      DBIO.successful()
    }
    val updateEhdollisenHyvaksynnanEhto = if (uusi.hasEhdollisenHyvaksynnanEhtoChanged(vanha)) {
      valinnantulosRepository.storeEhdollisenHyvaksynnanEhto(
        uusi.getEhdollisenHyvaksynnanEhtoMuutos(vanha), Some(ifUnmodifiedSince)
      )
    } else {
      DBIO.successful(())
    }
    val updateVastaanotto = if (uusi.vastaanottotila != vanha.vastaanottotila &&
      !(uusi.vastaanottotila == ValintatuloksenTila.KESKEN && vanha.vastaanottotila == ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN)) {
      valinnantulosRepository.storeAction(VirkailijanVastaanotto(haku.oid, uusi.valintatapajonoOid, uusi.henkiloOid, uusi.hakemusOid, hakukohdeOid,
        VirkailijanVastaanottoAction.getVirkailijanVastaanottoAction(Vastaanottotila.values.find(Vastaanottotila.matches(_, uusi.vastaanottotila))
          .getOrElse(throw new IllegalArgumentException(s"Odottamaton vastaanottotila ${uusi.vastaanottotila}"))), muokkaaja, selite), ifUnModifiedSince)
    } else {
      DBIO.successful(())
    }
    val updateIlmoittautuminen = if (uusi.ilmoittautumistila != vanha.ilmoittautumistila) {
      valinnantulosRepository.storeIlmoittautuminen(
        vanha.henkiloOid, Ilmoittautuminen(vanha.hakukohdeOid, uusi.ilmoittautumistila, muokkaaja, selite), Some(ifUnmodifiedSince))
    } else {
      DBIO.successful(())
    }
    val updateHyvaksyttyJaJulkaistuDate = if(uusi.julkaistavissa.getOrElse(false) && uusi.isHyvaksytty) {
      valinnantulosRepository.setHyvaksyttyJaJulkaistavissa(uusi.hakemusOid, uusi.valintatapajonoOid, muokkaaja, selite)
    } else {
      DBIO.successful(())
    }
    updateIlmoittautuminen.andThen(updateVastaanotto).andThen(updateOhjaus).andThen(updateEhdollisenHyvaksynnanEhto).andThen(updateHyvaksyttyJaJulkaistuDate)
  }

  def audit(uusi: Valinnantulos, vanhaOpt: Option[Valinnantulos]): Unit = {
    val vanha = vanhaOpt.getOrElse(throw new IllegalStateException(s"Vain valinnantuloksen muokkaus sallittu haussa ${haku.oid}"))
    audit.log(auditInfo.user, ValinnantuloksenMuokkaus,
      new Target.Builder()
        .setField("hakukohde", vanha.hakukohdeOid.toString)
        .setField("valintatapajono", vanha.valintatapajonoOid.toString)
        .setField("hakemus", vanha.hakemusOid.toString)
        .build(),
      new Changes.Builder()
        .updated("valinnantila", vanha.valinnantila.toString, uusi.valinnantila.toString)
        .updated("ehdollisestiHyvaksyttavissa", vanha.ehdollisestiHyvaksyttavissa.getOrElse(false).toString, uusi.ehdollisestiHyvaksyttavissa.getOrElse(false).toString)
        .updated("julkaistavissa", vanha.julkaistavissa.getOrElse(false).toString, uusi.julkaistavissa.getOrElse(false).toString)
        .updated("hyvaksyttyVarasijalta", vanha.hyvaksyttyVarasijalta.getOrElse(false).toString, uusi.hyvaksyttyVarasijalta.getOrElse(false).toString)
        .updated("hyvaksyPeruuntunut", vanha.hyvaksyPeruuntunut.getOrElse(false).toString, uusi.hyvaksyPeruuntunut.getOrElse(false).toString)
        .updated("vastaanottotila", vanha.vastaanottotila.toString, uusi.vastaanottotila.toString)
        .updated("ilmoittautumistila", vanha.ilmoittautumistila.toString, uusi.ilmoittautumistila.toString)
        .build()
    )
  }
}
