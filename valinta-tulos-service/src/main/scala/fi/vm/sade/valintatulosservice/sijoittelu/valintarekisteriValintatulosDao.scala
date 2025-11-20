package fi.vm.sade.valintatulosservice.sijoittelu

import fi.vm.sade.sijoittelu.domain.Valintatulos
import fi.vm.sade.valintatulosservice.logging.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValinnantulosRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, ValintatapajonoOid}

trait ValintarekisteriValintatulosDao {

  def loadValintatulokset(hakuOid:HakuOid):List[Valintatulos]
  def loadValintatuloksetForHakukohde(hakukohdeOid:HakukohdeOid):List[Valintatulos]
  def loadValintatuloksetForHakemus(hakemusOid:HakemusOid):List[Valintatulos]

}

class ValintarekisteriValintatulosDaoImpl(valinnantulosRepository: ValinnantulosRepository) extends ValintarekisteriValintatulosDao with Logging {

  private def run[R](operations: slick.dbio.DBIO[R]): R = valinnantulosRepository.runBlocking(operations)

  override def loadValintatulokset(hakuOid:HakuOid):List[Valintatulos] =
    run(valinnantulosRepository.getValinnantuloksetForHaku(hakuOid)).map(_.toValintatulos()).toList

  def loadValintatulos(hakukohdeOid: HakukohdeOid, valintatapajonoOid: ValintatapajonoOid, hakemusOid: HakemusOid): Valintatulos = {
    loadValintatuloksetForHakemus(hakemusOid).filter(v =>
      v.getValintatapajonoOid == valintatapajonoOid.toString && v.getHakukohdeOid == hakukohdeOid.toString
    ).head
  }

  override def loadValintatuloksetForHakukohde(hakukohdeOid:HakukohdeOid):List[Valintatulos] =
    run(valinnantulosRepository.getValinnantuloksetForHakukohde(hakukohdeOid)).map(_.toValintatulos()).toList

  override def loadValintatuloksetForHakemus(hakemusOid:HakemusOid):List[Valintatulos] =
    run(valinnantulosRepository.getValinnantuloksetForHakemus(hakemusOid)).map(_.toValintatulos()).toList
}
