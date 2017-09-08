package fi.vm.sade.valintatulosservice.valinnantulos

import java.text.SimpleDateFormat
import java.util.Date

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.ohjausparametrit.Ohjausparametrit
import fi.vm.sade.valintatulosservice.tarjonta.Haku
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, Valinnantulos, ValinnantulosUpdateStatus}
import fi.vm.sade.valintatulosservice.vastaanotto.VastaanottoUtils.laskeVastaanottoDeadline

class ErillishaunVastaanottoValidator(val haku: Haku,
                                      val hakukohdeOid: HakukohdeOid,
                                      val ohjausparametrit: Option[Ohjausparametrit],
                                      val valinnantulosRepository: ValinnantulosRepository with HakijaVastaanottoRepository)
  extends VastaanottoValidator with Logging {

  def onkoEhdollisestiVastaanotettavissa(valinnantulos: Valinnantulos) = false

}
