package fi.vm.sade.valintatulosservice.sijoittelu

import com.mongodb.DB
import fi.vm.sade.sijoittelu.domain.{SijoitteluAjo, Valintatulos}
import fi.vm.sade.sijoittelu.tulos.dao.{HakukohdeDao, SijoitteluDao}
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.{HakijaDTO, HakijaPaginationObject, KevytHakijaDTO}
import fi.vm.sade.valintatulosservice.sijoittelu.valintarekisteri.{RaportointiService, ValintatulosDao}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, ValintatapajonoOid}
import org.mongodb.morphia.Datastore

trait SijoitteluContext {

  def database:DB
  val morphiaDs:Datastore
  val valintatulosDao:ValintatulosDao
  val hakukohdeDao:HakukohdeDao
  val sijoitteluDao:SijoitteluDao
  val raportointiService:RaportointiService
  val valintatulosRepository:ValintatulosRepository
}

/*trait ValintatulosDao {

  def loadValintatulokset(hakuOid:HakuOid):List[Valintatulos]
  def loadValintatuloksetForHakukohde(hakukohdeOid:HakukohdeOid):List[Valintatulos]
  def loadValintatuloksetForValintatapajono(valintatapajonoOid:ValintatapajonoOid):List[Valintatulos]
  def loadValintatuloksetForHakemus(hakemusOid:HakemusOid):List[Valintatulos]
  def loadValintatulosForValintatapajono(valintatapajonoOid:ValintatapajonoOid, hakemusOid:HakemusOid):Valintatulos
  def loadValintatulos(hakukohdeOid:HakukohdeOid, valintatapajonoOid:ValintatapajonoOid, hakemusOid:HakemusOid):Valintatulos

  def createOrUpdateValintatulos(valintatulos:Valintatulos)
}

trait RaportointiService {
  def latestSijoitteluAjoForHaku(hakuOid:HakuOid): Option[SijoitteluAjo]
  def getSijoitteluAjo(sijoitteluajoId: Long): Option[SijoitteluAjo]
  def latestSijoitteluAjoForHakukohde(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): Option[SijoitteluAjo]

  def hakemus(sijoitteluAjo: SijoitteluAjo, hakemusOid:HakemusOid): Option[HakijaDTO]
  def hakemus(hakuOid:HakuOid, sijoitteluajoId:String, hakemusOid:HakemusOid): Option[HakijaDTO]

  def hakemukset(sijoitteluAjo: SijoitteluAjo, hakukohdeOid: HakukohdeOid): List[KevytHakijaDTO]

  def hakemukset(sijoitteluAjo: SijoitteluAjo,
                 hyvaksytyt: Option[Boolean],
                 ilmanHyvaksyntaa: Option[Boolean],
                 vastaanottaneet: Option[Boolean],
                 hakukohdeOids: Option[List[HakukohdeOid]],
                 count: Option[Int],
                 index: Option[Int]):HakijaPaginationObject


  def hakemuksetVainHakukohteenTietojenKanssa(sijoitteluAjo: SijoitteluAjo, hakukohdeOid:HakukohdeOid): List[KevytHakijaDTO]
}*/
