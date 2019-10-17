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
                        ehdollisenHyvaksymisenEhtoTekstiFI: Option[String],
                        ehdollisenHyvaksymisenEhtoTekstiSV: Option[String],
                        ehdollisenHyvaksymisenEhtoTekstiEN: Option[String]
                      ): DBIO[Unit]
}
