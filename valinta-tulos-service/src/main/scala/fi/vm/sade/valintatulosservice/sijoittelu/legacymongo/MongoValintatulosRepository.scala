package fi.vm.sade.valintatulosservice.sijoittelu.legacymongo

import fi.vm.sade.sijoittelu.domain.Valintatulos
import fi.vm.sade.valintatulosservice.sijoittelu.{ValintarekisteriValintatulosRepository, ValintatulosNotFoundException}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, ValintatapajonoOid}

import scala.util.{Failure, Success, Try}

class MongoValintatulosRepository(val dao:MongoValintatulosDao) extends ValintarekisteriValintatulosRepository {

  override def modifyValintatulos(hakukohdeOid: HakukohdeOid, valintatapajonoOid: ValintatapajonoOid, hakemusOid: HakemusOid,
                         block: (Valintatulos => Unit)): Either[Throwable, Unit] = {
    val valintatulos = findValintatulos(hakukohdeOid, valintatapajonoOid, hakemusOid)
    valintatulos.right.foreach(block)
    valintatulos.right.flatMap(storeValintatulos)
  }

  private def findValintatulos(hakukohdeOid: HakukohdeOid,
                       valintatapajonoOid: ValintatapajonoOid,
                       hakemusOid: HakemusOid): Either[Throwable, Valintatulos] = {
    Try(Option(dao.loadValintatulos(hakukohdeOid, valintatapajonoOid, hakemusOid))) match {
      case Success(Some(valintatulos)) => Right(valintatulos)
      case Success(None) => Left(new ValintatulosNotFoundException(s"Valintatulos for hakemus $hakemusOid in valintatapajono $valintatapajonoOid of hakukohde $hakukohdeOid not found"))
      case Failure(e) => Left(e)
    }
  }

  override def createIfMissingAndModifyValintatulos(hakukohdeOid: HakukohdeOid, valintatapajonoOid: ValintatapajonoOid, hakemusOid: HakemusOid,
                                           henkiloOid:String, hakuOid: HakuOid, hakutoiveenJarjestysnumero: Int,
                                           block: (Valintatulos => Unit)): Either[Throwable, Unit] = {
    modifyValintatulos(hakukohdeOid, valintatapajonoOid, hakemusOid, block) match {
      case Left(e: ValintatulosNotFoundException) =>
        val v = new Valintatulos(valintatapajonoOid.toString, hakemusOid.toString, hakukohdeOid.toString, henkiloOid, hakuOid.toString, hakutoiveenJarjestysnumero)
        block(v)
        storeValintatulos(v)
      case x => x
    }
  }

  private def storeValintatulos(valintatulos: Valintatulos): Either[Throwable, Unit] = {
    Try(Right(dao.createOrUpdateValintatulos(valintatulos))).recover { case ee => Left(ee) }.get
  }
}