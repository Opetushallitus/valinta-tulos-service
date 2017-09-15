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
                                     val ohjausparametrit: Option[Ohjausparametrit],
                                     val valinnantulosRepository: ValinnantulosRepository with HakijaVastaanottoRepository)
  extends VastaanottoValidator {

  override def onkoEhdollisestiVastaanotettavissa(uusi: Valinnantulos): DBIO[Boolean] = {

    def hakijanHakutoiveet(hakijanValinnantulokset: List[HakutoiveenValinnantulos]): List[HakijanHakutoive] = {
      def merkitsevänJononTulos(hakutoiveenJonot: List[HakijanHakutoive]): HakijanHakutoive = {
        JonoFinder.merkitseväJono(hakutoiveenJonot).map(_ match {
          case jono: HakutoiveenValinnantulos if jono.valintatapajonoOid == uusi.valintatapajonoOid => jono.copy(valinnantila = uusi.valinnantila, julkaistavissa = uusi.julkaistavissa)
          case jono => jono
        }).get
      }

      val maxHakutoiveSijoittelussa = hakijanValinnantulokset.map(_.hakutoive).max
      (1 to maxHakutoiveSijoittelussa).map(hakutoive => hakijanValinnantulokset.filter(_.hakutoive == hakutoive) match {
        case hakutoiveenJonot if hakutoiveenJonot.size == 0 => HakutoiveKesken(hakutoive) //Hakutoive ei vielä sijoittelussa
        case hakutoiveenJonot => merkitsevänJononTulos(hakutoiveenJonot)
      }).sortWith(_.hakutoive < _.hakutoive).toList
    }

    def ehdollinenVastaanotettavuus(hakutoiveet: List[HakijanHakutoive]): Boolean = {

      def findHakutoiveenValinnantulos(p: HakutoiveenValinnantulos => Boolean) = hakutoiveet.find(_ match {
        case x: HakutoiveenValinnantulos => p(x)
        case _ => false
      }).map(_.asInstanceOf[HakutoiveenValinnantulos])

      def ehdollisestiVastaanotettavaHakutoive =
        findHakutoiveenValinnantulos(_.valinnantila == Varalla).flatMap(varalla =>
          findHakutoiveenValinnantulos(t => t.valinnantila == Perunut && varalla.hakutoive < t.hakutoive) match {
            case None => findHakutoiveenValinnantulos(h => h.isHyväksytty() && varalla.hakutoive < h.hakutoive)
            case Some(perunutAfterVaralla) => findHakutoiveenValinnantulos(h => h.isHyväksytty() && varalla.hakutoive < h.hakutoive && perunutAfterVaralla.hakutoive > h.hakutoive)
          }).map(_.hakutoive)

      findHakutoiveenValinnantulos(toive => toive.valintatapajonoOid == uusi.valintatapajonoOid).exists(vastaanotettavaHakutoive => {
        val sovellaKorkeakouluSääntöjä = haku.korkeakoulu && haku.käyttääSijoittelua
        val ehdollinenVastaanottoSallittu = ehdollinenVastaanottoMahdollista(ohjausparametrit)
        val hakutoiveOnEhdollisestiVastaanotettavissa = ehdollisestiVastaanotettavaHakutoive.exists(_ == vastaanotettavaHakutoive.hakutoive)

        sovellaKorkeakouluSääntöjä && ehdollinenVastaanottoSallittu && hakutoiveOnEhdollisestiVastaanotettavissa && julkaistavissa(uusi)

      }) //Vastaanotto ei kohdistu merkitsevälle jonolle
    }

    valinnantulosRepository.getHakutoiveetForHakemusDBIO(haku.oid, uusi.hakemusOid)
      .flatMap(hakijanValinnantulokset => { DBIO.successful(hakijanHakutoiveet(hakijanValinnantulokset)) })
      .flatMap(hakutoiveet => DBIO.successful(ehdollinenVastaanotettavuus(hakutoiveet)))
  }
}
