package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakukohdeOid, TilanKuvausHashCode, ValinnantilanTarkenne, ValintatapajonoOid}
import slick.dbio.DBIO

trait TilanKuvausRepository {
  def storeTilanKuvaus(
                        tilanKuvausHashCode: TilanKuvausHashCode,
                        hakukohdeOid: HakukohdeOid,
                        valintatapajonoOid: ValintatapajonoOid,
                        hakemusOid: HakemusOid,
                        valinnantilanTarkenne: ValinnantilanTarkenne,
                        ehdollisenHyvaksymisenEhtoTekstiFI: Option[String],
                        ehdollisenHyvaksymisenEhtoTekstiSV: Option[String],
                        ehdollisenHyvaksymisenEhtoTekstiEN: Option[String]
                      ): DBIO[Unit]
}
