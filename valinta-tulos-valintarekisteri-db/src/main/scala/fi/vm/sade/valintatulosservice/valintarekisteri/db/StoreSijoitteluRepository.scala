package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.SijoitteluWrapper

trait StoreSijoitteluRepository {

  def storeSijoittelu(sijoittelu:SijoitteluWrapper)
}
