package fi.vm.sade.valintatulosservice

import java.time.Instant
import java.util.concurrent.TimeUnit

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValinnantulosRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Ilmoittautuminen

import scala.concurrent.duration.Duration

class ValinnantulosService(valinnantulosRepository: ValinnantulosRepository) extends Logging {

  def storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid:String,
                                               valinnantulokset: List[ValinnantulosPatch],
                                               ifUnmodifiedSince: Instant,
                                               muokkaaja:String) = {
    val valinnanTulokset = valinnantulosRepository.runBlocking(
      valinnantulosRepository.getValinnantuloksetForValintatapajono(valintatapajonoOid),
      Duration(1, TimeUnit.SECONDS)
    ).map(v => v._2.hakemusOid -> v).toMap
    valinnantulokset.foreach(muutos => {
      valinnanTulokset.get(muutos.hakemusOid) match {
        case Some(v) =>
          val lastModified = v._1
          val edellinenVastaanottotila = v._2.vastaanottotila
          val edellinenIlmoittautumistila = v._2.ilmoittautumistila
          if (lastModified.isAfter(ifUnmodifiedSince)) {
            logger.warn(s"Hakemus ${muutos.hakemusOid} valintatapajonossa $valintatapajonoOid " +
              s"on muuttunut $lastModified lukemisajan $ifUnmodifiedSince jälkeen.")
          } else {
            logger.info(s"Käyttäjä ${muokkaaja} muokkasi " +
              s"hakemuksen ${muutos.hakemusOid} valinnan tulosta valintatapajonossa $valintatapajonoOid " +
              s"vastaanottotilasta $edellinenVastaanottotila tilaan ${muutos.vastaanottotila} ja " +
              s"ilmoittautumistilasta $edellinenIlmoittautumistila tilaan ${muutos.ilmoittautumistila}.")
            valinnantulosRepository.runBlocking(
              valinnantulosRepository.storeIlmoittautuminen(v._2.henkiloOid,
                Ilmoittautuminen(v._2.hakukohdeOid, muutos.ilmoittautumistila, muokkaaja, "selite")),
              Duration(1, TimeUnit.SECONDS)
            )
          }
        case None =>
          logger.warn(s"Hakemuksen ${muutos.hakemusOid} valinnan tulosta ei löydy " +
            s"valintatapajonosta $valintatapajonoOid.")
      }
    })
  }


}
