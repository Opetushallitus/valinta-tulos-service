package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.time.Instant
import java.util.concurrent.TimeUnit

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Ilmoittautuminen, ValinnantuloksenOhjaus, Valinnantulos}
import slick.dbio.DBIO

import scala.concurrent.duration.Duration

trait ValinnantulosRepository extends ValintarekisteriRepository {

  def storeIlmoittautuminen(henkiloOid: String, ilmoittautuminen: Ilmoittautuminen, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]
  def storeValinnantuloksenOhjaus(ohjaus:ValinnantuloksenOhjaus, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]

  def getValinnantuloksetForValintatapajono(valintatapajonoOid:String, duration:Duration = Duration(1, TimeUnit.SECONDS)): List[(Instant, Valinnantulos)]

  def getTarjoajaForHakukohde(hakukohdeOid:String): String
  def getHakuForHakukohde(hakukohdeOid:String): String

}
