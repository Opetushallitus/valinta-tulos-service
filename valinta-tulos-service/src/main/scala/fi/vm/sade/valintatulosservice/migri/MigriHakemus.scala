package fi.vm.sade.valintatulosservice.migri

import scala.collection.mutable

case class MigriHakija(henkilotunnus: String,
                       henkiloOid: String,
                       sukunimi: String,
                       etunimet: String,
                       kansalaisuudet: List[String],
                       syntymaaika: String,
                       hakemukset: mutable.Set[MigriHakemus])

case class MigriHakemus(hakuOid: String,
                        hakuNimi: Map[String, String],
                        hakemusOid: String,
                        organisaatioOid: String,
                        organisaatioNimi: Map[String, String],
                        hakukohdeOid: String,
                        hakukohdeNimi: Map[String, String],
                        toteutusOid: String,
                        toteutusNimi: Map[String, String],
                        valintaTila: String,
                        vastaanottoTila: String,
                        ilmoittautuminenTila: String,
                        maksuvelvollisuus: String,
                        lukuvuosimaksu: String,
                        koulutuksenAlkamisvuosi: Int,
                        koulutuksenAlkamiskausi: String)
