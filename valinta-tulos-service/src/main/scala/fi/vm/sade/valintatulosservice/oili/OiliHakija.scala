package fi.vm.sade.valintatulosservice.oili

import java.time.OffsetDateTime

case class OiliHakija(oppijanumero: String,
                      sukunimi: Option[String],
                      etunimet: Option[String],
                      kutsumanimi: Option[String],
                      henkilotunnus: Option[String],
                      asiointikieli: Option[String],
                      hakemukset: List[OiliHakemus])

case class OiliHakemus(jattoAjanhetki: Option[OffsetDateTime],
                       hakemusOid: String,
                       hakuOid: String,
                       hakuvuosi: Option[String],
                       hakukausi: Option[String],
                       lahiosoite: Option[String],
                       postinumero: Option[String],
                       postitoimipaikka: Option[String],
                       sahkoposti: Option[String],
                       puhelinnumero: Option[String],
                       hakukohteet: List[OiliHakukohde])

case class OiliHakukohde(jarjestyspaikkaOid: String,
                         hakukohdeOid: String,
                         toteutusOid: String,
                         koulutuskoodiUri: List[String],
                         valinnanTila: Option[String],
                         vastaanotonTila: Option[String],
                         onkoIlmoittauduttavissa: Boolean,
                         ehdollisestiHyvaksytty: Boolean,
                         ehdollisestiHyvaksyttySyy: Option[String],
                         ehdollisestiHyvaksyttyMuuKuvaus: Option[Kielistetty],
                         ilmoittautuminen: Option[String])

case class Kielistetty(fi: Option[String], sv: Option[String], en: Option[String])
