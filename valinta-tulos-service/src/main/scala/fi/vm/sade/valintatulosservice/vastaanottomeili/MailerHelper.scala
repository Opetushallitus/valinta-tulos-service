package fi.vm.sade.valintatulosservice.vastaanottomeili

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakuOid, HakukohdeOid, ValintatapajonoOid}
import fi.vm.sade.valintatulosservice.vastaanottomeili.LahetysSyy.LahetysSyy


object MailerHelper {

  /** Splits each Ilmoitus so that Ilmoitus has only hakukohtees that share the same LahetysSyy. Groups the list of Ilmoituses by language and LahetysSyy.
    *
    * @param batch List of Ilmoitus to be processed
    * @return Map ,where key is tuple of (Language, LahetysSyy) and value is list of Ilmoituses
    */
  def splitAndGroupIlmoitus(batch: List[Ilmoitus]): Map[(String, LahetysSyy), List[Ilmoitus]] = {
    // If same ilmoitus has multiple LahetysSyy among its hakukohde, split it to multiple Ilmoitus
    val splitIlmoituses = batch.flatMap(splitIlmoitusByLahetysSyy)
    splitIlmoituses.groupBy(asiointikieliAndLahetysSyy)
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

  /** Read the name of the query object to the name of the lähetys when it's available. */
  def lahetyksenOtsikko(query: MailerQuery, ilmoitukset: List[Ilmoitus]): String =
    query match {
      case AllQuery => "Kaikki vastaanottosähköpostit"
      case HakuQuery(hakuOid) => hakuQueryOtsikko(hakuOid, ilmoitukset)
      case HakukohdeQuery(hakukohdeOid) => hakukohdeQueryOtsikko(ilmoitukset, hakukohdeOid)
      case HakemusQuery(hakemusOid) => s"Vastaanottosähköpostit hakemukselle $hakemusOid"
      case ValintatapajonoQuery(hakukohdeOid, valintatapajonoOid) =>
        valintajonoQueryOtsikko(ilmoitukset, hakukohdeOid, valintatapajonoOid)
    }

  private def valintajonoQueryOtsikko(ilmoitukset: List[Ilmoitus],
                                      hakukohdeOid: HakukohdeOid,
                                      valintatapajonoOid: ValintatapajonoOid): String =
    findHakukohdeNimi(ilmoitukset, hakukohdeOid)
      .map(nimi => s"Vastaanottosähköpostit hakukohteen $nimi ($hakukohdeOid) valintatapajonolle $valintatapajonoOid")
      .getOrElse(s"Vastaanottosähköpostit hakukohteen $hakukohdeOid valintatapajonolle $valintatapajonoOid")

  private def hakukohdeQueryOtsikko(ilmoitukset: List[Ilmoitus], hakukohdeOid: HakukohdeOid): String =
    findHakukohdeNimi(ilmoitukset, hakukohdeOid)
      .map(nimi => s"Vastaanottosähköpostit hakukohteelle $nimi ($hakukohdeOid)")
      .getOrElse(s"Vastaanottosähköpostit hakukohteelle $hakukohdeOid")

  private def hakuQueryOtsikko(hakuOid: HakuOid, ilmoitukset: List[Ilmoitus]): String =
    ilmoitukset.map(_.haku.nimi)
      .map(nimi => getFirstMatch(nimi, "fi", "sv", "en"))
      .find(_.isDefined)
      .flatten
      .map(nimi => s"Vastaanottosähköpostit haulle $nimi ($hakuOid)")
      .getOrElse(s"Vastaanottosähköpostit haulle $hakuOid")

  private def findHakukohdeNimi(ilmoitukset: List[Ilmoitus], hakukohdeOid: HakukohdeOid): Option[String] =
    ilmoitukset
      .flatMap(_.hakukohteet)
      .find(_.oid == hakukohdeOid)
      .flatMap(hk => getFirstMatch(hk.hakukohteenNimet, "fi", "sv", "en"))

  /** Find the first value from a map in order of the given keys. */
  private def getFirstMatch[K, V](map: Map[K, V], keys: K*): Option[V] = keys.find(map.contains).map(map(_))

}
