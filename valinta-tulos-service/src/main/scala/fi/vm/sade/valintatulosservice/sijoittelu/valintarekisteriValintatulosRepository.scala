package fi.vm.sade.valintatulosservice.sijoittelu

import fi.vm.sade.sijoittelu.domain.Valintatulos
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, ValintatapajonoOid}

import scala.util.{Failure, Success, Try}

class ValintatulosNotFoundException(msg: String) extends RuntimeException(msg)

trait ValintarekisteriValintatulosRepository {

  val dao:ValintarekisteriValintatulosDao

  def findValintatulokset(valintatapajonoOid: ValintatapajonoOid): Either[Throwable, Seq[Valintatulos]] = {
    Try(dao.loadValintatuloksetForValintatapajono(valintatapajonoOid)) match {
      case Success(valintatulokset) => Right(valintatulokset)
      case Failure(e) => Left(e)
    }
  }

  def modifyValintatulos(hakukohdeOid: HakukohdeOid, valintatapajonoOid: ValintatapajonoOid, hakemusOid: HakemusOid,
                         block: (Valintatulos => Unit)): Either[Throwable, Unit]

  def createIfMissingAndModifyValintatulos(hakukohdeOid: HakukohdeOid, valintatapajonoOid: ValintatapajonoOid, hakemusOid: HakemusOid,
                                           henkiloOid:String, hakuOid: HakuOid, hakutoiveenJarjestysnumero: Int,
                                           block: (Valintatulos => Unit)): Either[Throwable, Unit]

  def findValintatulos(valintatapajonoOid: ValintatapajonoOid,
                       hakemusOid: HakemusOid): Either[Throwable, Valintatulos] = {
    Try(Option(dao.loadValintatulosForValintatapajono(valintatapajonoOid, hakemusOid))) match {
      case Success(Some(valintatulos)) => Right(valintatulos)
      case Success(None) => Left(new ValintatulosNotFoundException(s"Valintatulos for hakemus $hakemusOid in valintatapajono $valintatapajonoOid not found"))
      case Failure(e) => Left(e)
    }
  }

  def findValintatulos(hakukohdeOid: HakukohdeOid,
                       valintatapajonoOid: ValintatapajonoOid,
                       hakemusOid: HakemusOid): Either[Throwable, Valintatulos] = {
    Try(Option(dao.loadValintatulos(hakukohdeOid, valintatapajonoOid, hakemusOid))) match {
      case Success(Some(valintatulos)) => Right(valintatulos)
      case Success(None) => Left(new ValintatulosNotFoundException(s"Valintatulos for hakemus $hakemusOid in valintatapajono $valintatapajonoOid of hakukohde $hakukohdeOid not found"))
      case Failure(e) => Left(e)
    }
  }
}

class ValintarekisteriValintatulosRepositoryImpl(val dao: ValintarekisteriValintatulosDao)
  extends ValintarekisteriValintatulosRepository with Logging {

  override def modifyValintatulos(hakukohdeOid: HakukohdeOid, valintatapajonoOid: ValintatapajonoOid,
                                  hakemusOid: HakemusOid, block: (Valintatulos) => Unit): Either[Throwable, Unit] = {
    logger.warn("Yritettiin muokata valintatulosta, mutta Mongoon kirjoitus ei ole päällä.")
    Right()
  }


  override def createIfMissingAndModifyValintatulos(hakukohdeOid: HakukohdeOid, valintatapajonoOid: ValintatapajonoOid,
                                                    hakemusOid: HakemusOid, henkiloOid: String, hakuOid: HakuOid,
                                                    hakutoiveenJarjestysnumero: Int, block: (Valintatulos) => Unit): Either[Throwable, Unit] = {
    logger.warn("Yritettiin luoda tai muokata valintatulosta, mutta Mongoon kirjoitus ei ole päällä.")
    Right()
  }
}

