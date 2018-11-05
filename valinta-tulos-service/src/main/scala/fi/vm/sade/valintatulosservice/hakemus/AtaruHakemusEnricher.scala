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

  private def toHakemus(henkilot: Map[HakijaOid, Henkilo], hakutoiveet: Map[HakukohdeOid, Hakutoive], hakemus: AtaruHakemus): Hakemus = {
    val henkilo = henkilot(hakemus.henkiloOid)
    Hakemus(
      oid = hakemus.oid,
      hakuOid = hakemus.hakuOid,
      henkiloOid = hakemus.henkiloOid.toString,
      asiointikieli = hakemus.asiointikieli,
      toiveet = hakemus.hakutoiveet.map(s => hakutoiveet(HakukohdeOid(s.hakukohdeOid))),
      henkilotiedot = Henkilotiedot(
        kutsumanimi = henkilo.kutsumanimi,
        email = hakemus.email,
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

  private def hakutoiveet(hakemukset: List[AtaruHakemus]): Either[Throwable, Map[HakukohdeOid, Hakutoive]] = {
    MonadHelper.sequence(uniqueHakukohdeOids(hakemukset).map(hakutoive))
      .right.map(_.map(h => h.oid -> h).toMap)
  }

  private def uniquePersonOids(hakemukset: List[AtaruHakemus]): Set[HakijaOid] = {
    hakemukset.map(_.henkiloOid).toSet
  }

  private def uniqueHakukohdeOids(hakemukset: List[AtaruHakemus]): Set[HakukohdeOid] = {
    hakemukset.flatMap(_.hakutoiveet).map(_.hakukohdeOid).toSet.map(HakukohdeOid)
  }

  private def hakutoive(oid: HakukohdeOid): Either[Throwable, Hakutoive] = {
    hakuService.getHakukohde(oid).right.map(h => {
      Hakutoive(
        oid = oid,
        tarjoajaOid = h.tarjoajaOids.head,
        nimi = h.hakukohteenNimet.getOrElse("kieli_fi", ""), // FIXME use correct language
        tarjoajaNimi = h.tarjoajaNimet.getOrElse("fi", "") // FIXME use correct language
      )
    })
  }
}
