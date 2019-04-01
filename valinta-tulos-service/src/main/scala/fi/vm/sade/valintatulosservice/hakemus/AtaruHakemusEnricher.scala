package fi.vm.sade.valintatulosservice.hakemus

import fi.vm.sade.valintatulosservice.MonadHelper
import fi.vm.sade.valintatulosservice.domain.{Hakemus, Hakutoive, Henkilotiedot}
import fi.vm.sade.valintatulosservice.oppijanumerorekisteri.{Henkilo, OppijanumerorekisteriService}
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakijaOid, HakukohdeOid}

class AtaruHakemusEnricher(hakuService: HakuService,
                           oppijanumerorekisteriService: OppijanumerorekisteriService) {
  def apply(ataruHakemukset: List[AtaruHakemus]): Either[Throwable, List[Hakemus]] = {
    for {
      henkilot <- henkilot(ataruHakemukset).right
      hakutoiveet <- hakutoiveet(ataruHakemukset).right
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
        hasHetu = henkilo.hetu.isDefined
      )
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
    hakuService.getHakukohde(oid).right.map(h => {
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
