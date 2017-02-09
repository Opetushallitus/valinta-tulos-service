package fi.vm.sade.valintatulosservice.kela

case class Henkilo(henkilotunnus: String, sukunimi: String, etunimet: String, vastaanotot: Seq[Vastaanotto])

case class Vastaanotto(tutkintotyyppi: String,
                       organisaatio: String,
                       oppilaitos: String,
                       hakukohde: String,
                       tutkinnonlaajuus1: String,
                       tutkinnonlaajuus2: Option[String],
                       tutkinnontaso: Option[String],
                       vastaaottoaika: String,
                       alkamiskausipvm: String) {

}
