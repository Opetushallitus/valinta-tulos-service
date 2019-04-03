package fi.vm.sade.valintatulosemailer.ryhmasahkoposti

import fi.vm.sade.groupemailer.{Recipient, Replacement}
import fi.vm.sade.valintatulosemailer.valintatulos
import fi.vm.sade.valintatulosemailer.valintatulos.LahetysSyy._
import fi.vm.sade.utils.slf4j.Logging
import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}

case class Hakukohde(
                      oid: String,
                      nimi: String,
                      tarjoaja: String,
                      ehdollisestiHyvaksyttavissa: Boolean
                    )

object VTEmailerReplacement {
  def secureLink(secureLink: String) = Replacement("securelink", secureLink)

  def firstName(name: String) = Replacement("etunimi", name)

  def deadline(date: Option[DateTime]) = Replacement("deadline", deadlineText(date))

  def haunNimi(name: String) = Replacement("haunNimi", name)

  def hakukohteet(hakukohteet: List[Hakukohde]) = Replacement("hakukohteet", hakukohteet)

  def hakukohde(hakukohde: String) = Replacement("hakukohde", hakukohde)

  private def deadlineText(date: Option[DateTime]): String = date match {
    case Some(deadline) =>
      VTEmailerDeadlineFormat.create().print(deadline.withZone(DateTimeZone.forID("Europe/Helsinki")))
    case _ =>
      ""
  }
}

object VTRecipient extends Logging {
  def apply(valintatulosRecipient: valintatulos.Ilmoitus, language: String): Recipient = {

    def getTranslation(rawTranslations: Map[String, Option[String]]) = {

      def fixKey(key: String) = key.toLowerCase.replace("kieli_", "")

      val translations = rawTranslations
        .filter { case (key, value) => value.isDefined && !value.get.isEmpty }
        .map { case (key, value) => (fixKey(key), value) }

      translations.get(language.toLowerCase).orElse(translations.get("fi")).getOrElse(translations.head._2).get
    }

    def getHakukohtees: Replacement = {
      val hakukohteet = valintatulosRecipient.hakukohteet
      val lahetysSyy: LahetysSyy = hakukohteet.head.lahetysSyy
      if (hakukohteet.size == 1 && (lahetysSyy.equals(ehdollisen_periytymisen_ilmoitus) || lahetysSyy.equals(sitovan_vastaanoton_ilmoitus))) {
        VTEmailerReplacement.hakukohde(getTranslation(hakukohteet.head.hakukohteenNimet))
      } else if (lahetysSyy.equals(vastaanottoilmoitusKk) || lahetysSyy.equals(vastaanottoilmoitus2aste)) {
        VTEmailerReplacement.hakukohteet(hakukohteet.map(hakukohde =>
          Hakukohde(hakukohde.oid, getTranslation(hakukohde.hakukohteenNimet),
            getTranslation(hakukohde.tarjoajaNimet), hakukohde.ehdollisestiHyvaksyttavissa)
        ))
      } else {
        throw new IllegalArgumentException("Failed to add hakukohde information to recipient. Hakemus " + valintatulosRecipient.hakemusOid +
          ". LahetysSyy was " + hakukohteet.head.lahetysSyy + " and there was " + hakukohteet.size + "hakukohtees")
      }
    }

    val deadlineReplacement: Replacement = VTEmailerReplacement.deadline(valintatulosRecipient.deadline)
    logger.info(s"Deadline for hakemus '${valintatulosRecipient.hakemusOid}' was '${valintatulosRecipient.deadline}', which gave the deadlineText: '${deadlineReplacement.value}'.")

    val replacements = List(
      VTEmailerReplacement.firstName(valintatulosRecipient.etunimi),
      deadlineReplacement,
      VTEmailerReplacement.haunNimi(getTranslation(valintatulosRecipient.haku.nimi)),
      getHakukohtees
    ) ++ valintatulosRecipient.secureLink.map(VTEmailerReplacement.secureLink).toList

    Recipient(Some(valintatulosRecipient.hakijaOid), valintatulosRecipient.email, valintatulosRecipient.asiointikieli, replacements)
  }
}

private object VTEmailerDeadlineFormat {
  val pattern: String = "dd.MM.yyyy HH:mm"

  def create(): DateTimeFormatter = {
    DateTimeFormat.forPattern(pattern)
  }
}
