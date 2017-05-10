package fi.vm.sade.valintatulosservice.sijoittelu

import java.util.{Date, Optional}

import fi.vm.sade.sijoittelu.domain.SijoitteluAjo
import fi.vm.sade.sijoittelu.tulos.dto.raportointi._
import fi.vm.sade.sijoittelu.tulos.dto.{HakemuksenTila, IlmoittautumisTila}
import fi.vm.sade.sijoittelu.tulos.resource.SijoitteluResource
import fi.vm.sade.utils.Timer
import fi.vm.sade.valintatulosservice.VastaanottoAikarajaMennyt
import fi.vm.sade.valintatulosservice.domain.Valintatila._
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.ohjausparametrit.OhjausparametritService
import fi.vm.sade.valintatulosservice.sijoittelu.JonoFinder.kaikkiJonotJulkaistu
import fi.vm.sade.valintatulosservice.sijoittelu.legacymongo.SijoittelunTulosRestClient
import fi.vm.sade.valintatulosservice.tarjonta.Haku
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, VastaanottoRecord}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila._
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.apache.commons.lang.StringUtils
import org.joda.time.DateTime
import slick.dbio.DBIO

import scala.concurrent.ExecutionContext.Implicits.global

class SijoittelutulosService(raportointiService: ValintarekisteriRaportointiService,
                             ohjausparametritService: OhjausparametritService,
                             hakijaVastaanottoRepository: HakijaVastaanottoRepository,
                             sijoittelunTulosClient: ValintarekisteriSijoittelunTulosClient) {
  import scala.collection.JavaConversions._

  def hakemuksenTulos(haku: Haku, hakemusOid: HakemusOid, hakijaOidIfFound: Option[String], aikataulu: Option[Vastaanottoaikataulu], latestSijoitteluAjo: Option[SijoitteluAjo]): Option[HakemuksenSijoitteluntulos] = {
    for (
      hakijaOid <- hakijaOidIfFound;
      sijoitteluAjo <- latestSijoitteluAjo;
      hakija: HakijaDTO <- findHakemus(hakemusOid, sijoitteluAjo)
    ) yield hakemuksenYhteenveto(hakija, aikataulu, fetchVastaanotto(hakijaOid, haku.oid), false)
  }

  def hakemustenTulos(hakuOid: HakuOid,
                      hakukohdeOid: Option[HakukohdeOid] = None,
                      hakijaOidsByHakemusOids: Map[HakemusOid, String],
                      haunVastaanotot: Option[Map[String, Set[VastaanottoRecord]]] = None): List[HakemuksenSijoitteluntulos] = {
    def fetchVastaanottos(hakemusOid: HakemusOid, hakijaOidFromSijoittelunTulos: Option[String]): Set[VastaanottoRecord] =
      (hakijaOidsByHakemusOids.get(hakemusOid).orElse(hakijaOidFromSijoittelunTulos), haunVastaanotot) match {
        case (Some(hakijaOid), Some(vastaanotot)) => vastaanotot.getOrElse(hakijaOid, Set())
        case (Some(hakijaOid), None) => fetchVastaanotto(hakijaOid, hakuOid)
        case (None, _) => throw new IllegalStateException(s"No hakija oid for hakemus $hakemusOid")
      }

    val aikataulu = findAikatauluFromOhjausparametritService(hakuOid)

    (for (
      sijoittelu <- findLatestSijoitteluAjo(hakuOid, hakukohdeOid);
      hakijat <- {
        hakukohdeOid match {
          case Some(hakukohde) => Option(Timer.timed("hakukohteen hakemukset", 1000)(raportointiService.hakemukset(sijoittelu, hakukohde)))
            .map(_.toList.map(h => hakemuksenKevytYhteenveto(h, aikataulu, fetchVastaanottos(HakemusOid(h.getHakemusOid), Option(h.getHakijaOid)))))
          case None => Option(Timer.timed("hakemukset", 1000)(raportointiService.hakemukset(sijoittelu, None, None, None, None, None, None)))
            .map(_.getResults.toList.map(h => hakemuksenYhteenveto(h, aikataulu, fetchVastaanottos(HakemusOid(h.getHakemusOid), Option(h.getHakijaOid)), false)))
        }
      }
    ) yield {
      hakijat
    }).getOrElse(Nil)
  }

  def findAikatauluFromOhjausparametritService(hakuOid: HakuOid): Option[Vastaanottoaikataulu] = {
    Timer.timed("findAikatauluFromOhjausparametritService -> ohjausparametritService.ohjausparametrit", 100) {
      ohjausparametritService.ohjausparametrit(hakuOid) match {
        case Right(o) => o.map(_.vastaanottoaikataulu)
        case Left(e) => throw e
      }
    }
  }

  def findLatestSijoitteluAjoForHaku(hakuOid: HakuOid): Option[SijoitteluAjo] = {
    Timer.timed("findLatestSijoitteluAjoForHaku -> latestSijoitteluAjoClient.fetchLatestSijoitteluAjoFromSijoitteluService", 100) {
      sijoittelunTulosClient.fetchLatestSijoitteluAjo(hakuOid)
    }
  }

  def findLatestSijoitteluAjo(hakuOid: HakuOid, hakukohdeOid: Option[HakukohdeOid]): Option[SijoitteluAjo] = {
    Timer.timed(s"findLatestSijoitteluAjo -> latestSijoitteluAjoClient.fetchLatestSijoitteluAjoFromSijoitteluService($hakuOid, $hakukohdeOid)", 100) {
      sijoittelunTulosClient.fetchLatestSijoitteluAjo(hakuOid, hakukohdeOid)
    }
  }

  def sijoittelunTuloksetWithoutVastaanottoTieto(hakuOid: HakuOid, sijoitteluajoId: String, hyvaksytyt: Option[Boolean], ilmanHyvaksyntaa: Option[Boolean], vastaanottaneet: Option[Boolean],
                                                 hakukohdeOid: Option[List[HakukohdeOid]], count: Option[Int], index: Option[Int],
                                                 haunVastaanototByHakijaOid: Map[String, Set[VastaanottoRecord]]): HakijaPaginationObject = {

    val sijoitteluntulos: Option[SijoitteluAjo] = findSijoitteluAjo(hakuOid, sijoitteluajoId)

    sijoitteluntulos.map { ajo =>
      raportointiService.hakemukset(ajo, hyvaksytyt, ilmanHyvaksyntaa, vastaanottaneet, hakukohdeOid, count, index)
    }.getOrElse(new HakijaPaginationObject)
  }

  def latestSijoittelunTulos(hakuOid: HakuOid, henkiloOid: String, hakemusOid: HakemusOid,
                             vastaanottoaikataulu: Option[Vastaanottoaikataulu]): DBIO[HakemuksenSijoitteluntulos] = {
    findLatestSijoitteluAjoForHaku(hakuOid).flatMap(findHakemus(hakemusOid, _)).map(hakija => {
      hakijaVastaanottoRepository.findHenkilonVastaanototHaussa(henkiloOid, hakuOid).map(vastaanotot => {
        hakemuksenYhteenveto(hakija, vastaanottoaikataulu, vastaanotot, false)
      })
    }).getOrElse(DBIO.successful(HakemuksenSijoitteluntulos(hakemusOid, None, Nil)))
  }

  def latestSijoittelunTulosVirkailijana(hakuOid: HakuOid, henkiloOid: String, hakemusOid: HakemusOid,
                                         vastaanottoaikataulu: Option[Vastaanottoaikataulu]): DBIO[HakemuksenSijoitteluntulos] = {
    findLatestSijoitteluAjoForHaku(hakuOid).flatMap(findHakemus(hakemusOid, _)).map(hakija => {
      hakijaVastaanottoRepository.findHenkilonVastaanototHaussa(henkiloOid, hakuOid).map(vastaanotot => {
        hakemuksenYhteenveto(hakija, vastaanottoaikataulu, vastaanotot, true)
      })
    }).getOrElse(DBIO.successful(HakemuksenSijoitteluntulos(hakemusOid, None, Nil)))
  }

  def sijoittelunTulosForAjoWithoutVastaanottoTieto(sijoitteluAjo: SijoitteluAjo, hakemusOid: HakemusOid): HakijaDTO = findHakemus(hakemusOid, sijoitteluAjo).orNull

  @Deprecated //TODO: Ei toimi erillishaulla, jolla ei ole laskentaa, jos käytössä PostgreSQL eikä Mongo. Käytetäänkö vielä oikeasti?
  def findSijoitteluAjo(hakuOid: HakuOid, sijoitteluajoId: String): Option[SijoitteluAjo] = {
    if (SijoitteluResource.LATEST == sijoitteluajoId) {
      findLatestSijoitteluAjoForHaku(hakuOid)
    } else raportointiService.getSijoitteluAjo(sijoitteluajoId.toLong)
  }

  private def findHakemus(hakemusOid: HakemusOid, sijoitteluAjo: SijoitteluAjo): Option[HakijaDTO] = {
    Timer.timed("SijoittelutulosService -> sijoittelunTulosClient.fetchHakemuksenTulos", 1000) {
      sijoittelunTulosClient.fetchHakemuksenTulos(sijoitteluAjo, hakemusOid)
    }
  }

  private def fetchVastaanotto(henkiloOid: String, hakuOid: HakuOid): Set[VastaanottoRecord] = {
    Timer.timed("hakijaVastaanottoRepository.findHenkilonVastaanototHaussa", 100) {
      hakijaVastaanottoRepository.runBlocking(hakijaVastaanottoRepository.findHenkilonVastaanototHaussa(henkiloOid, hakuOid))
    }
  }

  def hakemuksenYhteenveto(hakija: HakijaDTO, aikataulu: Option[Vastaanottoaikataulu], vastaanottoRecord: Set[VastaanottoRecord], vastaanotettavuusVirkailijana: Boolean): HakemuksenSijoitteluntulos = {

    val hakutoiveidenYhteenvedot = hakija.getHakutoiveet.toList.map { hakutoive: HakutoiveDTO =>
      val vastaanotto = vastaanottoRecord.find(v => v.hakukohdeOid.toString == hakutoive.getHakukohdeOid).map(_.action)
      val jono: HakutoiveenValintatapajonoDTO = JonoFinder.merkitseväJono(hakutoive).get
      val valintatila: Valintatila = jononValintatila(jono, hakutoive)

      val viimeisinHakemuksenTilanMuutos: Option[Date] = Option(jono.getHakemuksenTilanViimeisinMuutos)
      val viimeisinValintatuloksenMuutos: Option[Date] = Option(jono.getValintatuloksenViimeisinMuutos)
      val ( hakijanVastaanottotila, vastaanottoDeadline ) = laskeVastaanottotila(valintatila, vastaanotto, aikataulu, viimeisinHakemuksenTilanMuutos, vastaanotettavuusVirkailijana)
      val hakijanValintatila = vastaanottotilanVaikutusValintatilaan(valintatila, hakijanVastaanottotila)
      val hakijanVastaanotettavuustila: Vastaanotettavuustila.Value = laskeVastaanotettavuustila(valintatila, hakijanVastaanottotila)
      val hakijanTilat = HakutoiveenSijoittelunTilaTieto(hakijanValintatila, hakijanVastaanottotila, hakijanVastaanotettavuustila)

      val ( virkailijanVastaanottotila, _ ) = laskeVastaanottotila(valintatila, vastaanotto, aikataulu, viimeisinHakemuksenTilanMuutos, true)
      val virkailijanValintatila = vastaanottotilanVaikutusValintatilaan(valintatila, virkailijanVastaanottotila)
      val virkailijanVastaanotettavuustila = laskeVastaanotettavuustila(valintatila, virkailijanVastaanottotila)
      val virkailijanTilat = HakutoiveenSijoittelunTilaTieto(virkailijanValintatila, virkailijanVastaanottotila, virkailijanVastaanotettavuustila)

      val hyväksyttyJulkaistussaJonossa = valintatila == hyväksytty && jono.isJulkaistavissa
      val julkaistavissa = hyväksyttyJulkaistussaJonossa || kaikkiJonotJulkaistu(hakutoive)
      val ehdollisestiHyvaksyttavissa = jono.isEhdollisestiHyvaksyttavissa
      val ehdollisenHyvaksymisenEhtoKoodi = Option(jono.getEhdollisenHyvaksymisenEhtoKoodi)
      val ehdollisenHyvaksymisenEhtoFI = Option(jono.getEhdollisenHyvaksymisenEhtoFI)
      val ehdollisenHyvaksymisenEhtoSV = Option(jono.getEhdollisenHyvaksymisenEhtoSV)
      val ehdollisenHyvaksymisenEhtoEN = Option(jono.getEhdollisenHyvaksymisenEhtoEN)
      val pisteet: Option[BigDecimal] = Option(jono.getPisteet).map((p: java.math.BigDecimal) => new BigDecimal(p))

      HakutoiveenSijoitteluntulos(
        HakukohdeOid(hakutoive.getHakukohdeOid),
        hakutoive.getTarjoajaOid,
        ValintatapajonoOid(jono.getValintatapajonoOid),
        hakijanTilat = hakijanTilat,
        virkailijanTilat = virkailijanTilat,
        vastaanottoDeadline.map(_.toDate),
        SijoitteluajonIlmoittautumistila(Option(jono.getIlmoittautumisTila).getOrElse(IlmoittautumisTila.EI_TEHTY)),
        viimeisinHakemuksenTilanMuutos,
        viimeisinValintatuloksenMuutos,
        Option(jono.getJonosija).map(_.toInt),
        Option(jono.getVarasijojaKaytetaanAlkaen),
        Option(jono.getVarasijojaTaytetaanAsti),
        Option(jono.getVarasijanNumero).map(_.toInt),
        julkaistavissa,
        ehdollisestiHyvaksyttavissa,
        ehdollisenHyvaksymisenEhtoKoodi,
        ehdollisenHyvaksymisenEhtoFI,
        ehdollisenHyvaksymisenEhtoSV,
        ehdollisenHyvaksymisenEhtoEN,
        jono.getTilanKuvaukset.toMap,
        pisteet
      )
    }

    HakemuksenSijoitteluntulos(HakemusOid(hakija.getHakemusOid), Option(StringUtils.trimToNull(hakija.getHakijaOid)), hakutoiveidenYhteenvedot)
  }

  private def hakemuksenKevytYhteenveto(hakija: KevytHakijaDTO, aikataulu: Option[Vastaanottoaikataulu], vastaanottoRecord: Set[VastaanottoRecord]): HakemuksenSijoitteluntulos = {
    val hakutoiveidenYhteenvedot = hakija.getHakutoiveet.toList.map { hakutoive: KevytHakutoiveDTO =>
      val vastaanotto = vastaanottoRecord.find(v => v.hakukohdeOid.toString == hakutoive.getHakukohdeOid).map(_.action)
      val jono: KevytHakutoiveenValintatapajonoDTO = JonoFinder.merkitseväJono(hakutoive).get

      val valintatila: Valintatila = jononValintatila(jono, hakutoive)

      val viimeisinHakemuksenTilanMuutos: Option[Date] = Option(jono.getHakemuksenTilanViimeisinMuutos)
      val viimeisinValintatuloksenMuutos: Option[Date] = Option(jono.getValintatuloksenViimeisinMuutos)
      val ( hakijanVastaanottotila, vastaanottoDeadline ) = laskeVastaanottotila(valintatila, vastaanotto, aikataulu, viimeisinHakemuksenTilanMuutos, false)
      val hakijanValintatila = vastaanottotilanVaikutusValintatilaan(valintatila, hakijanVastaanottotila)
      val hakijanVastaanotettavuustila: Vastaanotettavuustila.Value = laskeVastaanotettavuustila(valintatila, hakijanVastaanottotila)
      val hakijanTilat = HakutoiveenSijoittelunTilaTieto(hakijanValintatila, hakijanVastaanottotila, hakijanVastaanotettavuustila)

      val ( virkailijanVastaanottotila, _ ) = laskeVastaanottotila(valintatila, vastaanotto, aikataulu, viimeisinHakemuksenTilanMuutos, true)
      val virkailijanValintatila = vastaanottotilanVaikutusValintatilaan(valintatila, virkailijanVastaanottotila)
      val virkailijanVastaanotettavuustila = laskeVastaanotettavuustila(valintatila, virkailijanVastaanottotila)
      val virkailijanTilat = HakutoiveenSijoittelunTilaTieto(virkailijanValintatila, virkailijanVastaanottotila, virkailijanVastaanotettavuustila)

      val hyväksyttyJulkaistussaJonossa = valintatila == hyväksytty && jono.isJulkaistavissa
      val julkaistavissa = hyväksyttyJulkaistussaJonossa || kaikkiJonotJulkaistu(hakutoive)
      val ehdollisestiHyvaksyttavissa = jono.isEhdollisestiHyvaksyttavissa
      val ehdollisenHyvaksymisenEhtoKoodi = Option(jono.getEhdollisenHyvaksymisenEhtoKoodi)
      val ehdollisenHyvaksymisenEhtoFI = Option(jono.getEhdollisenHyvaksymisenEhtoFI)
      val ehdollisenHyvaksymisenEhtoSV = Option(jono.getEhdollisenHyvaksymisenEhtoSV)
      val ehdollisenHyvaksymisenEhtoEN = Option(jono.getEhdollisenHyvaksymisenEhtoEN)
      val pisteet: Option[BigDecimal] = Option(jono.getPisteet).map((p: java.math.BigDecimal) => new BigDecimal(p))

      HakutoiveenSijoitteluntulos(
        HakukohdeOid(hakutoive.getHakukohdeOid),
        hakutoive.getTarjoajaOid,
        ValintatapajonoOid(jono.getValintatapajonoOid),
        hakijanTilat = hakijanTilat,
        virkailijanTilat = virkailijanTilat,
        vastaanottoDeadline.map(_.toDate),
        SijoitteluajonIlmoittautumistila(Option(jono.getIlmoittautumisTila).getOrElse(IlmoittautumisTila.EI_TEHTY)),
        viimeisinHakemuksenTilanMuutos,
        viimeisinValintatuloksenMuutos,
        Option(jono.getJonosija).map(_.toInt),
        Option(jono.getVarasijojaKaytetaanAlkaen),
        Option(jono.getVarasijojaTaytetaanAsti),
        Option(jono.getVarasijanNumero).map(_.toInt),
        julkaistavissa,
        ehdollisestiHyvaksyttavissa,
        ehdollisenHyvaksymisenEhtoKoodi,
        ehdollisenHyvaksymisenEhtoFI,
        ehdollisenHyvaksymisenEhtoSV,
        ehdollisenHyvaksymisenEhtoEN,
        jono.getTilanKuvaukset.toMap,
        pisteet
      )
    }

    HakemuksenSijoitteluntulos(HakemusOid(hakija.getHakemusOid), Option(StringUtils.trimToNull(hakija.getHakijaOid)), hakutoiveidenYhteenvedot)
  }

  private def laskeVastaanotettavuustila(valintatila: Valintatila, vastaanottotila: Vastaanottotila): Vastaanotettavuustila.Value = {
    if (Valintatila.isHyväksytty(valintatila) && Set(Vastaanottotila.kesken, Vastaanottotila.ehdollisesti_vastaanottanut).contains(vastaanottotila)) {
      Vastaanotettavuustila.vastaanotettavissa_sitovasti
    } else {
      Vastaanotettavuustila.ei_vastaanotettavissa
    }
  }

  private def jononValintatila(jono: HakutoiveenValintatapajonoDTO, hakutoive: HakutoiveDTO) = {
    val valintatila: Valintatila = ifNull(fromHakemuksenTila(jono.getTila), Valintatila.kesken)
    if (jono.getTila.isHyvaksytty && jono.isHyvaksyttyHarkinnanvaraisesti) {
      Valintatila.harkinnanvaraisesti_hyväksytty
    } else if (!jono.getTila.isHyvaksytty && !hakutoive.isKaikkiJonotSijoiteltu) {
      Valintatila.kesken
    } else if (valintatila == Valintatila.varalla && jono.isHyvaksyttyVarasijalta) {
      Valintatila.hyväksytty
    } else if (valintatila == Valintatila.varalla && jono.isEiVarasijatayttoa) {
      Valintatila.kesken
    } else {
      valintatila
    }
  }

  private def jononValintatila(jono: KevytHakutoiveenValintatapajonoDTO, hakutoive: KevytHakutoiveDTO) = {
    val valintatila: Valintatila = ifNull(fromHakemuksenTila(jono.getTila), Valintatila.kesken)
    if (jono.getTila.isHyvaksytty && jono.isHyvaksyttyHarkinnanvaraisesti) {
      Valintatila.harkinnanvaraisesti_hyväksytty
    } else if (!jono.getTila.isHyvaksytty && !hakutoive.isKaikkiJonotSijoiteltu) {
      Valintatila.kesken
    } else if (valintatila == Valintatila.varalla && jono.isHyvaksyttyVarasijalta) {
      Valintatila.hyväksytty
    } else if (valintatila == Valintatila.varalla && jono.isEiVarasijatayttoa) {
      Valintatila.kesken
    } else {
      valintatila
    }
  }

  def vastaanottotilaVainViimeisimmanVastaanottoActioninPerusteella(vastaanotto: Option[VastaanottoAction]): Vastaanottotila = vastaanotto match {
    case Some(Poista) | None => Vastaanottotila.kesken
    case Some(Peru) => Vastaanottotila.perunut
    case Some(VastaanotaSitovasti) => Vastaanottotila.vastaanottanut
    case Some(VastaanotaEhdollisesti) => Vastaanottotila.ehdollisesti_vastaanottanut
    case Some(Peruuta) => Vastaanottotila.peruutettu
    case Some(MerkitseMyohastyneeksi) => Vastaanottotila.ei_vastaanotettu_määräaikana
  }

  private def laskeVastaanottotila(valintatila: Valintatila, vastaanotto: Option[VastaanottoAction], aikataulu: Option[Vastaanottoaikataulu], viimeisinHakemuksenTilanMuutos: Option[Date], vastaanotettavuusVirkailijana: Boolean = false): ( Vastaanottotila, Option[DateTime] ) = {
    val deadline = laskeVastaanottoDeadline(aikataulu, viimeisinHakemuksenTilanMuutos)
    vastaanottotilaVainViimeisimmanVastaanottoActioninPerusteella(vastaanotto) match {
      case Vastaanottotila.kesken if Valintatila.isHyväksytty(valintatila) || valintatila == Valintatila.perunut =>
        if (deadline.exists(_.isBeforeNow) && !vastaanotettavuusVirkailijana) {
          (Vastaanottotila.ei_vastaanotettu_määräaikana, deadline)
        } else {
          (Vastaanottotila.kesken, deadline)
        }
      case tila if Valintatila.isHyväksytty(valintatila) => (tila, deadline)
      case tila => (tila, None)
    }
  }

  def haeVastaanotonAikarajaTiedot(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid, hakemusOids: Set[HakemusOid]): Set[VastaanottoAikarajaMennyt] = {
    def calculateLateness(aikataulu: Option[Vastaanottoaikataulu])(hakijaDto: KevytHakijaDTO): VastaanottoAikarajaMennyt = {
      val hakutoiveDtoOfThisHakukohde: Option[KevytHakutoiveDTO] = hakijaDto.getHakutoiveet.toList.find(_.getHakukohdeOid == hakukohdeOid)
      val vastaanottoDeadline: Option[DateTime] = hakutoiveDtoOfThisHakukohde.flatMap { hakutoive: KevytHakutoiveDTO =>
        val jono: KevytHakutoiveenValintatapajonoDTO = JonoFinder.merkitseväJono(hakutoive).get
        val viimeisinHakemuksenTilanMuutos: Option[Date] = Option(jono.getHakemuksenTilanViimeisinMuutos)
        laskeVastaanottoDeadline(aikataulu, viimeisinHakemuksenTilanMuutos)
      }
      val isLate: Boolean = vastaanottoDeadline.exists(new DateTime().isAfter)
      VastaanottoAikarajaMennyt(HakemusOid(hakijaDto.getHakemusOid), isLate, vastaanottoDeadline)
    }

    findLatestSijoitteluAjo(hakuOid, Some(hakukohdeOid)) match {
      case Some(sijoitteluAjo) =>
        val aikataulu = findAikatauluFromOhjausparametritService(hakuOid)
        val allHakijasForHakukohde = Timer.timed(s"Fetch hakemukset just for hakukohde $hakukohdeOid of haku $hakuOid", 1000) {
          raportointiService.hakemuksetVainHakukohteenTietojenKanssa(sijoitteluAjo, hakukohdeOid)
        }
        val queriedHakijasForHakukohde = allHakijasForHakukohde.filter(hakijaDto => hakemusOids.contains(hakijaDto.getHakemusOid))
        queriedHakijasForHakukohde.map(calculateLateness(aikataulu)).toSet
      case None => Set()
    }
  }

  private def laskeVastaanottoDeadline(aikataulu: Option[Vastaanottoaikataulu], viimeisinHakemuksenTilanMuutos: Option[Date]): Option[DateTime] = {
    aikataulu match {
      case Some(Vastaanottoaikataulu(Some(deadlineFromHaku), buffer)) =>
        val deadlineFromHakemuksenTilanMuutos = getDeadlineWithBuffer(viimeisinHakemuksenTilanMuutos, buffer, deadlineFromHaku)
        val deadlines = Some(deadlineFromHaku) ++ deadlineFromHakemuksenTilanMuutos
        Some(deadlines.maxBy((a: DateTime) => a.getMillis))
      case _ => None
    }
  }

  private def vastaanottotilanVaikutusValintatilaan(valintatila: Valintatila, vastaanottotila : Vastaanottotila): Valintatila = {
    if (List(Vastaanottotila.ehdollisesti_vastaanottanut, Vastaanottotila.vastaanottanut).contains(vastaanottotila)) {
      if (List(Valintatila.harkinnanvaraisesti_hyväksytty, Valintatila.varasijalta_hyväksytty, Valintatila.hyväksytty).contains(valintatila)) {
        valintatila
      } else {
         Valintatila.hyväksytty
      }
    } else if (Vastaanottotila.perunut == vastaanottotila) {
      Valintatila.perunut
    } else if (Vastaanottotila.peruutettu == vastaanottotila) {
       Valintatila.peruutettu
    } else {
      valintatila
    }
  }

  private def fromHakemuksenTila(tila: HakemuksenTila): Valintatila = {
    Valintatila.withName(tila.name)
  }


  private def getDeadlineWithBuffer(viimeisinMuutosOption: Option[Date], bufferOption: Option[Int], deadline: DateTime): Option[DateTime] = {
    for {
      viimeisinMuutos <- viimeisinMuutosOption
      buffer <- bufferOption
    } yield new DateTime(viimeisinMuutos).plusDays(buffer).withTime(deadline.getHourOfDay, deadline.getMinuteOfHour, deadline.getSecondOfMinute, deadline.getMillisOfSecond)
  }

  private def ifNull[T](value: T, defaultValue: T): T = {
    if (value == null) defaultValue
    else value
  }
}
