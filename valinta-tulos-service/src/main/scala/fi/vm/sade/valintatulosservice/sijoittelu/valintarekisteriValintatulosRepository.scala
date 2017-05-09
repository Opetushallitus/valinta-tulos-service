package fi.vm.sade.valintatulosservice.sijoittelu

import fi.vm.sade.sijoittelu.domain.Valintatulos
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, ValintatapajonoOid}

import scala.util.{Failure, Success, Try}

class ValintatulosNotFoundException(msg: String) extends RuntimeException(msg)

trait ValintarekisteriValintatulosRepository {

  val dao:ValintarekisteriValintatulosDao

  def modifyValintatulos(hakukohdeOid: HakukohdeOid, valintatapajonoOid: ValintatapajonoOid, hakemusOid: HakemusOid,
                         block: (Valintatulos => Unit)): Either[Throwable, Unit]

  def createIfMissingAndModifyValintatulos(hakukohdeOid: HakukohdeOid, valintatapajonoOid: ValintatapajonoOid, hakemusOid: HakemusOid,
                                           henkiloOid:String, hakuOid: HakuOid, hakutoiveenJarjestysnumero: Int,
                                           block: (Valintatulos => Unit)): Either[Throwable, Unit]
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

