package fi.vm.sade.valintatulosservice

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila._
import fi.vm.sade.sijoittelu.domain._
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.sijoittelu.ValintatulosRepository
import fi.vm.sade.valintatulosservice.tarjonta.{Haku, HakuService}

class VastaanottoService(hakuService: HakuService, valintatulosService: ValintatulosService, tulokset: ValintatulosRepository) {

  val sallitutVastaanottotilat: Set[ValintatuloksenTila] = Set(VASTAANOTTANUT, EHDOLLISESTI_VASTAANOTTANUT, PERUNUT)

  def vastaanota(hakuOid: String, vastaanotettavaHakemusOid: String, vastaanotto: Vastaanotto) {
    val haku = hakuService.getHaku(hakuOid).getOrElse(throw new IllegalArgumentException("Hakua ei löydy"))
    val hakemuksenTulos = valintatulosService.hakemuksentulos(hakuOid, vastaanotettavaHakemusOid).getOrElse(throw new IllegalArgumentException("Hakemusta ei löydy"))
    val hakutoive = hakemuksenTulos.findHakutoive(vastaanotto.hakukohdeOid).getOrElse(throw new IllegalArgumentException("Hakutoivetta ei löydy"))
    val haluttuTila = ValintatuloksenTila.valueOf(vastaanotto.tila.toString)
    val vastaanotettavaHakuKohdeOid = vastaanotto.hakukohdeOid

    val tarkistettavatHakemukset = korkeakouluYhteishaunVastaanottoonLiittyvienHakujenHakemukset(haku, hakemuksenTulos.hakijaOid, haluttuTila).
      filter(_.hakemusOid != vastaanotettavaHakemusOid)

    tarkistaEttaEiVastaanottoja(tarkistettavatHakemukset, haluttuTila, hakutoive, vastaanotettavaHakemusOid, vastaanotettavaHakuKohdeOid)

    tarkistaHakutoiveenJaValintatuloksenTila(hakutoive, haluttuTila)

    val tallennettavaTila = vastaanotaSitovastiJosKorkeakouluYhteishaku(haku, haluttuTila)
    tulokset.modifyValintatulos(
      vastaanotto.hakukohdeOid,
      hakutoive.valintatapajonoOid,
      vastaanotettavaHakemusOid
    ) { valintatulos => valintatulos.setTila(tallennettavaTila, vastaanotto.selite, vastaanotto.muokkaaja) }

    peruMuutHyvaksytyt(tarkistettavatHakemukset, vastaanotto, haku, vastaanotettavaHakemusOid, vastaanotettavaHakuKohdeOid)
  }

  def vastaanotaHakukohde(personOid:String, vastaanotto: Vastaanotto) {
    val hakemuksenTulos = valintatulosService.hakemustenTulosByHakukohdeAndPerson(vastaanotto.hakukohdeOid, personOid).getOrElse(throw new IllegalArgumentException("Hakemusta ei löydy"))
    vastaanota(hakemuksenTulos.hakuOid, hakemuksenTulos.hakemusOid, vastaanotto)
  }

  private def tarkistaHakutoiveenJaValintatuloksenTila(hakutoive: Hakutoiveentulos, tila: ValintatuloksenTila) {
    if (!sallitutVastaanottotilat.contains(tila)) {
      throw new IllegalArgumentException("Ei-hyväksytty vastaanottotila: " + tila)
    }
    if (List(VASTAANOTTANUT, PERUNUT).contains(tila) && !List(Vastaanotettavuustila.vastaanotettavissa_ehdollisesti, Vastaanotettavuustila.vastaanotettavissa_sitovasti).contains(hakutoive.vastaanotettavuustila)) {
      throw new IllegalArgumentException("Väärä vastaanotettavuustila kohteella " + hakutoive.hakukohdeOid + ": " + hakutoive.vastaanotettavuustila.toString + " (yritetty muutos: " + tila + ")")
    }
    if (tila == EHDOLLISESTI_VASTAANOTTANUT && hakutoive.vastaanotettavuustila != Vastaanotettavuustila.vastaanotettavissa_ehdollisesti) {
      throw new IllegalArgumentException("Väärä vastaanotettavuustila kohteella " + hakutoive.hakukohdeOid + ": " + hakutoive.vastaanotettavuustila.toString + " (yritetty muutos: " + tila + ")")
    }
  }

