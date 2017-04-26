package fi.vm.sade.valintatulosservice.valintarekisteri.domain

import java.util.Date

sealed trait Ensikertalaisuus {
  val personOid: String
}

object Ensikertalaisuus {
  def apply(personOid: String, paattyi: Option[Date]): Ensikertalaisuus = paattyi match {
    case Some(paattyiJo) => EiEnsikertalainen(personOid, paattyiJo)
    case None => Ensikertalainen(personOid)
  }
}

case class Ensikertalainen(personOid: String) extends Ensikertalaisuus

case class EiEnsikertalainen(personOid: String, paattyi: Date) extends Ensikertalaisuus

sealed trait Vastaanottotieto {
  val personOid: String
  val vastaanottoaika: Date
}

case class VastaanottoHistoria (
  val opintopolku: List[OpintopolunVastaanottotieto],
  val vanhat: List[VanhaVastaanottotieto]
)

case class OpintopolunVastaanottotieto (
   personOid: String,
   hakuOid: HakuOid,
   hakukohdeOid: HakukohdeOid,
   vastaanottoAction: String,
   vastaanottoaika: Date) extends Vastaanottotieto

case class VanhaVastaanottotieto(
    personOid: String,
    hakukohde: HakukohdeOid,
    vastaanottoaika: Date) extends Vastaanottotieto
