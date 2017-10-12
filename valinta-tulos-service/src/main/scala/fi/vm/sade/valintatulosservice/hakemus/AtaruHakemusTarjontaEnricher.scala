package fi.vm.sade.valintatulosservice.hakemus

import fi.vm.sade.valintatulosservice.MonadHelper
import fi.vm.sade.valintatulosservice.domain.{Hakemus, Hakutoive}
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakukohdeOid

class AtaruHakemusTarjontaEnricher(hakuService: HakuService) {
  def apply(ataruHakemus: AtaruHakemus): Either[Throwable, Hakemus] = {
    MonadHelper.sequence(ataruHakemus.hakukohteet.map(HakukohdeOid).map(hakutoive))
      .right.map(hakutoiveet => {
      Hakemus(
        oid = ataruHakemus.oid,
        hakuOid = ataruHakemus.hakuOid,
        henkiloOid = ataruHakemus.henkiloOid.toString,
        asiointikieli = ataruHakemus.asiointikieli,
        toiveet = hakutoiveet,
        henkilotiedot = ataruHakemus.henkilotiedot
      )
    })
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
