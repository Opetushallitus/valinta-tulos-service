package fi.vm.sade.valintatulosservice.migri

case class MigriHakija(henkilotunnus: Option[String],
                       henkiloOid: String,
                       sukunimi: Option[String],
                       etunimet: Option[String],
                       kansalaisuudet: Option[List[String]],
                       syntymaaika: Option[String],
                       hakemukset: Set[MigriHakemus])

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
                        maksuvelvollisuus: Option[String],
                        lukuvuosimaksu: Option[String],
                        koulutuksenAlkamisvuosi: Option[Int],
                        koulutuksenAlkamiskausi: Option[String])
