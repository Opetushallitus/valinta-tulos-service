package fi.vm.sade.valintatulosservice.valintarekisteri.domain

case class HakukohdeTiedot(oid: HakukohdeOid, sijoittelematta: Boolean, julkaisematta: Boolean)

case class HaunHakukohdeTiedot(oid: HakuOid, hakukohteet: Set[HakukohdeTiedot])
