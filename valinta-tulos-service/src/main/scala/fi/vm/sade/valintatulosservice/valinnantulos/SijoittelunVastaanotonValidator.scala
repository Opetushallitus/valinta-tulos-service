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

class SijoittelunVastaanotonValidator(haku: Haku,
                                      hakukohdeOid: HakukohdeOid,
                                      ohjausparametrit: Option[Ohjausparametrit],
                                      valinnantulosRepository: ValinnantulosRepository with HakijaVastaanottoRepository) extends Logging {

  private lazy val hyvaksyttyJaJulkaistuDatesForHakukohde = valinnantulosRepository.findHyvaksyttyJulkaistuDatesForHakukohde(hakukohdeOid)
  private lazy val tuloksetJulkaistavissa = ohjausparametrit.flatMap(_.tulostenJulkistusAlkaa).map(_.isBeforeNow()).getOrElse(ohjausparametrit.isDefined)

  val sitovaTaiEhdollinenVastaanotto = List(ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
  val keskenTaiVastaanottanutToisenPaikan = List(ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN, ValintatuloksenTila.KESKEN)
  val keskenTaiEhdollisestiVastaanottanut = List(ValintatuloksenTila.KESKEN, ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT)
  val virkailijanHyvaksytytTilat = List(Perunut, Peruutettu, Hyvaksytty, HyvaksyttyVarasijalta)

  private def merkitsevänJononTulos(uusi: Valinnantulos, hakutoiveet: List[HakijanHakutoive]): HakijanHakutoive = {
    JonoFinder.merkitseväJono(hakutoiveet).map(_ match {
      case jono:HakutoiveenValinnantulos if jono.valintatapajonoOid == uusi.valintatapajonoOid => jono.copy(valinnantila = uusi.valinnantila, julkaistavissa = uusi.julkaistavissa)
      case jono => jono
    }).get
  }

  private def hakijanHakutoiveet(uusi: Valinnantulos):List[HakijanHakutoive] = {
    val hakijanValinnantulokset = valinnantulosRepository.getHakutoiveetForHakemus(haku.oid, uusi.hakemusOid)
    val maxHakutoiveSijoittelussa = hakijanValinnantulokset.map(_.hakutoive).max
    (1 to maxHakutoiveSijoittelussa).map(hakutoive => hakijanValinnantulokset.filter(_.hakutoive == hakutoive) match {
      case list if list.size == 0 => HakutoiveKesken(hakutoive) //Hakutoive ei vielä sijoittelussa
      case list => merkitsevänJononTulos(uusi, list)
    }).sortWith(_.hakutoive < _.hakutoive).toList
  }

  private def onkoJulkaistu(vastaanotettavaHakutoive: HakijanHakutoive, hakutoiveet: List[HakijanHakutoive]) = {
    val kaikkiJonotJulkaistu = !hakutoiveet.exists(!_.isJulkaistu()) //TODO: Ota mukaan jonot, joissa tämä hakemus ei ole?
    tuloksetJulkaistavissa && vastaanotettavaHakutoive.isJulkaistu() && kaikkiJonotJulkaistu
  }

  def findHakutoiveenValinnantulos(hakutoiveet:List[HakijanHakutoive], p: HakutoiveenValinnantulos => Boolean) = hakutoiveet.find(_ match {
    case x:HakutoiveenValinnantulos => p(x)
    case _ => false
  }).map(_.asInstanceOf[HakutoiveenValinnantulos])

  private def onkoSitovastiVastaanotettavissa(uusi: Valinnantulos): Boolean = {
    val hakutoiveet: List[HakijanHakutoive] = hakijanHakutoiveet(uusi)

    findHakutoiveenValinnantulos(hakutoiveet, toive => toive.valintatapajonoOid == uusi.valintatapajonoOid).map(vastaanotettavaHakutoive => {
       onkoJulkaistu(vastaanotettavaHakutoive, hakutoiveet)
    }).getOrElse(false) //Vastaanotto ei kohdistu merkitsevälle jonolle
  }

  private def onkoEhdollisestiVastaanotettavissa(uusi: Valinnantulos): Boolean = {

    val hakutoiveet: List[HakijanHakutoive] = hakijanHakutoiveet(uusi)

    def findHakutoiveenValinnantulosPA = findHakutoiveenValinnantulos(hakutoiveet, _:HakutoiveenValinnantulos => Boolean)

    def ehdollisestiVastaanotettavaHakutoive =
      findHakutoiveenValinnantulosPA(_.valinnantila == Varalla).flatMap(varalla =>
        findHakutoiveenValinnantulosPA(t => t.valinnantila == Perunut && varalla.hakutoive < t.hakutoive) match {
          case None => findHakutoiveenValinnantulosPA(h => h.isHyväksytty() && varalla.hakutoive < h.hakutoive)
          case Some(perunutAfterVaralla) => findHakutoiveenValinnantulosPA(h => h.isHyväksytty() && varalla.hakutoive < h.hakutoive && perunutAfterVaralla.hakutoive > h.hakutoive)
        }).map(_.hakutoive)

    findHakutoiveenValinnantulosPA(toive => toive.valintatapajonoOid == uusi.valintatapajonoOid).map(vastaanotettavaHakutoive => {
      val sovellaKorkeakouluSääntöjä = haku.korkeakoulu && haku.käyttääSijoittelua
      val ehdollinenVastaanottoSallittu = ehdollinenVastaanottoMahdollista(ohjausparametrit)
      val hakutoiveOnEhdollisestiVastaanotettavissa = ehdollisestiVastaanotettavaHakutoive.exists(_ == vastaanotettavaHakutoive.hakutoive)

      sovellaKorkeakouluSääntöjä && ehdollinenVastaanottoSallittu && hakutoiveOnEhdollisestiVastaanotettavissa && onkoJulkaistu(vastaanotettavaHakutoive, hakutoiveet)

    }).getOrElse(false) //Vastaanotto ei kohdistu merkitsevälle jonolle
  }

  def validateVastaanotto(uusi: Valinnantulos, vanha: Valinnantulos): Either[ValinnantulosUpdateStatus, Unit] = {
    def left(msg:String) = Left(new ValinnantulosUpdateStatus(400, msg, uusi.valintatapajonoOid, uusi.hakemusOid))

    lazy val henkilönVastaanottoDeadline = laskeVastaanottoDeadline(ohjausparametrit, hyvaksyttyJaJulkaistuDatesForHakukohde.get(uusi.henkiloOid)).map(_.toDate)

    (vanha.vastaanottotila, uusi.vastaanottotila) match {
      case (_, ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN) => Right()
      case (ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN, x) if keskenTaiVastaanottanutToisenPaikan.contains(x) => Right()
      case (ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN, x) => left(s"Hakija on vastaanottanut toisen paikan")
      case (_, ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA) if henkilönVastaanottoDeadline.exists(_.after(new Date())) => left(s"""Hakijakohtaista määräaikaa ${new SimpleDateFormat("dd-MM-yyyy").format(henkilönVastaanottoDeadline)} kohteella ${hakukohdeOid} : ${uusi.vastaanottotila} ei ole vielä ohitettu.""")
      case (_, ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA) => Right()
      case (_, u) if !sitovaTaiEhdollinenVastaanotto.contains(u) => Right()
      case (v, u) if v == u => Right() //TODO valinnantilan, julkaisun muutos?!
      case (_, ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT) if onkoEhdollisestiVastaanotettavissa(uusi) => Right()
      case (_, ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT) => left(s"Hakutoivetta ei voi ottaa ehdollisesti vastaan")
      case (_, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI) if !virkailijanHyvaksytytTilat.contains(uusi.valinnantila) => left(s"""Ei voi tallentaa vastaanottotietoa, koska hakijalle näytettävä tila on "${uusi.valinnantila}"""")
      case (_, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI) if !onkoSitovastiVastaanotettavissa(uusi) => left(s"""Ei voi tallentaa vastaanottotietoa, koska hakemuksen valinnantulokset eivät ole julkaistavissa""")
      case (_, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI) => Right()
    }
  }
}