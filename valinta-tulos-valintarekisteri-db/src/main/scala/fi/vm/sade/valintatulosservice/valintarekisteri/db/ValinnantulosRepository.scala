package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.sql.Timestamp
import java.time.Instant
import java.util.concurrent.TimeUnit

import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Ilmoittautuminen, ValinnantilanTallennus, ValinnantuloksenOhjaus, Valinnantulos}
import slick.dbio.DBIO

import scala.concurrent.duration.Duration

trait ValinnantulosRepository extends ValintarekisteriRepository {

  def storeIlmoittautuminen(henkiloOid: String, ilmoittautuminen: Ilmoittautuminen, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]
  def storeValinnantuloksenOhjaus(ohjaus:ValinnantuloksenOhjaus, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]
  def storeValinnantila(tila:ValinnantilanTallennus, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]
  def storeValinnantilaOverridingTimestamp(tila:ValinnantilanTallennus, ifUnmodifiedSince: Option[Instant] = None, tilanViimeisinMuutos: Timestamp): DBIO[Unit]

  def updateValinnantuloksenOhjaus(ohjaus:ValinnantuloksenOhjaus, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]

  def getValinnantuloksetForValintatapajono(valintatapajonoOid:String, duration:Duration = Duration(1, TimeUnit.SECONDS)): List[(Instant, Valinnantulos)]

  def getTarjoajaForHakukohde(hakukohdeOid:String): String
  def getHakuForHakukohde(hakukohdeOid:String): String

  def deleteValinnantulos(muokkaaja:String, valinnantulos:Valinnantulos, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]
  def deleteIlmoittautuminen(henkiloOid: String, ilmoittautuminen: Ilmoittautuminen, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]
}
