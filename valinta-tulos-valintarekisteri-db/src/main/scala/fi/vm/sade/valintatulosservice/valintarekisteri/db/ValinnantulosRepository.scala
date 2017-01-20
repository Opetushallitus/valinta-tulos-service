package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.time.Instant
import java.util.concurrent.TimeUnit

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Ilmoittautuminen, Valinnantulos}

import scala.concurrent.duration.Duration

trait ValinnantulosRepository extends ValintarekisteriRepository {

  def storeIlmoittautuminen(henkiloOid: String, ilmoittautuminen: Ilmoittautuminen, duration:Duration = Duration(1, TimeUnit.SECONDS))

  def getValinnantuloksetForValintatapajono(valintatapajonoOid:String, duration:Duration = Duration(1, TimeUnit.SECONDS)): List[(Instant, Valinnantulos)]

  def getTarjoajaForHakukohde(hakukohdeOid:String): String

}
