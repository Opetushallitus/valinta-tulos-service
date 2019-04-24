package fi.vm.sade.valintatulosservice.vastaanottomeili

import fi.vm.sade.valintatulosservice.vastaanottomeili.LahetysSyy.LahetysSyy


class MailerHelper {

  /** Splits each Ilmoitus so that Ilmoitus has only hakukohtees that share the same LahetysSyy. Groups the list of Ilmoituses by language and LahetysSyy.
    *
    * @param batch List of Ilmoitus to be processed
    * @return Map ,where key is tuple of (Language, LahetysSyy) and value is list of Ilmoituses
    */
  def splitAndGroupIlmoitus(batch: List[Ilmoitus]): Map[(String, LahetysSyy), List[Ilmoitus]] = {
    // If same ilmoitus has multiple LahetysSyy among its hakukohde, split it to multiple Ilmoitus
    val splitedIlmoituses = batch.flatMap(splitIlmoitusByLahetysSyy)
    val groupedlmoituses = splitedIlmoituses.groupBy(asiointikieliAndLahetysSyy)
    groupedlmoituses
  }

  private def asiointikieliAndLahetysSyy(x: Ilmoitus): (String, LahetysSyy) = (x.asiointikieli, x.hakukohteet.head.lahetysSyy)

  private def splitIlmoitusByLahetysSyy(original: Ilmoitus): List[Ilmoitus] = {
    if (original.hakukohteet.isEmpty) throw new IllegalArgumentException("Empty hakukohdelist in hakemus " + original.hakemusOid)
    val hakukohteetByLahetysSyy = original.hakukohteet.groupBy(_.lahetysSyy)
    val res = for (
      syy <- hakukohteetByLahetysSyy
    ) yield {
      Ilmoitus(
        original.hakemusOid,
        original.hakijaOid,
        original.secureLink,
        original.asiointikieli,
        original.etunimi,
        original.email,
        original.deadline,
        hakukohteetByLahetysSyy(syy._1),
        original.haku
      )
    }
    res.toList
  }
}
