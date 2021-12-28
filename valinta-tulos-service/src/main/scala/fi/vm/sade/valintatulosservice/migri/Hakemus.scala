package fi.vm.sade.valintatulosservice.migri

import fi.vm.sade.valintatulosservice.oppijanumerorekisteri.Henkilo

case class Hakija(henkilo: Henkilo,
                  vastaanotot: Seq[Hakemus])

case class Hakemus(hakuOid: String,
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
                   koulutuksenAlkamisvuosi: String,
                   koulutuksenAlkamiskausi: String)
