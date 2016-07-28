package fi.vm.sade.valintatulosservice.json

import java.util.Date

import fi.vm.sade.valintatulosservice.domain.Valintatila.Valintatila
import fi.vm.sade.valintatulosservice.domain.Vastaanotettavuustila.Vastaanotettavuustila
import fi.vm.sade.valintatulosservice.domain.{HakutoiveenIlmoittautumistila, HakutoiveenSijoittelunTilaTieto, Hakutoiveentulos}
import org.json4s.Extraction._
import org.json4s.JsonAST.JObject
import org.json4s.JsonDSL._
import org.json4s.{CustomSerializer, Formats}


class HakutoiveentulosSerializer extends CustomSerializer[Hakutoiveentulos]((formats: Formats) => ( {
  case x: JObject =>
    implicit val f = formats
    val valintatila = (x \ "valintatila").extract[Valintatila]
    val vastaanottotila = (x \ "vastaanottotila").extract[String]
    val vastaanotettavuustila = (x \ "vastaanotettavuustila").extract[Vastaanotettavuustila]
    Hakutoiveentulos(
      hakukohdeOid = (x \ "hakukohdeOid").extract[String],
      hakukohdeNimi = (x \ "hakukohdeNimi").extract[String],
      tarjoajaOid = (x \ "tarjoajaOid").extract[String],
      tarjoajaNimi = (x \ "tarjoajaNimi").extract[String],
      valintatapajonoOid = (x \ "valintatapajonoOid").extract[String],
      valintatila = valintatila,
      vastaanottotila = vastaanottotila,
      ilmoittautumistila = (x \ "ilmoittautumistila").extract[HakutoiveenIlmoittautumistila],
      vastaanotettavuustila = vastaanotettavuustila,
      vastaanottoDeadline = (x \ "vastaanottoDeadline").extractOpt[Date],
      viimeisinHakemuksenTilanMuutos = (x \ "viimeisinHakemuksenTilanMuutos").extractOpt[Date],
      viimeisinValintatuloksenMuutos = (x \ "viimeisinValintatuloksenMuutos").extractOpt[Date],
      jonosija = (x \ "jonosija").extractOpt[Int],
      varasijojaKaytetaanAlkaen = (x \ "varasijojaKaytetaanAlkaen").extractOpt[Date],
      varasijojaTaytetaanAsti = (x \ "varasijojaTaytetaanAsti").extractOpt[Date],
      varasijanumero = (x \ "varasijanumero").extractOpt[Int],
      julkaistavissa = (x \ "julkaistavissa").extract[Boolean],
      ehdollisestiHyvaksyttavissa = (x \ "ehdollisestiHyvaksyttavissa").extract[Boolean],
      tilanKuvaukset = (x \ "tilanKuvaukset").extract[Map[String, String]],
      pisteet = (x \ "pisteet").extractOpt[BigDecimal],
      virkailijanTilat = HakutoiveenSijoittelunTilaTieto(valintatila, vastaanottotila, vastaanotettavuustila))
  }, {
  case tulos: Hakutoiveentulos =>
    implicit val f = formats
    ("hakukohdeOid" -> tulos.hakukohdeOid) ~ ("hakukohdeNimi" -> tulos.hakukohdeNimi) ~
      ("tarjoajaOid" -> tulos.tarjoajaOid) ~ ("tarjoajaNimi" -> tulos.tarjoajaNimi) ~
      ("valintatapajonoOid" -> tulos.valintatapajonoOid) ~
      ("valintatila" -> decompose(tulos.valintatila)) ~
      ("vastaanottotila" -> decompose(tulos.vastaanottotila)) ~
      ("ilmoittautumistila" -> decompose(tulos.ilmoittautumistila)) ~
      ("vastaanotettavuustila" -> decompose(tulos.vastaanotettavuustila)) ~
      ("vastaanottoDeadline" -> decompose(tulos.vastaanottoDeadline)) ~
      ("viimeisinHakemuksenTilanMuutos" -> decompose(tulos.viimeisinHakemuksenTilanMuutos)) ~
      ("viimeisinValintatuloksenMuutos" -> decompose(tulos.viimeisinValintatuloksenMuutos)) ~
      ("jonosija" -> tulos.jonosija) ~
      ("varasijojaKaytetaanAlkaen" -> decompose(tulos.varasijojaKaytetaanAlkaen)) ~
      ("varasijojaTaytetaanAsti" -> decompose(tulos.varasijojaTaytetaanAsti)) ~
      ("varasijanumero" -> tulos.varasijanumero) ~
      ("julkaistavissa" -> tulos.julkaistavissa) ~
      ("ehdollisestiHyvaksyttavissa" -> tulos.ehdollisestiHyvaksyttavissa) ~
      ("tilanKuvaukset" -> tulos.tilanKuvaukset) ~
      ("pisteet" -> tulos.pisteet)
}
  )
)
