package fi.vm.sade.valintatulosservice.vastaanottomeili

import fi.vm.sade.oppijantunnistus.{OppijanTunnistus, OppijanTunnistusService}
import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.valintatulosservice.logging.Logging
import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, OhjausparametritService}
import fi.vm.sade.valintatulosservice.tarjonta
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

import java.util.Date

class HakukohdeNotFoundException(message: String) extends RuntimeException(message)

class HakuNotFoundException(message: String) extends RuntimeException(message)

class MailDecorator(hakuService: HakuService,
                    oppijanTunnistusService: OppijanTunnistusService,
                    ohjausparametritService: OhjausparametritService) extends Logging {
  def statusToMail(status: HakemusMailStatus): Option[Ilmoitus] = {
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
            val haunOhjausparametrit: Option[Ohjausparametrit] = timed(s"Ohjausparametrien hakeminen hakuoidilla ${status.hakuOid}", 50) {
              fetchOhjausparametrit(status.hakuOid)
            }
            val hakukierrosPaattyy: Option[Long] = haunOhjausparametrit.flatMap(_.hakukierrosPaattyy.map(_.getMillis))
            oppijanTunnistusService.luoSecureLink(status.hakijaOid, status.hakemusOid, status.email, status.asiointikieli, hakukierrosPaattyy) match {
              case Right(OppijanTunnistus(securelink)) =>
                Some(ilmoitus.copy(secureLink = Some(securelink)))
              case Left(e) =>
                logger.error(s"Hakemukselle ei lähetetty vastaanottomeiliä, koska securelinkkiä ei saatu! ${status.hakemusOid}", e)
                None
            }
          }
        } catch {
          case e: Exception =>
            logger.error(s"Creating ilmoitus for ${status.hakemusOid} failed", e)
            None
        }
      } else {
        None
      }
  }

  def toHakukohde(hakukohdeMailStatus: HakukohdeMailStatus): Hakukohde = {
    hakuService.getHakukohde(hakukohdeMailStatus.hakukohdeOid) match {
      case Right(hakukohde) =>
        hakuService.getHaku(hakukohde.hakuOid) match {
          case Right(haku) => Hakukohde(hakukohdeMailStatus.hakukohdeOid,
            hakukohdeMailStatus.reasonToMail match {
              case Some(Vastaanottoilmoitus) if haku.korkeakoulu && hakukohde.tutkintoonJohtava => LahetysSyy.vastaanottoilmoitusKk
              case Some(Vastaanottoilmoitus) if haku.korkeakoulu && !hakukohde.tutkintoonJohtava => LahetysSyy.vastaanottoilmoitusKkTutkintoonJohtamaton
              case Some(Vastaanottoilmoitus) if haku.toinenAste && haku.yhteishaku => LahetysSyy.vastaanottoilmoitus2aste
              case Some(Vastaanottoilmoitus) if haku.toinenAste && !haku.yhteishaku => LahetysSyy.vastaanottoilmoitus2asteEiYhteishaku
              case Some(Vastaanottoilmoitus) => LahetysSyy.vastaanottoilmoitusMuut
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
            val msg = "Hakukohteen" + hakukohdeMailStatus.hakukohdeOid + " hakua ei löydy, oid: " + hakukohde.hakuOid
            logger.error(msg, e)
            throw new HakukohdeNotFoundException(msg)
        }
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
        val msg = s"Hakua ei löydy, oid: $oid"
        logger.error(msg, e)
        throw new HakuNotFoundException(msg)
    }
  }

  def fetchOhjausparametrit(oid: HakuOid): Option[Ohjausparametrit] = {
    ohjausparametritService.ohjausparametrit(oid) match {
      case Right(ohjausparametrit: Ohjausparametrit) =>
        Some(ohjausparametrit)
      case Left(e) =>
        val msg = s"Virhe haettaessa hakua ohjausparametreista, oid: $oid"
        logger.info(msg, e)
        None
      case _ => None
    }
  }
}
