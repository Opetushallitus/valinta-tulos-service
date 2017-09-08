package fi.vm.sade.valintatulosservice.valinnantulos

import java.text.SimpleDateFormat
import java.util.Date

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.ohjausparametrit.Ohjausparametrit
import fi.vm.sade.valintatulosservice.sijoittelu.JonoFinder
import fi.vm.sade.valintatulosservice.tarjonta.Haku
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{ValinnantulosRepository, HakijaVastaanottoRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakutoiveenValinnantulos
import fi.vm.sade.valintatulosservice.vastaanotto.VastaanottoUtils.{ehdollinenVastaanottoMahdollista, laskeVastaanottoDeadline}

class SijoittelunVastaanottoValidator(val haku: Haku,
                                      val hakukohdeOid: HakukohdeOid,
                                      val ohjausparametrit: Option[Ohjausparametrit],
                                      val valinnantulosRepository: ValinnantulosRepository with HakijaVastaanottoRepository)
  extends VastaanottoValidator with Logging {

  override def onkoEhdollisestiVastaanotettavissa(uusi: Valinnantulos): Boolean = {

    def hakijanHakutoiveet():List[HakijanHakutoive] = {
      def merkitsevänJononTulos(hakutoiveenJonot:List[HakijanHakutoive]): HakijanHakutoive = {
        JonoFinder.merkitseväJono(hakutoiveenJonot).map(_ match {
          case jono:HakutoiveenValinnantulos if jono.valintatapajonoOid == uusi.valintatapajonoOid => jono.copy(valinnantila = uusi.valinnantila, julkaistavissa = uusi.julkaistavissa)
          case jono => jono
        }).get
      }

      val hakijanValinnantulokset = valinnantulosRepository.getHakutoiveetForHakemus(haku.oid, uusi.hakemusOid)
      val maxHakutoiveSijoittelussa = hakijanValinnantulokset.map(_.hakutoive).max
      (1 to maxHakutoiveSijoittelussa).map(hakutoive => hakijanValinnantulokset.filter(_.hakutoive == hakutoive) match {
        case hakutoiveenJonot if hakutoiveenJonot.size == 0 => HakutoiveKesken(hakutoive) //Hakutoive ei vielä sijoittelussa
        case hakutoiveenJonot => merkitsevänJononTulos(hakutoiveenJonot)
      }).sortWith(_.hakutoive < _.hakutoive).toList
    }

    val hakutoiveet: List[HakijanHakutoive] = hakijanHakutoiveet()

    def findHakutoiveenValinnantulos(p: HakutoiveenValinnantulos => Boolean) = hakutoiveet.find(_ match {
      case x:HakutoiveenValinnantulos => p(x)
      case _ => false
    }).map(_.asInstanceOf[HakutoiveenValinnantulos])

    def ehdollisestiVastaanotettavaHakutoive =
      findHakutoiveenValinnantulos(_.valinnantila == Varalla).flatMap(varalla =>
        findHakutoiveenValinnantulos(t => t.valinnantila == Perunut && varalla.hakutoive < t.hakutoive) match {
          case None => findHakutoiveenValinnantulos(h => h.isHyväksytty() && varalla.hakutoive < h.hakutoive)
          case Some(perunutAfterVaralla) => findHakutoiveenValinnantulos(h => h.isHyväksytty() && varalla.hakutoive < h.hakutoive && perunutAfterVaralla.hakutoive > h.hakutoive)
        }).map(_.hakutoive)

    findHakutoiveenValinnantulos(toive => toive.valintatapajonoOid == uusi.valintatapajonoOid).map(vastaanotettavaHakutoive => {
      val sovellaKorkeakouluSääntöjä = haku.korkeakoulu && haku.käyttääSijoittelua
      val ehdollinenVastaanottoSallittu = ehdollinenVastaanottoMahdollista(ohjausparametrit)
      val hakutoiveOnEhdollisestiVastaanotettavissa = ehdollisestiVastaanotettavaHakutoive.exists(_ == vastaanotettavaHakutoive.hakutoive)

      sovellaKorkeakouluSääntöjä && ehdollinenVastaanottoSallittu && hakutoiveOnEhdollisestiVastaanotettavissa && julkaistavissa(uusi)

    }).getOrElse(false) //Vastaanotto ei kohdistu merkitsevälle jonolle
  }
}