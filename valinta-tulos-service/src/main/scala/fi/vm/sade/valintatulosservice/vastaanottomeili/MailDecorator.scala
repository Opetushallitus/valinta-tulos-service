package fi.vm.sade.valintatulosservice.vastaanottomeili

import java.util.Date

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.AppConfig
import fi.vm.sade.valintatulosservice.domain.{Hakemus, Henkilotiedot}
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.oppijantunnistus.{OppijanTunnistus, OppijanTunnistusService}
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.MailPollerRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

class HakukohdeNotFoundException(message: String) extends RuntimeException(message)

class HakuNotFoundException(message: String) extends RuntimeException(message)

class MailDecorator(hakemusRepository: HakemusRepository,
                    mailPollerRepository: MailPollerRepository,
                    hakuService: HakuService,
                    oppijanTunnistusService: OppijanTunnistusService
                   ) extends Logging {
  def statusToMail(status: HakemusMailStatus): Option[Ilmoitus] = {
    status.anyMailToBeSent match {
      case true => {
        hakemusRepository.findHakemus(status.hakemusOid) match {
          case Right(Hakemus(_, _, henkiloOid, asiointikieli, _, Henkilotiedot(Some(kutsumanimi), Some(email), henkilötunnuksellinen))) =>
            val mailables = status.hakukohteet.filter(_.shouldMail)
            val deadline: Option[Date] = mailables.flatMap(_.deadline).sorted.headOption

            try {
              val ilmoitus = Ilmoitus(
                status.hakemusOid, henkiloOid, None, asiointikieli, kutsumanimi, email, deadline, mailables.map(toHakukohde),
                toHaku(status.hakuOid))
              if(henkilötunnuksellinen) {
                Some(ilmoitus)
              } else {
                oppijanTunnistusService.luoSecureLink(henkiloOid, status.hakemusOid, email, asiointikieli) match {
                  case Right(OppijanTunnistus(securelink)) =>
                    Some(ilmoitus.copy(secureLink = Some(securelink)))
                  case Left(e) =>
                    logger.error("Hakemukselle ei lähetetty vastaanottomeiliä, koska securelinkkiä ei saatu! " + status.hakemusOid, e)
                    None
                }
              }
            } catch {
              case e: Exception =>
                status.hakukohteet.filter(_.shouldMail).foreach {
                  mailPollerRepository.addMessage(status, _,  e.getMessage)
                }
                None
            }
          case Right(hakemus) =>
            logger.warn("Hakemukselta puuttuu kutsumanimi tai email: " + status.hakemusOid)
            status.hakukohteet.filter(_.shouldMail).foreach {
              mailPollerRepository.addMessage(status, _,  "Hakemukselta puuttuu kutsumanimi tai email")
            }
            None
          case Left(e) =>
            logger.error("Hakemusta ei löydy: " + status.hakemusOid, e)
            status.hakukohteet.filter(_.shouldMail).foreach {
              mailPollerRepository.addMessage(status, _,  "Hakemusta ei löydy")
            }
            None
        }
      }
      case _ => None
    }
  }

  def toHakukohde(hakukohdeMailStatus: HakukohdeMailStatus) : Hakukohde = {
    hakuService.getHakukohde(hakukohdeMailStatus.hakukohdeOid) match {
      case Right(hakukohde) =>
        Hakukohde(hakukohdeMailStatus.hakukohdeOid,
          hakukohdeMailStatus.reasonToMail match {
            case Some(MailReason.VASTAANOTTOILMOITUS) => LahetysSyy.vastaanottoilmoitus
            case Some(MailReason.EHDOLLISEN_PERIYTYMISEN_ILMOITUS) => LahetysSyy.ehdollisen_periytymisen_ilmoitus
            case Some(MailReason.SITOVAN_VASTAANOTON_ILMOITUS) => LahetysSyy.sitovan_vastaanoton_ilmoitus
            case _ =>
              throw new RuntimeException(s"Tuntematon lähetyssyy ${hakukohdeMailStatus.reasonToMail}")
          },
          hakukohdeMailStatus.vastaanottotila,
          hakukohdeMailStatus.ehdollisestiHyvaksyttavissa,
          hakukohde.hakukohteenNimet,
          hakukohde.tarjoajaNimet)
      case Left(e) =>
        val msg = "Hakukohde ei löydy, oid: " + hakukohdeMailStatus.hakukohdeOid
        logger.error(msg, e)
        throw new HakukohdeNotFoundException(msg)
    }
  }

  def toHaku(oid: HakuOid) : Haku = {
    hakuService.getHaku(oid) match {
      case Right(haku) =>
        Haku(haku.oid, haku.nimi)
      case Left(e) =>
        val msg = "Hakua ei löydy, oid: " + oid
        logger.error(msg, e)
        throw new HakuNotFoundException(msg)
    }
  }
}
