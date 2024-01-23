package fi.vm.sade.valintatulosservice.hakemus

import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.valintatulosservice.MonadHelper
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.domain.{Hakemus, Hakutoive, Henkilotiedot}
import fi.vm.sade.valintatulosservice.memoize.TTLOptionalMemoize
import fi.vm.sade.valintatulosservice.oppijanumerorekisteri.{Henkilo, OppijanumerorekisteriService}
import fi.vm.sade.valintatulosservice.tarjonta.{HakuService, Hakukohde}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakijaOid, HakukohdeOid}

class AtaruHakemusEnricher(config: VtsAppConfig,
                           hakuService: HakuService,
                           oppijanumerorekisteriService: OppijanumerorekisteriService) {

  private val hakukohdeCache: HakukohdeOid => Either[Throwable, Hakukohde] =
    TTLOptionalMemoize.memoize(
      hakuService.getHakukohde,
      lifetimeSeconds = config.settings.ataruHakemusEnricherHakukohdeCacheTtl.toSeconds,
      maxSize = config.settings.ataruHakemusEnricherHakukohdeCacheMaxSize)

  def apply(ataruHakemukset: List[AtaruHakemus]): Either[Throwable, List[Hakemus]] = {
    for {
      henkilot <- timed("Ataru: Henkilöiden tietojen hakeminen ONR:stä", 1000)(henkilot(ataruHakemukset).right)
      hakutoiveet <- timed("Ataru: Hakukohdeiden tietojen hakeminen Kouta-internalista", 1000)(hakutoiveet(ataruHakemukset).right)
    } yield ataruHakemukset.map(hakemus => toHakemus(henkilot, hakutoiveet, hakemus))
  }

  private def toHakemus(henkilot: Map[HakijaOid, Henkilo], hakutoiveet: Map[HakukohdeOid, String => Hakutoive], hakemus: AtaruHakemus): Hakemus = {
    val henkilo = henkilot(hakemus.henkiloOid)
    Hakemus(
      oid = hakemus.oid,
      hakuOid = hakemus.hakuOid,
      henkiloOid = hakemus.henkiloOid.toString,
      asiointikieli = hakemus.asiointikieli,
      toiveet = hakemus.hakukohdeOids.map(oid => hakutoiveet(oid)(hakemus.asiointikieli)),
      henkilotiedot = Henkilotiedot(
        kutsumanimi = henkilo.kutsumanimi,
        email = Some(hakemus.email),
        hasHetu = henkilo.hetu.isDefined,
        henkilo.kansalaisuudet.getOrElse(List()),
        yksiloity = henkilo.yksiloity
      ),
      maksuvelvollisuudet = hakemus.paymentObligations
    )
  }

  private def henkilot(hakemukset: List[AtaruHakemus]): Either[Throwable, Map[HakijaOid, Henkilo]] = {
    val personOids = uniquePersonOids(hakemukset)
    oppijanumerorekisteriService.henkilot(personOids)
      .right.flatMap(hs => {
      val missingOids = personOids.diff(hs.keySet)
      if (missingOids.isEmpty) {
        Right(hs)
      } else {
        Left(new IllegalArgumentException(s"No henkilöt $missingOids found"))
      }
    })
  }

  private def hakutoiveet(hakemukset: List[AtaruHakemus]): Either[Throwable, Map[HakukohdeOid, String => Hakutoive]] = {
    MonadHelper.sequence(uniqueHakukohdeOids(hakemukset).map(hakutoive))
      .right.map(_.toMap)
  }

  private def uniquePersonOids(hakemukset: List[AtaruHakemus]): Set[HakijaOid] = {
    hakemukset.map(_.henkiloOid).toSet
  }

  private def uniqueHakukohdeOids(hakemukset: List[AtaruHakemus]): Set[HakukohdeOid] = {
    hakemukset.flatMap(_.hakukohdeOids).toSet
  }

  private def getFirstNonBlank[K](m: Map[K, String], keys: List[K]): Option[String] = {
    keys.collectFirst { case k if m.contains(k) && m(k).trim.nonEmpty => m(k) }
  }

  private def hakutoive(oid: HakukohdeOid): Either[Throwable, (HakukohdeOid, String => Hakutoive)] = {
    hakukohdeCache(oid).right.map(h => {
      (oid, lang => Hakutoive(
        oid = oid,
        tarjoajaOid = h.tarjoajaOids.head,
        nimi = getFirstNonBlank(h.hakukohteenNimet, List("kieli_" + lang, "kieli_fi", "kieli_sv", "kieli_en"))
          .getOrElse(throw new IllegalStateException(s"Hakukohteella $oid ei ole nimeä")),
        tarjoajaNimi = getFirstNonBlank(h.tarjoajaNimet, List(lang, "fi", "sv", "en"))
          .getOrElse(throw new IllegalStateException(s"Hakukohteella $oid ei ole tarjoajan nimeä"))
      ))
    })
  }
}
