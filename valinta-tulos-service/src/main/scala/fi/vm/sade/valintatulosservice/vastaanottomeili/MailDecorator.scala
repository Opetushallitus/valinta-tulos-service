package fi.vm.sade.valintatulosservice.vastaanottomeili

import java.util.Date

import fi.vm.sade.oppijantunnistus.{OppijanTunnistus, OppijanTunnistusService}
import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.tarjonta
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

class HakukohdeNotFoundException(message: String) extends RuntimeException(message)

class HakuNotFoundException(message: String) extends RuntimeException(message)

class MailDecorator(hakuService: HakuService,
                    oppijanTunnistusService: OppijanTunnistusService) extends Logging {
  def statusToMail(status: HakemusMailStatus): Option[Ilmoitus] = {
    timed(s"Vastaanottopostien status muuntaminen mailiksi hakemukselle ${status.hakemusOid}", 50) {
      if (status.anyMailToBeSent) {
        try {
          val mailables = status.hakukohteet.filter(_.shouldMail)
          val deadline: Option[Date] = mailables.flatMap(_.deadline).sorted.headOption
          val tarjontaHaku = timed(s"Tarjontahaun hakeminen hakuoidilla ${status.hakuOid}", 50) {
            fetchHaku(status.hakuOid)
          }
          val ilmoitus = Ilmoitus(status.hakemusOid, status.hakijaOid, None, status.asiointikieli, status.kutsumanimi, status.email, deadline, mailables.map(toHakukohde), toHaku(tarjontaHaku))

          if (status.hasHetu && !tarjontaHaku.toinenAste) {
            Some(ilmoitus)
          } else {
            oppijanTunnistusService.luoSecureLink(status.hakijaOid, status.hakemusOid, status.email, status.asiointikieli) match {
              case Right(OppijanTunnistus(securelink)) =>
                Some(ilmoitus.copy(secureLink = Some(securelink)))
              case Left(e) =>
                logger.error("Hakemukselle ei lähetetty vastaanottomeiliä, koska securelinkkiä ei saatu! " + status.hakemusOid, e)
                None
            }
          }
        } catch {
          case e: Exception =>
            logger.error(s"Creating ilmoitus for ${status.hakemusOid} failed", e)
            None
        }
      } else {
        logger.info(s"Ei hakutoiveita joilla syytä sähköpostin lähetykselle hakemuksella ${status.hakemusOid}")
        None
      }
    }
  }

  def toHakukohde(hakukohdeMailStatus: HakukohdeMailStatus): Hakukohde = {
    hakuService.getHakukohde(hakukohdeMailStatus.hakukohdeOid) match {
      case Right(hakukohde) =>
        Hakukohde(hakukohdeMailStatus.hakukohdeOid,
          hakukohdeMailStatus.reasonToMail match {
            case Some(Vastaanottoilmoitus) if hakukohde.kkHakukohde => LahetysSyy.vastaanottoilmoitusKk
            case Some(Vastaanottoilmoitus) => LahetysSyy.vastaanottoilmoitus2aste
            case Some(EhdollisenPeriytymisenIlmoitus) => LahetysSyy.ehdollisen_periytymisen_ilmoitus
            case Some(SitovanVastaanotonIlmoitus) => LahetysSyy.sitovan_vastaanoton_ilmoitus
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

  def toHaku(haku: tarjonta.Haku): Haku = {
    Haku(haku.oid, haku.nimi, haku.toinenAste)
  }


  def fetchHaku(oid: HakuOid): tarjonta.Haku = {
    hakuService.getHaku(oid) match {
      case Right(haku: tarjonta.Haku) =>
        haku
      case Left(e) =>
        val msg = "Hakua ei löydy, oid: " + oid
        logger.error(msg, e)
        throw new HakuNotFoundException(msg)
    }
  }
}
