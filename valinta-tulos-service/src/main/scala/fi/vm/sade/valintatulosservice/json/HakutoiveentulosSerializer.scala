package fi.vm.sade.valintatulosservice.json

import java.time.LocalDateTime
import java.util.Date

import fi.vm.sade.valintatulosservice.domain.Valintatila.Valintatila
import fi.vm.sade.valintatulosservice.domain.Vastaanotettavuustila.Vastaanotettavuustila
import fi.vm.sade.valintatulosservice.domain.{HakutoiveenIlmoittautumistila, HakutoiveenSijoittelunTilaTieto, Hakutoiveentulos, Ilmoittautumisaika}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, ValintatapajonoOid}
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
      hakukohdeOid = (x \ "hakukohdeOid").extract[HakukohdeOid],
      hakukohdeNimi = (x \ "hakukohdeNimi").extract[String],
      tarjoajaOid = (x \ "tarjoajaOid").extract[String],
      tarjoajaNimi = (x \ "tarjoajaNimi").extract[String],
      valintatapajonoOid = (x \ "valintatapajonoOid").extract[ValintatapajonoOid],
      valintatila = valintatila,
      vastaanottotila = vastaanottotila,
      vastaanotonIlmoittaja = None,
      ilmoittautumistila = (x \ "ilmoittautumistila").extract[HakutoiveenIlmoittautumistila],
      ilmoittautumisenAikaleima = (x \ "ilmoittautumisenAikaleima").extractOpt[Date],
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
      ehdollisenHyvaksymisenEhtoKoodi = (x \ "ehdollisenHyvaksymisenEhtoKoodi").extractOpt[String],
      ehdollisenHyvaksymisenEhtoFI = (x \ "ehdollisenHyvaksymisenEhtoFI").extractOpt[String],
      ehdollisenHyvaksymisenEhtoSV = (x \ "ehdollisenHyvaksymisenEhtoSV").extractOpt[String],
      ehdollisenHyvaksymisenEhtoEN = (x \ "ehdollisenHyvaksymisenEhtoEN").extractOpt[String],
      tilanKuvaukset = (x \ "tilanKuvaukset").extract[Map[String, String]],
      pisteet = (x \ "pisteet").extractOpt[BigDecimal],
      virkailijanTilat = HakutoiveenSijoittelunTilaTieto(valintatila, vastaanottotila, None, vastaanotettavuustila))
  }, {
  case tulos: Hakutoiveentulos =>
    implicit val f = formats
    ("hakukohdeOid" -> tulos.hakukohdeOid.toString) ~ ("hakukohdeNimi" -> tulos.hakukohdeNimi) ~
      ("tarjoajaOid" -> tulos.tarjoajaOid) ~ ("tarjoajaNimi" -> tulos.tarjoajaNimi) ~
      ("valintatapajonoOid" -> tulos.valintatapajonoOid.toString) ~
      ("valintatila" -> decompose(tulos.valintatila)) ~
      ("vastaanottotila" -> decompose(tulos.vastaanottotila)) ~
      ("ilmoittautumistila" -> decompose(tulos.ilmoittautumistila)) ~
      ("ilmoittautumisenAikaleima" -> decompose(tulos.ilmoittautumisenAikaleima)) ~
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
      ("ehdollisenHyvaksymisenEhtoKoodi" -> tulos.ehdollisenHyvaksymisenEhtoKoodi) ~
      ("ehdollisenHyvaksymisenEhtoFI" -> tulos.ehdollisenHyvaksymisenEhtoFI) ~
      ("ehdollisenHyvaksymisenEhtoSV" -> tulos.ehdollisenHyvaksymisenEhtoSV) ~
      ("ehdollisenHyvaksymisenEhtoEN" -> tulos.ehdollisenHyvaksymisenEhtoEN) ~
      ("tilanKuvaukset" -> tulos.tilanKuvaukset) ~
      ("kelaURL" -> tulos.kelaURL) ~
      ("pisteet" -> tulos.pisteet)
}
  )
)
