package fi.vm.sade.valintatulosservice.valintarekisteri.domain

import java.time.OffsetDateTime

case class ViestinnanOhjaus(hakukohdeOid: HakukohdeOid, valintatapajonoOid: ValintatapajonoOid, hakemusOid: HakemusOid,
                            previousCheck: Option[OffsetDateTime], sent: Option[OffsetDateTime],
                            done: Option[OffsetDateTime], message: String)
