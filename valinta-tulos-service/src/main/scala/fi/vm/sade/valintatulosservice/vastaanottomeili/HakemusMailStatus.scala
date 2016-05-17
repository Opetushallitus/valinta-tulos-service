package fi.vm.sade.valintatulosservice.vastaanottomeili

import java.util.Date

case class HakemusMailStatus(hakemusOid: String, hakukohteet: List[HakukohdeMailStatus], hakuOid: String) {
  def anyMailToBeSent = hakukohteet.find(_.shouldMail).nonEmpty
}

case class HakukohdeMailStatus(hakukohdeOid: String, valintatapajonoOid: String, status: MailStatus.Value,
                               deadline: Option[Date], message: String, ehdollisestiHyvaksyttavissa: Boolean) {
  def shouldMail = status == MailStatus.SHOULD_MAIL
}

case class HakemusIdentifier(hakuOid: String, hakemusOid: String)

object MailStatus extends Enumeration {
  val NOT_MAILED, MAILED, SHOULD_MAIL, NEVER_MAIL = Value
}