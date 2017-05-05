package fi.vm.sade.valintatulosservice.sijoittelu.valintarekisteri

import fi.vm.sade.sijoittelu.domain.Valintatulos
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.sijoittelu.ValintatulosRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, ValintatapajonoOid}

class ValintarekisteriValintatulosRepository(val dao: ValintarekisteriValintatulosDao)
  extends ValintatulosRepository with Logging {

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
