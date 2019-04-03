package fi.vm.sade.valintatulosemailer.valintatulos

import java.util.Date

import fi.vm.sade.valintatulosemailer.valintatulos.LahetysSyy.LahetysSyy
import org.joda.time.DateTime

case class VtsPollResult(complete: Boolean,
                         candidatesProcessed: Int,
                         started: Date,
                         mailables: List[Ilmoitus])

case class Ilmoitus(
                     hakemusOid: String,
                     hakijaOid: String,
                     secureLink: Option[String],
                     asiointikieli: String,
                     etunimi: String,
                     email: String,
                     deadline: Option[DateTime],
                     hakukohteet: List[Hakukohde],
                     haku: Haku
                   )

case class Hakukohde(
                      oid: String,
                      ehdollisestiHyvaksyttavissa: Boolean,
                      hakukohteenNimet: Map[String, Option[String]],
                      tarjoajaNimet: Map[String, Option[String]],
                      lahetysSyy: LahetysSyy
                    )

case class Haku(
                 oid: String,
                 nimi: Map[String, Option[String]]
               )

case class LahetysKuittaus(
                            hakemusOid: String,
                            hakukohteet: List[String],
                            mediat: List[String]
                          )

object LahetysKuittaus {
  def apply(recipient: Ilmoitus): LahetysKuittaus = {
    new LahetysKuittaus(recipient.hakemusOid, recipient.hakukohteet.map(_.oid), List("email"))
  }
}

object LahetysSyy {
  type LahetysSyy = String
  val vastaanottoilmoitusKk: LahetysSyy = "VASTAANOTTOILMOITUS_KK"
  val vastaanottoilmoitus2aste: LahetysSyy = "VASTAANOTTOILMOITUS_2_ASTE"
  val ehdollisen_periytymisen_ilmoitus: LahetysSyy = "EHDOLLISEN_PERIYTYMISEN_ILMOITUS"
  val sitovan_vastaanoton_ilmoitus: LahetysSyy = "SITOVAN_VASTAANOTON_ILMOITUS"
}
