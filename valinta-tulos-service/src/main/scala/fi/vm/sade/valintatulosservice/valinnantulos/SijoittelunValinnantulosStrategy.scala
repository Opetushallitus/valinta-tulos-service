package fi.vm.sade.valintatulosservice.valinnantulos

import java.time.Instant

import fi.vm.sade.auditlog.{Changes, Target}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.{AuditInfo, ValinnantuloksenMuokkaus, ValinnantulosUpdateStatus}
import fi.vm.sade.valintatulosservice.ohjausparametrit.Ohjausparametrit
import fi.vm.sade.valintatulosservice.security.{Role, Session}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

import scala.util.{Failure, Success, Try}

trait SijoittelunValinnantulosStrategy extends ValinnantulosStrategy with Logging {

  def handleSijoittelunValinnantulokset(auditInfo:AuditInfo, valintatapajonoOid:String, valinnantulokset:List[Valinnantulos], ifUnmodifiedSince: Instant) =
    new SijoittelunValinnantulosContext(auditInfo, valintatapajonoOid, valinnantulokset, ifUnmodifiedSince).handle()

  class SijoittelunValinnantulosContext(val auditInfo:AuditInfo, val valintatapajonoOid:String, val valinnantulokset:List[Valinnantulos], val ifUnmodifiedSince: Instant) extends ValinnantulosContext {
    val session = auditInfo.session._2
    val vanhatValinnantulokset = getVanhatValinnantulokset()
    val hakukohdeOid = vanhatValinnantulokset.headOption.map(_._2._2.hakukohdeOid).getOrElse("")

    val (hakuOid:String, tarjoajaOid:String) = getOidsFromValintarekisteri(hakukohdeOid)

    def getOidsFromValintarekisteri(hakukohdeOid: String): (String, String) = (
      valinnantulosRepository.getHakuForHakukohde(hakukohdeOid),
      valinnantulosRepository.getTarjoajaForHakukohde(hakukohdeOid)
    )

