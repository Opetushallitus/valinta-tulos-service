package fi.vm.sade.valintatulosservice.valintarekisteri.domain

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila

trait HakijanHakutoive {
  val hakutoive: Int

  def isHyväksytty() = false
  def isJulkaistu() = false
  def isKesken():Boolean

}

case class HakutoiveenValinnantulos(hakutoive: Int,
                                    prioriteetti: Option[Int],
                                    varasijanNumero:Option[Int],
                                    hakukohdeOid: HakukohdeOid,
                                    valintatapajonoOid: ValintatapajonoOid,
                                    hakemusOid: HakemusOid,
                                    valinnantila: Valinnantila,
                                    julkaistavissa: Option[Boolean],
                                    vastaanottotila: ValintatuloksenTila) extends HakijanHakutoive {

  override def isHyväksytty() = List(VarasijaltaHyvaksytty, Hyvaksytty).contains(valinnantila)

  override def isJulkaistu() = julkaistavissa.getOrElse(false)

  override def isKesken() = false
}

case class HakutoiveKesken(hakutoive: Int) extends HakijanHakutoive {
  override def isKesken() = true
}