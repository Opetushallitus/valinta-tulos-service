package fi.vm.sade.valintatulosservice.domain

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid}

case class Hakutoive(oid: HakukohdeOid, tarjoajaOid: String, nimi: String, tarjoajaNimi: String)
case class Hakemus(oid: HakemusOid, hakuOid: HakuOid, henkiloOid: String, asiointikieli: String, toiveet: List[Hakutoive], henkilotiedot: Henkilotiedot, maksuvelvollisuudet: Map[String, String])
case class Henkilotiedot(kutsumanimi: Option[String], email: Option[String], hasHetu: Boolean, kansalaisuudet: List[String])
