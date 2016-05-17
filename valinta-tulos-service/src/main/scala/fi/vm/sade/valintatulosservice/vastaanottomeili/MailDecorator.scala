package fi.vm.sade.valintatulosservice.vastaanottomeili

import java.util.Date
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.domain.{Hakemus, Henkilotiedot}
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.tarjonta.HakuService

class HakukohdeNotFoundException(message: String) extends RuntimeException(message)

class HakuNotFoundException(message: String) extends RuntimeException(message)

class MailDecorator(hakemusRepository: HakemusRepository, valintatulosCollection: ValintatulosMongoCollection, hakuService: HakuService) extends Logging {
  def statusToMail(status: HakemusMailStatus): Option[VastaanotettavuusIlmoitus] = {
    status.anyMailToBeSent match {
      case true => {
        hakemusRepository.findHakemus(status.hakemusOid) match {
          case Some(Hakemus(_, _, henkiloOid, asiointikieli, _, Henkilotiedot(Some(kutsumanimi), Some(email), true))) =>
            val mailables = status.hakukohteet.filter(_.shouldMail)
            val deadline: Option[Date] = mailables.flatMap(_.deadline).sorted.headOption

            try {
              Some(VastaanotettavuusIlmoitus(
                status.hakemusOid, henkiloOid, asiointikieli, kutsumanimi, email, deadline, mailables.map(toHakukohde),
                toHaku(status.hakuOid)
              ))
            } catch {
              case e: Exception =>
                status.hakukohteet.filter(_.shouldMail).foreach {
                  valintatulosCollection.addMessage(status, _,  e.getMessage)
                }
                None
            }
          case Some(hakemus) =>
            logger.warn("Hakemukselta puuttuu kutsumanimi tai email: " + status.hakemusOid)
            status.hakukohteet.filter(_.shouldMail).foreach {
              valintatulosCollection.addMessage(status, _,  "Hakemukselta puuttuu kutsumanimi tai email")
            }
            None
          case _ =>
            logger.error("Hakemusta ei löydy: " + status.hakemusOid)
            status.hakukohteet.filter(_.shouldMail).foreach {
              valintatulosCollection.addMessage(status, _,  "Hakemusta ei löydy")
            }
            None
        }
      }
      case _ => None
    }
  }

  def toHakukohde(hakukohdeMailStatus: HakukohdeMailStatus) : Hakukohde = {
    hakuService.getHakukohde(hakukohdeMailStatus.hakukohdeOid) match {
      case Some(hakukohde) =>
        Hakukohde(hakukohdeMailStatus.hakukohdeOid, hakukohdeMailStatus.ehdollisestiHyvaksyttavissa,
          hakukohde.hakukohteenNimet, hakukohde.tarjoajaNimet)

      case _ =>
        val msg = "Hakukohde ei löydy, oid: " + hakukohdeMailStatus.hakukohdeOid
        logger.error(msg)
        throw new HakukohdeNotFoundException(msg)
    }
  }

  def toHaku(oid: String) : Haku = {
    hakuService.getHaku(oid) match {
      case Some(haku) =>
        Haku(haku.oid, haku.nimi)

      case _ =>
        val msg = "Hakua ei löydy, oid: " + oid
        logger.error(msg)
        throw new HakuNotFoundException(msg)
    }
  }
}
