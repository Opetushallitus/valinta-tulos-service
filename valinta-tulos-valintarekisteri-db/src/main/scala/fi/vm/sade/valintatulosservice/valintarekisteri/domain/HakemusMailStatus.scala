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
  def anyMailToBeSent = hakukohteet.exists(_.shouldMail)
}

case class HakukohdeMailStatus(hakukohdeOid: HakukohdeOid,
                               valintatapajonoOid: ValintatapajonoOid,
                               status: MailStatus.Value,
                               reasonToMail: Option[MailReason.Value],
                               deadline: Option[Date],
                               message: String,
                               valintatila: Valintatila,
                               vastaanottotila: Vastaanottotila,
                               ehdollisestiHyvaksyttavissa: Boolean) {
  def shouldMail = status == MailStatus.SHOULD_MAIL
}

object MailStatus extends Enumeration {
  val NOT_MAILED, MAILED, SHOULD_MAIL, NEVER_MAIL = Value
}

object MailReason extends Enumeration {
  val VASTAANOTTOILMOITUS,
  EHDOLLISEN_PERIYTYMISEN_ILMOITUS,
  SITOVAN_VASTAANOTON_ILMOITUS = Value
}
