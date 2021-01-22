package fi.vm.sade.valintatulosservice.valinnantulos

import fi.vm.sade.valintatulosservice.ohjausparametrit.Ohjausparametrit
import fi.vm.sade.valintatulosservice.sijoittelu.JonoFinder
import fi.vm.sade.valintatulosservice.tarjonta.Haku
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakutoiveenValinnantulos
import fi.vm.sade.valintatulosservice.vastaanotto.VastaanottoUtils.{ehdollinenVastaanottoMahdollista}
import slick.dbio.{DBIO}
import scala.concurrent.ExecutionContext.Implicits.global

class SijoittelunVastaanottoValidator(val haku: Haku,
                                     val hakukohdeOid: HakukohdeOid,
                                     val ohjausparametrit: Ohjausparametrit,
                                     val valinnantulosRepository: ValinnantulosRepository with HakijaVastaanottoRepository)
  extends VastaanottoValidator {

  override def onkoEhdollisestiVastaanotettavissa(uusi: Valinnantulos): DBIO[Boolean] =
    valinnantulosRepository.getHakutoiveidenValinnantuloksetForHakemusDBIO(haku.oid, uusi.hakemusOid).flatMap(hakemuksenKaikkiValinnantulokset => {

      val merkitseväTulosForEveryHakutoiveInHakemus: List[HakijanHakutoive] = {
        def merkitsevänJononTulos(hakutoiveenJonot: List[HakijanHakutoive]): HakijanHakutoive = {
          JonoFinder.merkitseväJono(hakutoiveenJonot).map(_ match {
            case jono: HakutoiveenValinnantulos if jono.valintatapajonoOid == uusi.valintatapajonoOid => jono.copy(valinnantila = uusi.valinnantila, julkaistavissa = uusi.julkaistavissa)
            case jono => jono
          }).get
        }

        val maxHakutoiveSijoittelussa = hakemuksenKaikkiValinnantulokset.map(_.hakutoive).max
        (1 to maxHakutoiveSijoittelussa).map(hakutoive => hakemuksenKaikkiValinnantulokset.filter(_.hakutoive == hakutoive) match {
          case hakutoiveenJonot if hakutoiveenJonot.size == 0 => HakutoiveKesken(hakutoive) //Hakutoive ei vielä sijoittelussa
          case hakutoiveenJonot => merkitsevänJononTulos(hakutoiveenJonot)
        }).sortWith(_.hakutoive < _.hakutoive).toList
      }

      def findHakutoiveenValinnantulos(p: HakutoiveenValinnantulos => Boolean) = merkitseväTulosForEveryHakutoiveInHakemus.find(_ match {
        case x: HakutoiveenValinnantulos => p(x)
        case _ => false
      }).map(_.asInstanceOf[HakutoiveenValinnantulos])

      //Ensimmäinen hyväksytty hakutoive varalla olevan hakutoiveen jälkeen.
      //Välissä ei saa olla hakijan perumaa hakutoivetta.
      val ehdollisestiVastaanotettavaHakutoive =
        findHakutoiveenValinnantulos(_.valinnantila == Varalla).flatMap(varalla =>
          findHakutoiveenValinnantulos(t => t.valinnantila == Perunut && varalla.hakutoive < t.hakutoive) match {
            case None => findHakutoiveenValinnantulos(h => h.isHyväksytty() && varalla.hakutoive < h.hakutoive)
            case Some(perunutAfterVaralla) => findHakutoiveenValinnantulos(h => h.isHyväksytty() && varalla.hakutoive < h.hakutoive && perunutAfterVaralla.hakutoive > h.hakutoive)
          }).map(_.hakutoive)


      val ehdollinenVastaanotettavuus = findHakutoiveenValinnantulos(toive => toive.valintatapajonoOid == uusi.valintatapajonoOid).exists(vastaanotettavaHakutoive => {
        val sovellaKorkeakouluSääntöjä = haku.korkeakoulu && haku.sijoitteluJaPriorisointi
        val ehdollinenVastaanottoSallittu = ehdollinenVastaanottoMahdollista(ohjausparametrit)
        val hakutoiveOnEhdollisestiVastaanotettavissa = ehdollisestiVastaanotettavaHakutoive.exists(_ == vastaanotettavaHakutoive.hakutoive)

        sovellaKorkeakouluSääntöjä && ehdollinenVastaanottoSallittu && hakutoiveOnEhdollisestiVastaanotettavissa && julkaistavissa(uusi)
      })

    DBIO.successful(ehdollinenVastaanotettavuus)
  })
}
