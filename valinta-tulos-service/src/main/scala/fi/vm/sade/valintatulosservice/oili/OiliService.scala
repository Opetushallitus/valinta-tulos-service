package fi.vm.sade.valintatulosservice.oili

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.domain.Ilmoittautumisaika
import fi.vm.sade.valintatulosservice.hakemus.{AtaruHakemus, AtaruHakemusRepository, WithHakemusOids}
import fi.vm.sade.valintatulosservice.logging.Logging
import fi.vm.sade.valintatulosservice.ohjausparametrit.OhjausparametritService
import fi.vm.sade.valintatulosservice.oppijanumerorekisteri.{Henkilo, OppijanumerorekisteriService}
import fi.vm.sade.valintatulosservice.tarjonta.{HakuService, HakukohdeOili, OiliTarjontaHakukohdeNotImplementedException}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValinnantulosRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.{AuditInfo, ValinnantulosService}
import org.joda.time.DateTime

import java.time.OffsetDateTime

class OiliService(hakemusRepository: AtaruHakemusRepository,
                  hakuService: HakuService,
                  valinnantulosService: ValinnantulosService,
                  oppijanumerorekisteriService: OppijanumerorekisteriService,
                  valinnantulosRepository: ValinnantulosRepository,
                  ohjausparametritService: OhjausparametritService)(implicit appConfig: VtsAppConfig) extends Logging {

  private val vastaanotonTilatHyvaksytaan: Set[ValintatuloksenTila] = Set(
    ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI,
    ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT)

  def getOiliHakija(hakijaOid: HakijaOid, auditInfo: AuditInfo): Option[OiliHakija] = {
    val henkiloMap = oppijanumerorekisteriService.henkilot(Set(hakijaOid)).fold(
      e => throw new RuntimeException(s"OILI: ONR-haku epäonnistui oidille $hakijaOid", e),
      identity
    )
    buildOiliHakija(hakijaOid, henkiloMap.get(hakijaOid), auditInfo)
  }

  private def buildOiliHakija(oid: HakijaOid, henkiloOpt: Option[Henkilo], auditInfo: AuditInfo): Option[OiliHakija] = {
    henkiloOpt.flatMap { henkilo =>
      val vastaanotetut = valinnantulosRepository.getHakijanVastaanotetutValinnantilat(oid)
      if (vastaanotetut.isEmpty) None
      else {
        val hakemusOids = vastaanotetut.map(_.hakemusOid)
        val ataruByOid = fetchAtaruHakemukset(hakemusOids)
        val tuloksetByHakemus = valinnantulosService
          .getValinnantuloksetForHakemukset(hakemusOids, auditInfo)
          .filter(t => vastaanotonTilatHyvaksytaan.contains(t.valinnantulos.vastaanottotila))
          .groupBy(_.valinnantulos.hakemusOid)

        val hakuOidit: Set[HakuOid] = ataruByOid.values.map(_.hakuOid).toSet
        val hakuAktiivinenByOid: Map[HakuOid, Boolean] =
          hakuOidit.iterator.map(oid => oid -> isHakuAktiivinen(oid)).toMap

        val hakemukset = ataruByOid.values.toList
          .filter(a => hakuAktiivinenByOid.getOrElse(a.hakuOid, false))
          .map(buildOiliHakemus(_, tuloksetByHakemus))
          .filter(_.hakukohteet.nonEmpty)

        if (hakemukset.isEmpty) None
        else Some(OiliHakija(
          oppijanumero = henkilo.oid.toString,
          sukunimi = henkilo.sukunimi,
          etunimet = henkilo.etunimet,
          kutsumanimi = henkilo.kutsumanimi,
          henkilotunnus = henkilo.hetu.map(_.toString),
          asiointikieli = resolveAsiointikieli(henkilo, ataruByOid),
          hakemukset = hakemukset
        ))
      }
    }
  }

  private def resolveAsiointikieli(henkilo: Henkilo, ataruByOid: Map[HakemusOid, AtaruHakemus]): Option[String] = {
    henkilo.asiointiKieli.filter(_.nonEmpty).orElse(
      ataruByOid.values.toList
        .sortBy(_.jattoAjanhetki)(Ordering[Option[OffsetDateTime]].reverse)
        .map(_.asiointikieli)
        .find(_.nonEmpty)
    )
  }

  private def fetchAtaruHakemukset(hakemusOids: Set[HakemusOid]): Map[HakemusOid, AtaruHakemus] = {
    if (hakemusOids.isEmpty) Map.empty
    else {
      hakemusRepository.getHakemukset(WithHakemusOids(hakemusOids.toList, None, includeYhteystiedot = true)) match {
        case Right(response) => response.applications.map(a => a.oid -> a).toMap
        case Left(e) =>
          logger.error(s"OILI: Ataru-haku epäonnistui hakemuksille $hakemusOids", e)
          throw new RuntimeException(s"OILI: Ataru-haku epäonnistui", e)
      }
    }
  }

  private def buildOiliHakemus(ataru: AtaruHakemus,
                                tuloksetByHakemus: Map[HakemusOid, Set[ValinnantulosWithTilahistoria]]): OiliHakemus = {
    val tulokset = tuloksetByHakemus.getOrElse(ataru.oid, Set.empty)
    val hakukohteet = tulokset.toList.flatMap(t => buildOiliHakukohde(t, ataru.hakuOid))
    val (hakuvuosi, hakukausi) = hakuvuosiJaKausi(ataru.hakuOid)
    OiliHakemus(
      jattoAjanhetki = ataru.jattoAjanhetki,
      hakemusOid = ataru.oid.toString,
      hakuOid = ataru.hakuOid.toString,
      hakuvuosi = hakuvuosi,
      hakukausi = hakukausi,
      lahiosoite = ataru.lahiosoite,
      postinumero = ataru.postinumero,
      postitoimipaikka = ataru.postitoimipaikka,
      sahkoposti = Option(ataru.email).filter(_.nonEmpty),
      puhelinnumero = ataru.puhelinnumero,
      hakukohteet = hakukohteet
    )
  }

  private def buildOiliHakukohde(tulos: ValinnantulosWithTilahistoria, hakuOid: HakuOid): Option[OiliHakukohde] = {
    val v = tulos.valinnantulos
    getHakukohdeOili(v.hakukohdeOid).map { hakukohde =>
      val ehdollisesti = v.ehdollisestiHyvaksyttavissa.getOrElse(false)
      val muuKuvaus = if (ehdollisesti) Some(Kielistetty(
        fi = v.ehdollisenHyvaksymisenEhtoFI,
        sv = v.ehdollisenHyvaksymisenEhtoSV,
        en = v.ehdollisenHyvaksymisenEhtoEN
      )) else None
      OiliHakukohde(
        jarjestyspaikkaOid = hakukohde.jarjestyspaikkaOid,
        hakukohdeOid = v.hakukohdeOid.toString,
        toteutusOid = hakukohde.toteutusOid,
        koulutuskoodiUri = hakukohde.koulutusKoodiUrit,
        valinnanTila = Some(v.valinnantila.toString),
        vastaanotonTila = Some(v.vastaanottotila.name()),
        onkoIlmoittauduttavissa = isIlmoittauduttavissa(v, hakuOid),
        ehdollisestiHyvaksytty = ehdollisesti,
        ehdollisestiHyvaksyttySyy = if (ehdollisesti) v.ehdollisenHyvaksymisenEhtoKoodi else None,
        ehdollisestiHyvaksyttyMuuKuvaus = muuKuvaus,
        ilmoittautuminen = Some(v.ilmoittautumistila.toString)
      )
    }
  }

  private def getHakukohdeOili(hakukohdeOid: HakukohdeOid): Option[HakukohdeOili] = {
    try {
      hakuService.getHakukohdeOili(hakukohdeOid).fold(
        e => {
          logger.warn(s"OILI: hakukohteen $hakukohdeOid haku Koutasta epäonnistui, sivuutetaan: ${e.getMessage}")
          None
        },
        h => Some(h)
      )
    } catch {
      case e: OiliTarjontaHakukohdeNotImplementedException =>
        logger.warn(s"OILI: vanhan tarjonnan hakukohde $hakukohdeOid sivuutetaan: ${e.getMessage}")
        None
    }
  }

  private def hakuvuosiJaKausi(hakuOid: HakuOid): (Option[String], Option[String]) = {
    hakuService.getHaku(hakuOid).fold(
      e => {
        logger.warn(s"OILI: haun $hakuOid haku epäonnistui: ${e.getMessage}")
        (None, None)
      },
      haku => haku.koulutuksenAlkamiskausi match {
        case Some(Kevat(vuosi)) => (Some(vuosi.toString), Some("K"))
        case Some(Syksy(vuosi)) => (Some(vuosi.toString), Some("S"))
        case _ => (None, None)
      }
    )
  }

  private def isIlmoittauduttavissa(v: Valinnantulos, hakuOid: HakuOid): Boolean = {
    if (!appConfig.settings.ilmoittautuminenEnabled) false
    else if (v.vastaanottotila != ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI) false
    else if (v.ilmoittautumistila != EiTehty) false
    else ohjausparametritService.ohjausparametrit(hakuOid).fold(
      _ => false,
      o => Ilmoittautumisaika(None, o.ilmoittautuminenPaattyy.map(new DateTime(_).withTime(23, 59, 59, 999))).aktiivinen
    )
  }

  private def isHakuAktiivinen(hakuOid: HakuOid): Boolean =
    ohjausparametritService.ohjausparametrit(hakuOid).fold(
      e => {
        logger.warn(s"OILI: ohjausparametrien haku epäonnistui haulle $hakuOid, sivuutetaan: ${e.getMessage}")
        false
      },
      o => o.hakukierrosPaattyy.exists(!_.isBeforeNow)
    )
}
