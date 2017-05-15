package fi.vm.sade.valintatulosservice.sijoittelu

import fi.vm.sade.sijoittelu.domain.{HakukohdeItem, SijoitteluAjo}
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.HakijaDTO
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakemusRepository, SijoitteluRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu.SijoitteluajonHakija

import scala.util.{Failure, Success, Try}

trait ValintarekisteriSijoittelunTulosClient {

  def fetchLatestSijoitteluAjo(hakuOid: HakuOid, hakukohdeOid: Option[HakukohdeOid] = None): Option[SijoitteluAjo]

  def fetchHakemuksenTulos(sijoitteluAjo: SijoitteluAjo, hakemusOid: HakemusOid): Option[HakijaDTO]

}

class ValintarekisteriSijoittelunTulosClientImpl(sijoitteluRepository: HakemusRepository with SijoitteluRepository with ValinnantulosRepository, valinnantulosRepository: ValinnantulosRepository) extends ValintarekisteriSijoittelunTulosClient {

  private def run[R](operations: slick.dbio.DBIO[R]): R = valinnantulosRepository.runBlocking(operations)

  override def fetchLatestSijoitteluAjo(hakuOid: HakuOid, hakukohdeOid: Option[HakukohdeOid] = None): Option[SijoitteluAjo] = {
    val latestId = sijoitteluRepository.getLatestSijoitteluajoId(hakuOid)

    def sijoitteluajonHakukohdeOidit = latestId.map(id => sijoitteluRepository.getSijoitteluajonHakukohdeOidit(id)).getOrElse(List())
    def valinnantulostenHakukohdeOidit = run(valinnantulosRepository.getValinnantulostenHakukohdeOiditForHaku(hakuOid))

    val hakukohdeOidit = sijoitteluajonHakukohdeOidit.union(valinnantulostenHakukohdeOidit).distinct

    val hakukohdeMissing = hakukohdeOid match {
      case None => false
      case Some(oid) => !hakukohdeOidit.contains(oid)
    }

    latestId match {
      case _ if hakukohdeMissing => None
      case None if hakukohdeOidit.isEmpty => None
      case None => Some(SyntheticSijoitteluAjoForHakusWithoutSijoittelu(hakuOid, hakukohdeOidit))
      case Some(id) => sijoitteluRepository.getSijoitteluajo(id).map(_.entity(hakukohdeOidit))
    }
  }

  override def fetchHakemuksenTulos(sijoitteluAjo: SijoitteluAjo, hakemusOid: HakemusOid): Option[HakijaDTO] =
    Try(new SijoitteluajonHakija(sijoitteluRepository, sijoitteluAjo, hakemusOid).dto()) match {
      case Failure(e) => throw new RuntimeException(e)
      case Success(r) => Some(r)
    }
}
