package fi.vm.sade.valintatulosservice.valintarekisteri.domain

import java.util.Date

import fi.vm.sade.valintatulosservice.domain.Valintatila.Valintatila
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila._

case class HakemusMailStatus(hakijaOid: String,
                             hakemusOid: HakemusOid,
                             asiointikieli: String,
                             kutsumanimi: String,
                             email: String,
                             hasHetu: Boolean,
                             hakuOid: HakuOid,
                             hakukohteet: List[HakukohdeMailStatus]) {
  def anyMailToBeSent: Boolean = hakukohteet.exists(_.shouldMail)
}

case class HakukohdeMailStatus(hakukohdeOid: HakukohdeOid,
                               valintatapajonoOid: ValintatapajonoOid,
                               reasonToMail: Option[MailReason],
                               deadline: Option[Date],
                               message: String,
                               valintatila: Valintatila,
                               vastaanottotila: Vastaanottotila,
                               ehdollisestiHyvaksyttavissa: Boolean) {
  def shouldMail: Boolean = reasonToMail.isDefined
}

sealed trait MailReason
case object Vastaanottoilmoitus extends MailReason {
  override def toString: String = "VASTAANOTTOILMOITUS"
}
case object EhdollisenPeriytymisenIlmoitus extends MailReason {
  override def toString: String = "EHDOLLISEN_PERIYTYMISEN_ILMOITUS"
}
case object SitovanVastaanotonIlmoitus extends MailReason {
  override def toString: String = "SITOVAN_VASTAANOTON_ILMOITUS"
}

object MailReason {
  private val valueMapping = Map(
    "VASTAANOTTOILMOITUS" -> Vastaanottoilmoitus,
    "EHDOLLISEN_PERIYTYMISEN_ILMOITUS" -> EhdollisenPeriytymisenIlmoitus,
    "SITOVAN_VASTAANOTON_ILMOITUS" -> SitovanVastaanotonIlmoitus
  )
  def apply(s: String): MailReason = {
    valueMapping.getOrElse(s, throw new IllegalArgumentException(s"Unknown MailReason $s, expected one of ${valueMapping.keys.mkString(", ")}"))
  }
}
