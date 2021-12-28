package fi.vm.sade.valintatulosservice.migri

import fi.vm.sade.valintatulosservice.oppijanumerorekisteri.{Henkilo, OppijanumerorekisteriService}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakijaOid

class MigriService(oppijanumerorekisteriService: OppijanumerorekisteriService) {

  private def fetchHenkilotFromONR(hakijaOids: Set[HakijaOid]): Set[Henkilo] = {
    oppijanumerorekisteriService.henkilot(hakijaOids) match {
      case Right(h) => h.values.toSet
      case Left(_) => throw new IllegalArgumentException(s"No hakijas found for oid: $hakijaOids found.")
    }
  }

  def fetchHakemuksetByHakijaOid(hakijaOids: Set[HakijaOid]): Set[Hakija] = {
    fetchHenkilotFromONR(hakijaOids).map(henkilo =>
      fi.vm.sade.valintatulosservice.migri.Hakija(
        henkilo = henkilo, Seq()
      )
    )
  }
}