    def save(uusi: Valinnantulos, vanhaOpt: Option[Valinnantulos]) = {
      val muokkaaja = session.personOid
      val vanha = vanhaOpt.getOrElse(throw new RuntimeException("Foo"))

      logger.info(s"Käyttäjä ${muokkaaja} muokkasi " +
        s"hakemuksen ${uusi.hakemusOid} valinnan tulosta valintatapajonossa $uusi.valintatapajonoOid " +
        s"vastaanottotilasta ${vanha.vastaanottotila} tilaan ${uusi.vastaanottotila} ja " +
        s"ilmoittautumistilasta ${vanha.ilmoittautumistila} tilaan ${uusi.ilmoittautumistila}.")

      val operations = List(
        Option(uusi.hasOhjausChanged(vanha)).collect { case true => valinnantulosRepository.updateValinnantuloksenOhjaus(
          uusi.getValinnantuloksenOhjauksenMuutos(vanha, muokkaaja, "Virkailijan tallennus"), Some(ifUnmodifiedSince))
        },
        Option(uusi.ilmoittautumistila != vanha.ilmoittautumistila).collect { case true => valinnantulosRepository.storeIlmoittautuminen(
          vanha.henkiloOid, Ilmoittautuminen(vanha.hakukohdeOid, uusi.ilmoittautumistila, muokkaaja, "Virkailijan tallennus"), Some(ifUnmodifiedSince))
        }
      ).flatten

      Try(valinnantulosRepository.runBlockingTransactionally(
        slick.dbio.DBIO.seq(operations: _*)
      )) match {
        case Success(_) =>
          audit.log(auditInfo.user, ValinnantuloksenMuokkaus,
            new Target.Builder()
              .setField("hakukohde", vanha.hakukohdeOid)
              .setField("valintatapajono", vanha.valintatapajonoOid)
              .setField("hakemus", vanha.hakemusOid)
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
          Right()
        case Failure(t) =>
          logger.warn(s"Valinnantuloksen $uusi tallennus epäonnistui", t)
          Left(ValinnantulosUpdateStatus(500, s"Valinnantuloksen tallennus epäonnistui", valintatapajonoOid, uusi.hakemusOid))
      }
    }

    def validate(uusi: Valinnantulos, vanhaOpt: Option[Valinnantulos]) = {
      if (vanhaOpt.isEmpty) {
        logger.warn(s"Hakemuksen ${uusi.hakemusOid} valinnan tulosta ei löydy " +
          s"valintatapajonosta $valintatapajonoOid.")
        Left(ValinnantulosUpdateStatus(404, s"Valinnantulosta ei löydy", uusi.valintatapajonoOid, uusi.hakemusOid))
      } else {
        val vanha = vanhaOpt.get

        def validateMuutos(): Either[ValinnantulosUpdateStatus, Unit] = {
          for {
            valinnantila <- validateValinnantila().right
            ehdollisestiHyvaksyttavissa <- validateEhdollisestiHyvaksyttavissa().right
            julkaistavissa <- validateJulkaistavissa().right
            hyvaksyttyVarasijalta <- validateHyvaksyttyVarasijalta().right
            hyvaksyPeruuntunut <- validateHyvaksyPeruuntunut().right
            //TODO vastaanotto <- validateVastaanotto(vanha, uusi, session, tarjoajaOid).right
            ilmoittautumistila <- validateIlmoittautumistila().right
          } yield ilmoittautumistila
        }

        def validateValinnantila() = uusi.valinnantila match {
          case vanha.valinnantila => Right()
          case _ => Left(ValinnantulosUpdateStatus(403, s"Valinnantilan muutos ei ole sallittu", uusi.valintatapajonoOid, uusi.hakemusOid))
        }

        def validateEhdollisestiHyvaksyttavissa() = uusi.ehdollisestiHyvaksyttavissa match {
          case None | vanha.ehdollisestiHyvaksyttavissa => Right()
          case _ if allowOrgUpdate(session, tarjoajaOid) => Right()
          case _ => Left(ValinnantulosUpdateStatus(401, s"Käyttäjällä ${session.personOid} ei ole oikeuksia hyväksyä ehdollisesti", uusi.valintatapajonoOid, uusi.hakemusOid))
        }

        def validateJulkaistavissa() = (uusi.julkaistavissa, uusi.vastaanottotila) match {
          case (None, _) | (vanha.julkaistavissa, _) => Right()
          case (Some(false), vastaanotto) if List(MerkitseMyohastyneeksi, Poista).contains(vastaanotto) => Right()
          case (Some(false), _) => Left(ValinnantulosUpdateStatus(409, s"Valinnantulosta ei voida merkitä ei-julkaistavaksi, koska sillä on vastaanotto", uusi.valintatapajonoOid, uusi.hakemusOid))
          case (Some(true), _) if allowJulkaistavissaUpdate() => Right()
          case (_, _) => Left(ValinnantulosUpdateStatus(401, s"Käyttäjällä ${session.personOid} ei ole oikeuksia julkaista valinnantulosta", uusi.valintatapajonoOid, uusi.hakemusOid))
        }

        def allowJulkaistavissaUpdate(): Boolean = {
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

        def validateHyvaksyttyVarasijalta() = (uusi.hyvaksyttyVarasijalta, uusi.valinnantila) match {
          case (None, _) | (vanha.hyvaksyttyVarasijalta, _) => Right()
          case (Some(true), Varalla) if (allowOphUpdate(session) || allowMusiikkiUpdate(session, tarjoajaOid)) => Right()
          case (Some(true), x) if x != Varalla => Left(ValinnantulosUpdateStatus(409, s"Ei voida hyväksyä varasijalta", uusi.valintatapajonoOid, uusi.hakemusOid))
          case (Some(false), _) if (allowOphUpdate(session) || allowMusiikkiUpdate(session, tarjoajaOid)) => Right()
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
          case (_, VastaanotaSitovasti) => Right()
          case (_, _) => Left(ValinnantulosUpdateStatus(409, s"Ilmoittautumista ei voida muuttaa, koska vastaanotto ei ole sitova", uusi.valintatapajonoOid, uusi.hakemusOid))
        }

        def allowPeruuntuneidenHyvaksynta() = authorizer.checkAccess(session, tarjoajaOid, List(Role.SIJOITTELU_PERUUNTUNEIDEN_HYVAKSYNTA_OPH)) match {
          case Failure(e) => Left(ValinnantulosUpdateStatus(401, s"Käyttäjällä ${session.personOid} ei ole oikeuksia hyväksyä peruuntunutta", uusi.valintatapajonoOid, uusi.hakemusOid))
          case Success(_) => Right()
        }

        def allowOphUpdate(session: Session) = session.hasAnyRole(Set(Role.SIJOITTELU_CRUD_OPH))

        def allowOrgUpdate(session: Session, tarjoajaOid: String) = session.hasAnyRole(Set(Role.sijoitteluCrudOrg(tarjoajaOid), Role.sijoitteluUpdateOrg(tarjoajaOid)))

        def allowMusiikkiUpdate(session: Session, tarjoajaOid: String) = session.hasAnyRole(Set(Role.musiikkialanValintaToinenAste(tarjoajaOid)))

        validateMuutos()
      }
    }
  }
}
