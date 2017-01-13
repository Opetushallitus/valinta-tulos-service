package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.time.Instant

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Ilmoittautuminen, ValinnanTulos}
import slick.dbio.DBIO

trait ValinnantulosRepository extends ValintarekisteriRepository {

  def storeIlmoittautuminen(henkiloOid: String, ilmoittautuminen: Ilmoittautuminen): DBIO[Unit]

  def getValinnanTuloksetForValintatapajono(valintatapajonoOid: String): DBIO[List[(Instant, ValinnanTulos)]]

}
