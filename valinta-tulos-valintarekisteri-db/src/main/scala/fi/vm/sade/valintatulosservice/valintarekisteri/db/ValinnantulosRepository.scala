package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.time.Instant

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Ilmoittautuminen, Valinnantulos}
import slick.dbio.DBIO

trait ValinnantulosRepository extends ValintarekisteriRepository {

  def storeIlmoittautuminen(henkiloOid: String, ilmoittautuminen: Ilmoittautuminen): DBIO[Unit]

  def getValinnantuloksetForValintatapajono(valintatapajonoOid: String): DBIO[List[(Instant, Valinnantulos)]]

}
