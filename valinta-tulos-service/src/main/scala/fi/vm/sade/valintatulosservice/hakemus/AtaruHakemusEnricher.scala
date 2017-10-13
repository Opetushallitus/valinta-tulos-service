package fi.vm.sade.valintatulosservice.hakemus

import fi.vm.sade.valintatulosservice.MonadHelper
import fi.vm.sade.valintatulosservice.domain.{Hakemus, Hakutoive, Henkilotiedot}
import fi.vm.sade.valintatulosservice.oppijanumerorekisteri.OppijanumerorekisteriService
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakukohdeOid

class AtaruHakemusEnricher(hakuService: HakuService,
                           oppijanumerorekisteriService: OppijanumerorekisteriService) {
  def apply(ataruHakemus: AtaruHakemus): Either[Throwable, Hakemus] = {
    for {
      hakutoiveet <- MonadHelper.sequence(ataruHakemus.hakukohteet.map(HakukohdeOid).map(hakutoive)).right
      henkilo <- oppijanumerorekisteriService.henkilo(ataruHakemus.henkiloOid).right
    } yield Hakemus(
      oid = ataruHakemus.oid,
      hakuOid = ataruHakemus.hakuOid,
      henkiloOid = henkilo.oid.toString,
      asiointikieli = ataruHakemus.asiointikieli,
      toiveet = hakutoiveet,
      henkilotiedot = Henkilotiedot(
        kutsumanimi = henkilo.kutsumanimi,
        email = ataruHakemus.henkilotiedot.email,
        hasHetu = henkilo.hetu.isDefined
      )
    )
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