  private def korkeakouluYhteishaunVastaanottoonLiittyvienHakujenHakemukset(haku: Haku, personOid: String, tila: ValintatuloksenTila): Set[Hakemuksentulos] =
    if (haku.korkeakoulu && haku.yhteishaku && List(VASTAANOTTANUT, EHDOLLISESTI_VASTAANOTTANUT).contains(tila)) {
      (Set(haku.oid) ++ hakuService.findLiittyvatHaut(haku)).flatMap(valintatulosService.hakemuksentuloksetByPerson(_, personOid))
    } else {
      Set()
    }

  private def tarkistaEttaEiVastaanottoja(muutHakemukset: Set[Hakemuksentulos], tila: ValintatuloksenTila, hakutoive: Hakutoiveentulos, vastaanotettavaHakemusOid: String, vastaanotettavaHakuKohdeOid: String) {
    muutHakemukset.foreach(tulos => {
      val hakemuksenMuutHakuToiveet: List[Hakutoiveentulos] = tulos.hakutoiveet.filter(toive => !(tulos.hakemusOid == vastaanotettavaHakemusOid && toive.hakukohdeOid == vastaanotettavaHakuKohdeOid) )
      val vastaanotettu = hakemuksenMuutHakuToiveet.find(toive => List(Vastaanottotila.vastaanottanut, Vastaanottotila.ehdollisesti_vastaanottanut).contains(toive.vastaanottotila))
      if (vastaanotettu.isDefined) {
        throw PriorAcceptanceException(tulos.hakuOid,  vastaanotettu.get.hakukohdeOid,  vastaanotettu.get.vastaanottotila, tila, hakutoive.hakukohdeOid)
      }
    })
  }


  private def peruMuutHyvaksytyt(muutHakemukset: Set[Hakemuksentulos], vastaanotto: Vastaanotto, vastaanotonHaku: Haku, vastaanotettavaHakemusOid: String, vastaanotettavaHakuKohdeOid: String) {
    muutHakemukset.foreach(hakemus => {
      val peruttavatKohteet = hakemus.hakutoiveet.
        filter(toive => !(hakemus.hakemusOid == vastaanotettavaHakemusOid && toive.hakukohdeOid == vastaanotettavaHakuKohdeOid) ).
        filter(toive => Valintatila.isHyväksytty(toive.valintatila) && toive.vastaanottotila == Vastaanottotila.kesken)
      peruttavatKohteet.foreach(kohde =>
        tulokset.modifyValintatulos(
          kohde.hakukohdeOid,
          kohde.valintatapajonoOid,
          hakemus.hakemusOid
        ) { valintatulos => valintatulos.setTila(
          ValintatuloksenTila.PERUNUT,
          vastaanotto.tila + " paikan " + vastaanotto.hakukohdeOid + " toisesta hausta " + vastaanotonHaku.oid,
          vastaanotto.muokkaaja)
        }
      )
    })
  }

  private def vastaanotaSitovastiJosKorkeakouluYhteishaku(haku: Haku, tila: ValintatuloksenTila): ValintatuloksenTila =
    if (tila == VASTAANOTTANUT && haku.korkeakoulu) {
      ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI
    } else {
      tila
    }
}


case class PriorAcceptanceException(hakuOid:String, hakukohdeOid: String, estavaTila: Vastaanottotila.Vastaanottotila, yritettyTila: ValintatuloksenTila, yritettyKohde: String) extends IllegalArgumentException("Väärä vastaanottotila toisen haun " + hakuOid + " kohteella " + hakukohdeOid + ": " + estavaTila + " (yritetty muutos: " + yritettyTila + " " + yritettyKohde + ")")
