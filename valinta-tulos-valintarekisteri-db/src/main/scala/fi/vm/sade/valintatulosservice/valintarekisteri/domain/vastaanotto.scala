package fi.vm.sade.valintatulosservice.valintarekisteri.domain

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila.Vastaanottotila
import fi.vm.sade.valintatulosservice.valintarekisteri.db.VastaanottoEvent

@Deprecated //Used by old Vastaanotto API
case class Vastaanotto(hakukohdeOid: String, tila: Vastaanottotila, muokkaaja: String, selite: String)

case class HakijanVastaanottoDto(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, action: HakijanVastaanottoAction)

case class HakijanVastaanotto(henkiloOid: String, hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, action: HakijanVastaanottoAction) extends VastaanottoEvent {
  val ilmoittaja = henkiloOid
  val selite = "Hakijan oma vastaanotto"
}

case class VirkailijanVastaanotto(hakuOid: HakuOid, valintatapajonoOid: ValintatapajonoOid, henkiloOid: String, hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid,
                                  action: VirkailijanVastaanottoAction, ilmoittaja: String, selite: String) extends VastaanottoEvent

object VirkailijanVastaanotto {
  def tallennusJarjestys: (VirkailijanVastaanotto, VirkailijanVastaanotto) => Boolean = (first, second) => {
    val firstsTila: ValintatuloksenTila = first.action.valintatuloksenTila
    List(ValintatuloksenTila.KESKEN, ValintatuloksenTila.PERUUTETTU, ValintatuloksenTila.PERUNUT, ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA).contains(firstsTila)
  }


  def apply(dto: VastaanottoEventDto): VirkailijanVastaanotto = {
    VirkailijanVastaanotto(
      dto.hakuOid,
      dto.valintatapajonoOid,
      dto.henkiloOid,
      dto.hakemusOid,
      dto.hakukohdeOid,
      VirkailijanVastaanottoAction.getVirkailijanVastaanottoAction(dto.tila), dto.ilmoittaja, dto.selite)
  }
}

trait VastaanottoAction {
  def valintatuloksenTila: ValintatuloksenTila
}

trait HakijanVastaanottoAction extends VastaanottoAction

sealed trait VirkailijanVastaanottoAction extends VastaanottoAction

case object Peru extends VirkailijanVastaanottoAction with HakijanVastaanottoAction {
  val valintatuloksenTila = ValintatuloksenTila.PERUNUT
}
case object VastaanotaSitovasti extends VirkailijanVastaanottoAction with HakijanVastaanottoAction {
  val valintatuloksenTila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI
}
case object VastaanotaSitovastiPeruAlemmat extends VirkailijanVastaanottoAction with HakijanVastaanottoAction {
  val valintatuloksenTila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI
}
case object VastaanotaEhdollisesti extends VirkailijanVastaanottoAction with HakijanVastaanottoAction {
  val valintatuloksenTila = ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT
}
case object Peruuta extends VirkailijanVastaanottoAction {
  val valintatuloksenTila = ValintatuloksenTila.PERUUTETTU
}
case object Poista extends VirkailijanVastaanottoAction {
  val valintatuloksenTila = ValintatuloksenTila.KESKEN
}
case object MerkitseMyohastyneeksi extends VirkailijanVastaanottoAction {
  val valintatuloksenTila = ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA
}

object HakijanVastaanottoAction {
  private val valueMapping = Map(
    "Peru" -> Peru,
    "VastaanotaSitovasti" -> VastaanotaSitovasti,
    "VastaanotaSitovastiPeruAlemmat" -> VastaanotaSitovastiPeruAlemmat,
    "VastaanotaEhdollisesti" -> VastaanotaEhdollisesti)
  val values: List[String] = valueMapping.keysIterator.toList
  def apply(value: String): HakijanVastaanottoAction = valueMapping.getOrElse(value, {
    throw new IllegalArgumentException(s"Unknown action '$value', expected one of $values")
  })

  def getHakijanVastaanottoAction(vastaanottotila: Vastaanottotila): HakijanVastaanottoAction = vastaanottotila match {
    case Vastaanottotila.ehdollisesti_vastaanottanut => VastaanotaEhdollisesti
    case Vastaanottotila.vastaanottanut => VastaanotaSitovasti
    case Vastaanottotila.perunut => Peru
    case x  => throw new IllegalArgumentException(s"Tila ${x} ei ole sallittu")
  }
}

object VirkailijanVastaanottoAction {
  private val valueMapping = Map(
    "Peru" -> Peru,
    "VastaanotaSitovasti" -> VastaanotaSitovasti,
    "VastaanotaEhdollisesti" -> VastaanotaEhdollisesti,
    "MerkitseMyöhästyneeksi" -> MerkitseMyohastyneeksi,
    "Peruuta" -> Peruuta,
    "Poista" -> Poista)
  val values: List[String] = valueMapping.keysIterator.toList
  def apply(value: String): VirkailijanVastaanottoAction = valueMapping.getOrElse(value, {
    throw new IllegalArgumentException(s"Unknown action '$value', expected one of $values")
  })

  def getVirkailijanVastaanottoAction(vastaanottotila: Vastaanottotila): VirkailijanVastaanottoAction = vastaanottotila.toString match {
    case x if x == Vastaanottotila.ehdollisesti_vastaanottanut.toString => VastaanotaEhdollisesti
    case x if x == Vastaanottotila.vastaanottanut.toString => VastaanotaSitovasti
    case x if x == Vastaanottotila.peruutettu.toString => Peruuta
    case x if x == Vastaanottotila.perunut.toString => Peru
    case x if x == Vastaanottotila.kesken.toString => Poista
    case x if x == Vastaanottotila.ei_vastaanotettu_määräaikana.toString => MerkitseMyohastyneeksi
    case x  => throw new IllegalArgumentException(s"Tila ${x} ei ole sallittu")
  }
}
