package fi.vm.sade.valintatulosservice.sijoittelu.legacymongo

import fi.vm.sade.sijoittelu.domain.Valintatulos
import fi.vm.sade.sijoittelu.tulos.dao.{ValintatulosDao => MongoDao}
import fi.vm.sade.valintatulosservice.sijoittelu.valintarekisteri.ValintatulosDao
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, ValintatapajonoOid}

import scala.collection.JavaConverters._

class MongoValintatulosDao(dao:MongoDao) extends ValintatulosDao {

  override def loadValintatulokset(hakuOid:HakuOid) =
    dao.loadValintatulokset(hakuOid.toString).asScala.toList

  override def loadValintatuloksetForHakukohde(hakukohdeOid:HakukohdeOid) =
    dao.loadValintatuloksetForHakukohde(hakukohdeOid.toString).asScala.toList

  override def loadValintatuloksetForValintatapajono(valintatapajonoOid:ValintatapajonoOid) =
    dao.loadValintatuloksetForValintatapajono(valintatapajonoOid.toString).asScala.toList

  override def loadValintatuloksetForHakemus(hakemusOid:HakemusOid) =
    dao.loadValintatuloksetForHakemus(hakemusOid.toString).asScala.toList

  override def loadValintatulosForValintatapajono(valintatapajonoOid:ValintatapajonoOid, hakemusOid:HakemusOid) =
    dao.loadValintatulosForValintatapajono(valintatapajonoOid.toString, hakemusOid.toString)

  override def loadValintatulos(hakukohdeOid:HakukohdeOid, valintatapajonoOid:ValintatapajonoOid, hakemusOid:HakemusOid) =
    dao.loadValintatulos(hakukohdeOid.toString, valintatapajonoOid.toString, hakemusOid.toString)

  override def createOrUpdateValintatulos(valintatulos:Valintatulos) =
    dao.createOrUpdateValintatulos(valintatulos)

}
