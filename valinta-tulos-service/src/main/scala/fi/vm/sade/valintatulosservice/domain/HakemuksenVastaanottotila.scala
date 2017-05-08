package fi.vm.sade.valintatulosservice.domain

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila._
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, ValintatapajonoOid}

case class HakemuksenVastaanottotila(hakemusOid: HakemusOid, valintatapajonoOid: Option[ValintatapajonoOid], vastaanottotila: Option[Vastaanottotila])
