package fi.vm.sade.valintatulosservice.sijoittelu

import fi.vm.sade.sijoittelu.domain.{HakukohdeItem, SijoitteluAjo}
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.HakijaDTO
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaRepository, SijoitteluRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu.SijoitteluajonHakija

import scala.util.{Failure, Success, Try}

trait ValintarekisteriSijoittelunTulosClient {

  def fetchLatestSijoitteluAjo(hakuOid: HakuOid, hakukohdeOid: Option[HakukohdeOid] = None): Option[SijoitteluAjo]

  def fetchLatestSijoitteluAjoWithoutHakukohdes(hakuOid: HakuOid): Option[SijoitteluAjo]

  def fetchHakemuksenTulos(sijoitteluAjo: SijoitteluAjo, hakemusOid: HakemusOid): Option[HakijaDTO]

}

class ValintarekisteriSijoittelunTulosClientImpl(repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository) extends ValintarekisteriSijoittelunTulosClient {

  private def run[R](operations: slick.dbio.DBIO[R]): R = repository.runBlocking(operations)

  override def fetchLatestSijoitteluAjo(hakuOid: HakuOid, hakukohdeOid: Option[HakukohdeOid] = None): Option[SijoitteluAjo] = {
    val latestId = repository.getLatestSijoitteluajoId(hakuOid)

    def sijoitteluajonHakukohdeOidit = latestId.map(id => repository.getSijoitteluajonHakukohdeOidit(id)).getOrElse(List())
    def valinnantulostenHakukohdeOidit = run(repository.getValinnantulostenHakukohdeOiditForHaku(hakuOid))

    val hakukohdeOidit = sijoitteluajonHakukohdeOidit.union(valinnantulostenHakukohdeOidit).distinct

    val hakukohdeMissing = hakukohdeOid match {
      case None => false
      case Some(oid) => !hakukohdeOidit.contains(oid)
    }

    latestId match {
      case _ if hakukohdeMissing => None
      case None if hakukohdeOidit.isEmpty => None
      case None => Some(SyntheticSijoitteluAjoForHakusWithoutSijoittelu(hakuOid, hakukohdeOidit))
      case Some(id) => repository.getSijoitteluajo(id).map(_.entity(hakukohdeOidit))
    }
  }

  override def fetchLatestSijoitteluAjoWithoutHakukohdes(hakuOid: HakuOid): Option[SijoitteluAjo] = {
    repository.getLatestSijoitteluajoId(hakuOid) match {
      case None => Some(SyntheticSijoitteluAjoForHakusWithoutSijoittelu(hakuOid))
      case Some(id) => repository.getSijoitteluajo(id).map(_.entity(Nil))
    }
  }

  override def fetchHakemuksenTulos(sijoitteluAjo: SijoitteluAjo, hakemusOid: HakemusOid): Option[HakijaDTO] =
    Try(new SijoitteluajonHakija(repository, sijoitteluAjo, hakemusOid).dto()) match {
      case Failure(e) if e.isInstanceOf[NotFoundException] => None
      case Failure(e) => throw new RuntimeException(e)
      case Success(r) => Some(r)
    }
}
