package fi.vm.sade.valintatulosservice.kela

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakukohdeOid

case class Henkilo(henkilotunnus: String, sukunimi: String, etunimet: String, vastaanotot: Seq[Vastaanotto])

case class Vastaanotto(organisaatio: String,
                       oppilaitos: String,
                       hakukohde: HakukohdeOid,
                       tutkinnonlaajuus1: Option[String],
                       tutkinnonlaajuus2: Option[String],
                       tutkinnontaso: Option[String],
                       vastaaottoaika: String,
                       alkamiskausipvm: Option[String]) {

}
