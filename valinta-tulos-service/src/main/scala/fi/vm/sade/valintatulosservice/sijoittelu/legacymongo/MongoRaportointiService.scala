package fi.vm.sade.valintatulosservice.sijoittelu.legacymongo

import fi.vm.sade.sijoittelu.domain.SijoitteluAjo
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.{HakijaDTO, HakijaPaginationObject, KevytHakijaDTO}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, SyntheticSijoitteluAjoForHakusWithoutSijoittelu}
import fi.vm.sade.sijoittelu.tulos.service.{RaportointiService => MongoService}
import fi.vm.sade.valintatulosservice.sijoittelu.ValintarekisteriRaportointiService

import scala.collection.JavaConverters._

class MongoRaportointiService(service:MongoService) extends ValintarekisteriRaportointiService {
  def latestSijoitteluAjoForHaku(hakuOid: HakuOid): Option[SijoitteluAjo] =
    toOption[SijoitteluAjo](service.latestSijoitteluAjoForHaku(hakuOid.toString))

  def hakemus(sijoitteluAjo: SijoitteluAjo, hakemusOid:HakemusOid): Option[HakijaDTO] =
    Option(service.hakemus(sijoitteluAjo, hakemusOid.toString))

  def hakemus(hakuOid: HakuOid, sijoitteluajoId: String, hakemusOid: HakemusOid): Option[HakijaDTO] =
    Option(service.hakemus(hakuOid.toString, sijoitteluajoId, hakemusOid.toString))

  override def hakemukset(sijoitteluAjo: SijoitteluAjo, cachedHakukohteet: Boolean = false): HakijaPaginationObject =
    hakemukset(SyntheticSijoitteluAjoForHakusWithoutSijoittelu.getSijoitteluajoId(sijoitteluAjo), HakuOid(sijoitteluAjo.getHakuOid), None, None, None, None, None, None)

  override def hakemukset(sijoitteluajoId: Option[Long], hakuOid: HakuOid, hyvaksytyt: Option[Boolean], ilmanHyvaksyntaa: Option[Boolean],
                          vastaanottaneet: Option[Boolean], hakukohdeOids: Option[List[HakukohdeOid]], count: Option[Int],
                          index: Option[Int]): HakijaPaginationObject = {
    // service.hakemukset should only use sijoitteluajoId and hakuOid from sijoitteluAjo
    val id: Long = sijoitteluajoId.getOrElse(SyntheticSijoitteluAjoForHakusWithoutSijoittelu.syntheticSijoitteluajoId)
    val sijoitteluAjo = new SijoitteluAjo()
    sijoitteluAjo.setSijoitteluajoId(id)
    sijoitteluAjo.setHakuOid(hakuOid.toString)
    service.hakemukset(sijoitteluAjo, toJavaBoolean(hyvaksytyt), toJavaBoolean(ilmanHyvaksyntaa), toJavaBoolean(vastaanottaneet),
      hakukohdeOidsListAsJava(hakukohdeOids), toJavaInt(count), toJavaInt(index))
  }

  override def getSijoitteluAjo(sijoitteluajoId: Long): Option[SijoitteluAjo] =
    toOption[SijoitteluAjo](service.getSijoitteluAjo(sijoitteluajoId))

  def latestSijoitteluAjoForHakukohde(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): Option[SijoitteluAjo] =
    toOption[SijoitteluAjo](service.latestSijoitteluAjoForHakukohde(hakuOid.toString, hakukohdeOid.toString))

  override def hakemuksetVainHakukohteenTietojenKanssa(sijoitteluAjo: SijoitteluAjo, hakukohdeOid: HakukohdeOid): List[KevytHakijaDTO] =
    service.hakemuksetVainHakukohteenTietojenKanssa(sijoitteluAjo, hakukohdeOid.toString).asScala.toList

  override def hakemukset(sijoitteluAjo: SijoitteluAjo, hakukohdeOid: HakukohdeOid): List[KevytHakijaDTO] =
    service.hakemukset(sijoitteluAjo, hakukohdeOid.toString).asScala.toList

  def toOption[R](opt:java.util.Optional[R]):Option[R] = if (opt.isPresent) Some(opt.get) else None

  def toJavaBoolean(b: Option[Boolean]): java.lang.Boolean = b match {
    case Some(scalaBoolean) => scalaBoolean.asInstanceOf[java.lang.Boolean]
    case None => null.asInstanceOf[java.lang.Boolean]
  }

  def toJavaInt(i: Option[Int]): java.lang.Integer = i match {
    case Some(scalaInt) => scalaInt
    case None => null
  }

  def hakukohdeOidsListAsJava(hakukohdeOids: Option[List[HakukohdeOid]]): java.util.List[String] = hakukohdeOids match {
    case Some(oids) => oids.map(_.toString).asJava
    case None => null
  }
}
