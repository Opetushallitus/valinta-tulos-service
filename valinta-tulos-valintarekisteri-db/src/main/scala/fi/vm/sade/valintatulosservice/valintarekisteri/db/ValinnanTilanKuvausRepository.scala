package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakukohdeOid, ValinnanTilanKuvausHashCode, ValinnantilanTarkenne, ValintatapajonoOid}
import slick.dbio.DBIO

trait ValinnanTilanKuvausRepository {
  def storeValinnanTilanKuvaus(
                                valinnanTilanKuvausHashCode: ValinnanTilanKuvausHashCode,
                                hakukohdeOid: HakukohdeOid,
                                valintatapajonoOid: ValintatapajonoOid,
                                hakemusOid: HakemusOid,
                                valinnantilanTarkenne: ValinnantilanTarkenne,
                                valinnantilanKuvauksenTekstiFI: Option[String],
                                valinnantilanKuvauksenTekstiSV: Option[String],
                                valinnantilanKuvauksenTekstiEN: Option[String]
                      ): DBIO[Unit]
}
