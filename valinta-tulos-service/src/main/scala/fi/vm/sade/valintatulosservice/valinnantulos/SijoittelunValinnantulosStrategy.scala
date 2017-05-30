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
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValinnantulosRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.{AuditInfo, ValinnantuloksenMuokkaus}
import slick.dbio.DBIO

import scala.concurrent.ExecutionContext.Implicits.global

class SijoittelunValinnantulosStrategy(auditInfo: AuditInfo,
                                       tarjoajaOids: Set[String],
                                       haku: Haku,
                                       ohjausparametrit: Option[Ohjausparametrit],
                                       authorizer: OrganizationHierarchyAuthorizer,
                                       appConfig: VtsAppConfig,
                                       valinnantulosRepository: ValinnantulosRepository,
                                       ifUnmodifiedSince: Instant,
                                       audit: Audit) extends ValinnantulosStrategy with Logging {
  private val session = auditInfo.session._2

  def hasChange(uusi:Valinnantulos, vanha:Valinnantulos) = (uusi.hasChanged(vanha) || uusi.hasOhjausChanged(vanha) || uusi.hasEhdollisenHyvaksynnanEhtoChanged(vanha))

  def validate(uusi: Valinnantulos, vanhaOpt: Option[Valinnantulos]): Either[ValinnantulosUpdateStatus, Unit] = {
    if (vanhaOpt.isEmpty) {
      logger.warn(s"Hakemuksen ${uusi.hakemusOid} valinnan tulosta ei löydy " +
        s"valintatapajonosta ${uusi.valintatapajonoOid}.")
      Left(ValinnantulosUpdateStatus(404, s"Valinnantulosta ei löydy", uusi.valintatapajonoOid, uusi.hakemusOid))
    } else {
      val vanha = vanhaOpt.get

      def validateMuutos(): Either[ValinnantulosUpdateStatus, Unit] = {
        for {
          valinnantila <- validateValinnantila().right
          julkaistavissa <- validateJulkaistavissa().right
          _ <- validateEhdollisestiHyvaksytty.right
          hyvaksyttyVarasijalta <- validateHyvaksyttyVarasijalta().right
          hyvaksyPeruuntunut <- validateHyvaksyPeruuntunut().right
          vastaanottoNotChanged <- validateVastaanottoNotChanged().right
          //TODO vastaanotto <- validateVastaanotto(vanha, uusi, session, tarjoajaOid).right
          ilmoittautumistila <- validateIlmoittautumistila().right
        } yield ilmoittautumistila
      }

      def validateVastaanottoNotChanged() = vanha.vastaanottotila match {
        case uusi.vastaanottotila => Right()
        case _ => Left(ValinnantulosUpdateStatus(404,
          s"Valinnantulosta ei voida päivittää, koska vastaanottoa ${uusi.vastaanottotila} on muutettu samanaikaisesti tilaan ${vanha.vastaanottotila}", uusi.valintatapajonoOid, uusi.hakemusOid))
      }

      def validateValinnantila() = uusi.valinnantila match {
        case vanha.valinnantila => Right()
        case _ => Left(ValinnantulosUpdateStatus(403, s"Valinnantilan muutos ei ole sallittu", uusi.valintatapajonoOid, uusi.hakemusOid))
      }

      def validateJulkaistavissa() = (uusi.julkaistavissa, uusi.vastaanottotila) match {
        case (vanha.julkaistavissa, _) => Right()
        case (false, vastaanotto) if vastaanotto != ValintatuloksenTila.KESKEN =>
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
        case (Some(false), Some(_)) | (None, Some(_)) =>
          Left(ValinnantulosUpdateStatus(409, "Valinnantulos on ei ole ehdollisesti hyväksyttävissä, mutta ehto on annettu.", uusi.valintatapajonoOid, uusi.hakemusOid))
        case (_, Some(EhdollisenHyvaksymisenEhtoKoodi.EHTO_MUU)) if uusi.ehdollisenHyvaksymisenEhtoFI.forall(_.isEmpty) =>
          Left(ValinnantulosUpdateStatus(409, "Valinnantuloksen ehdollisen hyväksynnän suomenkielistä ehtoa ei ole annettu.", uusi.valintatapajonoOid, uusi.hakemusOid))
        case (_, Some(EhdollisenHyvaksymisenEhtoKoodi.EHTO_MUU)) if uusi.ehdollisenHyvaksymisenEhtoSV.forall(_.isEmpty) =>
          Left(ValinnantulosUpdateStatus(409, "Valinnantuloksen ehdollisen hyväksynnän ruotsinkielistä ehtoa ei ole annettu.", uusi.valintatapajonoOid, uusi.hakemusOid))
        case (_, Some(EhdollisenHyvaksymisenEhtoKoodi.EHTO_MUU)) if uusi.ehdollisenHyvaksymisenEhtoEN.forall(_.isEmpty) =>
          Left(ValinnantulosUpdateStatus(409, "Valinnantuloksen ehdollisen hyväksynnän englanninkielistä ehtoa ei ole annettu.", uusi.valintatapajonoOid, uusi.hakemusOid))
        case _ => Right()
      }

      def validateHyvaksyttyVarasijalta() = (uusi.hyvaksyttyVarasijalta, uusi.valinnantila) match {
        case (vanha.hyvaksyttyVarasijalta, _) => Right()
        case (true, Varalla) if allowOphUpdate(session) || allowMusiikkiUpdate(session, tarjoajaOids) => Right()
        case (true, x) if x != Varalla => Left(ValinnantulosUpdateStatus(409, s"Ei voida hyväksyä varasijalta", uusi.valintatapajonoOid, uusi.hakemusOid))
        case (false, _) if allowOphUpdate(session) || allowMusiikkiUpdate(session, tarjoajaOids) => Right()
        case (_, _) => Left(ValinnantulosUpdateStatus(401, s"Käyttäjällä ${session.personOid} ei ole oikeuksia hyväksyä varasijalta", uusi.valintatapajonoOid, uusi.hakemusOid))
      }

      def validateHyvaksyPeruuntunut() = (uusi.hyvaksyPeruuntunut, uusi.valinnantila, uusi.julkaistavissa) match {
        case (vanha.hyvaksyPeruuntunut, _, _) => Right()
        case (_, Hyvaksytty, false) if vanha.hyvaksyPeruuntunut => allowPeruuntuneidenHyvaksynta()
        case (_, Peruuntunut, false) => allowPeruuntuneidenHyvaksynta()
        case (_, _, _) => Left(ValinnantulosUpdateStatus(409, s"Hyväksy peruuntunut -arvoa ei voida muuttaa valinnantulokselle", uusi.valintatapajonoOid, uusi.hakemusOid))
      }

      def validateIlmoittautumistila() = (uusi.ilmoittautumistila, uusi.vastaanottotila) match {
        case (vanha.ilmoittautumistila, _) => Right()
        case (_, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI) => Right()
        case (_, _) => Left(ValinnantulosUpdateStatus(409, s"Ilmoittautumista ei voida muuttaa, koska vastaanotto ei ole sitova", uusi.valintatapajonoOid, uusi.hakemusOid))
      }

      def allowPeruuntuneidenHyvaksynta() = authorizer.checkAccess(session, tarjoajaOids, Set(Role.SIJOITTELU_PERUUNTUNEIDEN_HYVAKSYNTA_OPH))
        .left.map(_ => ValinnantulosUpdateStatus(401, s"Käyttäjällä ${session.personOid} ei ole oikeuksia hyväksyä peruuntunutta", uusi.valintatapajonoOid, uusi.hakemusOid))

      def allowOphUpdate(session: Session) = session.hasAnyRole(Set(Role.SIJOITTELU_CRUD_OPH))

      def allowMusiikkiUpdate(session: Session, tarjoajaOids: Set[String]) =
        authorizer.checkAccess(session, tarjoajaOids, Set(Role.VALINTAKAYTTAJA_MUSIIKKIALA)).isRight

      validateMuutos()
    }
  }

  def save(uusi: Valinnantulos, vanhaOpt: Option[Valinnantulos]): DBIO[Unit] = {
    val muokkaaja = session.personOid
    val vanha = vanhaOpt.getOrElse(throw new IllegalStateException(s"Vain valinnantuloksen muokkaus sallittu haussa ${haku.oid}"))
    val updateOhjaus = if (uusi.hasOhjausChanged(vanha)) {
      valinnantulosRepository.updateValinnantuloksenOhjaus(
        uusi.getValinnantuloksenOhjauksenMuutos(vanha, muokkaaja, "Virkailijan tallennus"), Some(ifUnmodifiedSince))
    } else {
      DBIO.successful(())
    }
    val updateEhdollisenHyvaksynnanEhto = if (uusi.hasEhdollisenHyvaksynnanEhtoChanged(vanha)) {
      valinnantulosRepository.storeEhdollisenHyvaksynnanEhto(
        uusi.getEhdollisenHyvaksynnanEhtoMuutos(vanha), Some(ifUnmodifiedSince)
      )
    } else {
      DBIO.successful(())
    }
    val updateIlmoittautuminen = if (uusi.ilmoittautumistila != vanha.ilmoittautumistila) {
      valinnantulosRepository.storeIlmoittautuminen(
        vanha.henkiloOid, Ilmoittautuminen(vanha.hakukohdeOid, uusi.ilmoittautumistila, muokkaaja, "Virkailijan tallennus"), Some(ifUnmodifiedSince))
    } else {
      DBIO.successful(())
    }
    updateOhjaus.andThen(updateEhdollisenHyvaksynnanEhto).andThen(updateIlmoittautuminen)
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
        .updated("julkaistavissa", vanha.julkaistavissa.toString, uusi.julkaistavissa.toString)
        .updated("hyvaksyttyVarasijalta", vanha.hyvaksyttyVarasijalta.toString, uusi.hyvaksyttyVarasijalta.toString)
        .updated("hyvaksyPeruuntunut", vanha.hyvaksyPeruuntunut.toString, uusi.hyvaksyPeruuntunut.toString)
        .updated("vastaanottotila", vanha.vastaanottotila.toString, uusi.vastaanottotila.toString)
        .updated("ilmoittautumistila", vanha.ilmoittautumistila.toString, uusi.ilmoittautumistila.toString)
        .build()
    )
  }
}
