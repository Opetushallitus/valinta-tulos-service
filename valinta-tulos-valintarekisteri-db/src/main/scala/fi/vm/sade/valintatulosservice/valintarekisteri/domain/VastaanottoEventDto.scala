package fi.vm.sade.valintatulosservice.valintarekisteri.domain

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila._

case class VastaanottoEventDto(
  valintatapajonoOid: ValintatapajonoOid,
  henkiloOid: String,
  hakemusOid: HakemusOid,
  hakukohdeOid: HakukohdeOid,
  hakuOid: HakuOid,
  tila: Vastaanottotila,
  ilmoittaja: String,
  selite: String
) {
  val fieldsWithNames = List(
    (valintatapajonoOid, "valintatapajonoOid"),
    (henkiloOid, "henkiloOid"),
    (hakemusOid, "hakemusOid"),
    (hakukohdeOid, "hakukohdeOid"),
    (hakuOid, "hakuOid"),
    (tila, "tila"),
    (ilmoittaja, "ilmoittaja"),
    (selite, "selite")
  )
  val errorMessages = fieldsWithNames.filter(_._1 == null).map(_._2 + " was null")
  assert(errorMessages.isEmpty, errorMessages.mkString(", "))
}
