package fi.vm.sade.valintatulosservice.sijoittelu.valintarekisteri

import fi.vm.sade.sijoittelu.domain.Valintatulos
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValinnantulosRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, ValintatapajonoOid}

trait ValintatulosDao {

  def loadValintatulokset(hakuOid:HakuOid):List[Valintatulos]
  def loadValintatuloksetForHakukohde(hakukohdeOid:HakukohdeOid):List[Valintatulos]
  def loadValintatuloksetForValintatapajono(valintatapajonoOid:ValintatapajonoOid):List[Valintatulos]
  def loadValintatuloksetForHakemus(hakemusOid:HakemusOid):List[Valintatulos]
  def loadValintatulosForValintatapajono(valintatapajonoOid:ValintatapajonoOid, hakemusOid:HakemusOid):Valintatulos
  def loadValintatulos(hakukohdeOid:HakukohdeOid, valintatapajonoOid:ValintatapajonoOid, hakemusOid:HakemusOid):Valintatulos

  def createOrUpdateValintatulos(valintatulos:Valintatulos)
}

class ValintarekisteriValintatulosDao(valinnantulosRepository: ValinnantulosRepository) extends ValintatulosDao with Logging {

  override def loadValintatulokset(hakuOid:HakuOid) =
    valinnantulosRepository.runBlocking(
      valinnantulosRepository.getValinnantuloksetForHaku(hakuOid)
    ).map(_.toValintatulos).toList


  override def loadValintatuloksetForHakukohde(hakukohdeOid:HakukohdeOid) =
    valinnantulosRepository.runBlocking(
      valinnantulosRepository.getValinnantuloksetForHakukohde(hakukohdeOid)
    ).map(_.toValintatulos).toList

  override def loadValintatuloksetForValintatapajono(valintatapajonoOid:ValintatapajonoOid) =
    valinnantulosRepository.runBlocking(
      valinnantulosRepository.getValinnantuloksetForValintatapajono(valintatapajonoOid)
    ).map(_.toValintatulos).toList

  override def loadValintatuloksetForHakemus(hakemusOid:HakemusOid) =
    throw new NotImplementedError("TODO")

  override def loadValintatulosForValintatapajono(valintatapajonoOid:ValintatapajonoOid, hakemusOid:HakemusOid) =
    valinnantulosRepository.runBlocking(
      valinnantulosRepository.getValinnantuloksetForValintatapajono(valintatapajonoOid)
    ).find(_.hakemusOid == hakemusOid).map(_.toValintatulos()).getOrElse(null)

  override def loadValintatulos(hakukohdeOid:HakukohdeOid, valintatapajonoOid:ValintatapajonoOid, hakemusOid:HakemusOid) =
    valinnantulosRepository.runBlocking(
      valinnantulosRepository.getValinnantuloksetForHakukohde(hakukohdeOid)
    ).find(vt => vt.hakemusOid == hakemusOid && vt.valintatapajonoOid == valintatapajonoOid).map(_.toValintatulos()).getOrElse(null)

  override def createOrUpdateValintatulos(valintatulos:Valintatulos) =
    logger.warn("Yritettiin kirjoittaa valintatulosta, mutta Mongoon kirjoitus ei ole päällä.")

}
