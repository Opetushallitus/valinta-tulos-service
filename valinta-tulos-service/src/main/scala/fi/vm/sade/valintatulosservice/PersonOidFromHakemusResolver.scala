package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakemusOid

trait PersonOidFromHakemusResolver {
  def findBy(hakemusOid: HakemusOid): Option[String]
}
