package fi.vm.sade.valintatulosservice.json

import java.util.Date
import fi.vm.sade.valintatulosservice.domain.Valintatila.Valintatila
import fi.vm.sade.valintatulosservice.domain.Vastaanotettavuustila.Vastaanotettavuustila
import fi.vm.sade.valintatulosservice.domain.{HakutoiveenIlmoittautumistila, HakutoiveenSijoittelunTilaTieto, Hakutoiveentulos, Ilmoittautumisaika, Valintatila, Vastaanotettavuustila}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{EhdollisenHyvaksymisenEhto, HakemusOid, HakukohdeOid, JonokohtainenTulostieto, ValintatapajonoOid}
import org.json4s.Extraction._
import org.json4s.JsonAST.{JArray, JObject}
import org.json4s.JsonDSL._
import org.json4s.{CustomSerializer, Formats}

/**
 * Custom serializer for JonokohtainenTulostieto to avoid json4s reflection issues with Scala 2.13 Enumerations.
 */
class JonokohtainenTulostietoSerializer extends CustomSerializer[JonokohtainenTulostieto]((formats: Formats) => ( {
  case x: JObject =>
    implicit val f = formats
    JonokohtainenTulostieto(
      oid = (x \ "oid").extract[ValintatapajonoOid],
      nimi = (x \ "nimi").extract[String],
      pisteet = (x \ "pisteet").extractOpt[BigDecimal],
      alinHyvaksyttyPistemaara = (x \ "alinHyvaksyttyPistemaara").extractOpt[BigDecimal],
      valintatila = Valintatila.withName((x \ "valintatila").extract[String]),
      julkaistavissa = (x \ "julkaistavissa").extract[Boolean],
      valintatapajonoPrioriteetti = (x \ "valintatapajonoPrioriteetti").extractOpt[Int],
      tilanKuvaukset = (x \ "tilanKuvaukset").extractOpt[Map[String, String]],
      ehdollisestiHyvaksyttavissa = (x \ "ehdollisestiHyvaksyttavissa").extract[Boolean],
      ehdollisenHyvaksymisenEhto = (x \ "ehdollisenHyvaksymisenEhto").extractOpt[EhdollisenHyvaksymisenEhto],
      varasijanumero = (x \ "varasijanumero").extractOpt[Int],
      eiVarasijatayttoa = (x \ "eiVarasijatayttoa").extract[Boolean],
      varasijat = (x \ "varasijat").extractOpt[Int],
      varasijasaannotKaytossa = (x \ "varasijasaannotKaytossa").extract[Boolean]
    )
}, {
  case t: JonokohtainenTulostieto =>
    implicit val f = formats
    ("oid" -> t.oid.toString) ~
      ("nimi" -> t.nimi) ~
      ("pisteet" -> t.pisteet) ~
      ("alinHyvaksyttyPistemaara" -> t.alinHyvaksyttyPistemaara) ~
      ("valintatila" -> t.valintatila.toString) ~
      ("julkaistavissa" -> t.julkaistavissa) ~
      ("valintatapajonoPrioriteetti" -> t.valintatapajonoPrioriteetti) ~
      ("tilanKuvaukset" -> t.tilanKuvaukset) ~
      ("ehdollisestiHyvaksyttavissa" -> t.ehdollisestiHyvaksyttavissa) ~
      ("ehdollisenHyvaksymisenEhto" -> decompose(t.ehdollisenHyvaksymisenEhto)) ~
      ("varasijanumero" -> t.varasijanumero) ~
      ("eiVarasijatayttoa" -> t.eiVarasijatayttoa) ~
      ("varasijat" -> t.varasijat) ~
      ("varasijasaannotKaytossa" -> t.varasijasaannotKaytossa)
}))


class HakutoiveentulosSerializer extends CustomSerializer[Hakutoiveentulos]((formats: Formats) => ( {
  case x: JObject =>
    implicit val f = formats
    // Extract enumerations as strings and convert manually to avoid json4s reflection issues with Scala 2.13 Enumerations
    val valintatila = Valintatila.withName((x \ "valintatila").extract[String])
    val vastaanottotila = (x \ "vastaanottotila").extract[String]
    val vastaanotettavuustila = Vastaanotettavuustila.withName((x \ "vastaanotettavuustila").extract[String])
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
      hyvaksyttyJaJulkaistuDate = (x \ "hyvaksyttyJaJulkaistuDate").extractOpt[Date],
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
      virkailijanTilat = HakutoiveenSijoittelunTilaTieto(valintatila, vastaanottotila, None, vastaanotettavuustila),
      jonokohtaisetTulostiedot = (x \ "jonokohtaisetTulostiedot").extract[List[JonokohtainenTulostieto]]
    )
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
      ("hyvaksyttyJaJulkaistuDate" -> decompose(tulos.hyvaksyttyJaJulkaistuDate)) ~
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
      ("showMigriURL" -> tulos.showMigriURL) ~
      ("pisteet" -> tulos.pisteet) ~
      ("jonokohtaisetTulostiedot" -> decompose(tulos.jonokohtaisetTulostiedot))
}
  )
)
