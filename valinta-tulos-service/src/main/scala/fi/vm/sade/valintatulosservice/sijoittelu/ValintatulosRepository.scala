package fi.vm.sade.valintatulosservice.sijoittelu

import fi.vm.sade.sijoittelu.domain.Valintatulos
import fi.vm.sade.sijoittelu.tulos.dao.ValintatulosDao

import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Try}

class ValintatulosNotFoundException(msg: String) extends RuntimeException(msg)

class ValintatulosRepository(dao: ValintatulosDao) {
  def findValintatulokset(valintatapajonoOid: String): Either[Throwable, Seq[Valintatulos]] = {
    Try(dao.loadValintatuloksetForValintatapajono(valintatapajonoOid)) match {
      case Success(valintatulokset) => Right(valintatulokset.toList)
      case Failure(e) => Left(e)
    }
  }

  def modifyValintatulos(hakukohdeOid: String, valintatapajonoOid: String, hakemusOid: String,
                         block: (Valintatulos => Unit)): Either[Throwable, Unit] = {
    val valintatulos = findValintatulos(hakukohdeOid, valintatapajonoOid, hakemusOid)
    valintatulos.right.foreach(block)
    valintatulos.right.flatMap(storeValintatulos)
  }

  def createIfMissingAndModifyValintatulos(hakukohdeOid: String, valintatapajonoOid: String, hakemusOid: String,
                                           henkiloOid:String, hakuOid: String, hakutoiveenJarjestysnumero: Int,
                                           block: (Valintatulos => Unit)): Either[Throwable, Unit] = {
    modifyValintatulos(hakukohdeOid, valintatapajonoOid, hakemusOid, block) match {
      case Left(e: ValintatulosNotFoundException) =>
        val v = new Valintatulos(valintatapajonoOid, hakemusOid, hakukohdeOid, henkiloOid, hakuOid, hakutoiveenJarjestysnumero)
        block(v)
        storeValintatulos(v)
      case x => x
    }
  }

  def findValintatulos(valintatapajonoOid: String,
                       hakemusOid: String): Either[Throwable, Valintatulos] = {
    Try(Option(dao.loadValintatulosForValintatapajono(valintatapajonoOid, hakemusOid))) match {
      case Success(Some(valintatulos)) => Right(valintatulos)
      case Success(None) => Left(new ValintatulosNotFoundException(s"Valintatulos for hakemus $hakemusOid in valintatapajono $valintatapajonoOid not found"))
      case Failure(e) => Left(e)
    }
  }

  def findValintatulos(hakukohdeOid: String,
                       valintatapajonoOid: String,
                       hakemusOid: String): Either[Throwable, Valintatulos] = {
    Try(Option(dao.loadValintatulos(hakukohdeOid, valintatapajonoOid, hakemusOid))) match {
      case Success(Some(valintatulos)) => Right(valintatulos)
      case Success(None) => Left(new ValintatulosNotFoundException(s"Valintatulos for hakemus $hakemusOid in valintatapajono $valintatapajonoOid of hakukohde $hakukohdeOid not found"))
      case Failure(e) => Left(e)
    }
  }

  private def storeValintatulos(valintatulos: Valintatulos): Either[Throwable, Unit] = {
    Try(Right(dao.createOrUpdateValintatulos(valintatulos))).recover { case ee => Left(ee) }.get
  }
}
