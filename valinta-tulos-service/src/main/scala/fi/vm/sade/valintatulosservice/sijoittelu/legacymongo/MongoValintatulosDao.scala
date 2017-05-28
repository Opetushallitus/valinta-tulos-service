package fi.vm.sade.valintatulosservice.sijoittelu.legacymongo

import fi.vm.sade.sijoittelu.domain.Valintatulos
import fi.vm.sade.sijoittelu.tulos.dao.{ValintatulosDao => MongoDao}
import fi.vm.sade.valintatulosservice.sijoittelu.{ValintarekisteriValintatulosDao}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, ValintatapajonoOid}

import scala.collection.JavaConverters._

class MongoValintatulosDao(dao:MongoDao) extends ValintarekisteriValintatulosDao {

  override def loadValintatulokset(hakuOid:HakuOid) =
    dao.loadValintatulokset(hakuOid.toString).asScala.toList

  override def loadValintatuloksetForHakukohde(hakukohdeOid:HakukohdeOid) =
    dao.loadValintatuloksetForHakukohde(hakukohdeOid.toString).asScala.toList

  override def loadValintatuloksetForHakemus(hakemusOid:HakemusOid) =
    dao.loadValintatuloksetForHakemus(hakemusOid.toString).asScala.toList

  def loadValintatulos(hakukohdeOid:HakukohdeOid, valintatapajonoOid:ValintatapajonoOid, hakemusOid:HakemusOid) =
    dao.loadValintatulos(hakukohdeOid.toString, valintatapajonoOid.toString, hakemusOid.toString)

  def createOrUpdateValintatulos(valintatulos:Valintatulos) =
    dao.createOrUpdateValintatulos(valintatulos)

}
