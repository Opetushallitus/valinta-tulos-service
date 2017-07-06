package fi.vm.sade.valintatulosservice.sijoittelu

import java.time.{Instant, OffsetDateTime, ZoneId}
import java.util.concurrent.TimeUnit

import fi.vm.sade.sijoittelu.domain.SijoitteluAjo
import fi.vm.sade.sijoittelu.tulos.dto.raportointi._
import fi.vm.sade.sijoittelu.tulos.dto.{HakemuksenTila, IlmoittautumisTila}
import fi.vm.sade.sijoittelu.tulos.resource.SijoitteluResource
import fi.vm.sade.utils.Timer
import fi.vm.sade.valintatulosservice.domain.Valintatila._
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, OhjausparametritService}
import fi.vm.sade.valintatulosservice.sijoittelu.JonoFinder.kaikkiJonotJulkaistu
import fi.vm.sade.valintatulosservice.tarjonta.Haku
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, VastaanottoRecord}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila._
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.{PersonOidFromHakemusResolver, VastaanottoAikarajaMennyt}
import org.apache.commons.lang.StringUtils
import org.joda.time.{DateTime, DateTimeZone}
import slick.dbio.DBIO

import scala.concurrent.ExecutionContext.Implicits.global

class SijoittelutulosService(raportointiService: ValintarekisteriRaportointiService,
                             ohjausparametritService: OhjausparametritService,
                             hakijaVastaanottoRepository: HakijaVastaanottoRepository,
                             sijoittelunTulosClient: ValintarekisteriSijoittelunTulosClient) {
  import scala.collection.JavaConversions._

  def hakemuksenTulos(haku: Haku,
                      hakemusOid: HakemusOid,
                      hakijaOidIfFound: Option[String],
                      ohjausparametrit: Option[Ohjausparametrit],
                      latestSijoitteluajoId: Option[Long]): Option[HakemuksenSijoitteluntulos] = {
    for (
      hakijaOid <- hakijaOidIfFound;
      hakija: HakijaDTO <- findHakemus(hakemusOid, latestSijoitteluajoId, haku.oid)
    ) yield hakemuksenYhteenveto(hakija, ohjausparametrit,
      hakijaVastaanottoRepository.findHyvaksyttyJulkaistuDatesForHenkilo(hakijaOid),
      fetchVastaanotto(hakijaOid, haku.oid), vastaanotettavuusVirkailijana = false)
  }

  def hakemustenTulos(hakuOid: HakuOid,
                      hakukohdeOid: Option[HakukohdeOid],
                      personOidResolver: PersonOidFromHakemusResolver,
                      haunVastaanotot: Option[Map[String, Set[VastaanottoRecord]]] = None): List[HakemuksenSijoitteluntulos] = {
    def fetchVastaanottos(hakemusOid: HakemusOid, hakijaOidFromSijoittelunTulos: Option[String]): Set[VastaanottoRecord] =
      (hakijaOidFromSijoittelunTulos.orElse(personOidResolver.findBy(hakemusOid)), haunVastaanotot) match {
        case (Some(hakijaOid), Some(vastaanotot)) => vastaanotot.getOrElse(hakijaOid, Set())
        case (Some(hakijaOid), None) => fetchVastaanotto(hakijaOid, hakuOid)
        case (None, _) => throw new IllegalStateException(s"No hakija oid for hakemus $hakemusOid")
      }

    val ohjausparametrit = findOhjausparametritFromOhjausparametritService(hakuOid)

    (for (
      sijoittelu <- findLatestSijoitteluAjo(hakuOid, hakukohdeOid);
      hakijaDtot <- hakukohdeOid match {
        case Some(hakukohde) => Option(Timer.timed("hakukohteen hakemukset", 1000)(raportointiService.kevytHakemukset(sijoittelu, hakukohde)))
        case None => Option(Timer.timed("hakemukset", 1000)(raportointiService.kevytHakemukset(sijoittelu)))
      };
      hakijat <- {
        val hyvaksyttyJaJulkaistuDates = hakijaVastaanottoRepository.findHyvaksyttyJulkaistuDatesForHaku(hakuOid)
        Option(hakijaDtot.map(h => hakemuksenKevytYhteenveto(h, ohjausparametrit, hyvaksyttyJaJulkaistuDates.getOrElse(h.getHakijaOid, Map()),
          fetchVastaanottos(HakemusOid(h.getHakemusOid), Option(h.getHakijaOid)))))
      }
    ) yield {
      hakijat
    }).getOrElse(Nil)
  }

  def findOhjausparametritFromOhjausparametritService(hakuOid: HakuOid): Option[Ohjausparametrit] = {
    Timer.timed("findAikatauluFromOhjausparametritService -> ohjausparametritService.ohjausparametrit", 100) {
      ohjausparametritService.ohjausparametrit(hakuOid) match {
        case Right(o) => o
        case Left(e) => throw e
      }
    }
  }

  def findLatestSijoitteluajoId(hakuOid: HakuOid): Option[Long] = {
    Timer.timed("findLatestSijoitteluajoId -> sijoittelunTulosClient.fetchLatestSijoitteluajoId", 100) {
      sijoittelunTulosClient.fetchLatestSijoitteluajoId(hakuOid)
    }
  }

  def findLatestSijoitteluAjoForHaku(hakuOid: HakuOid): Option[SijoitteluAjo] = {
    Timer.timed("findLatestSijoitteluAjoForHaku -> latestSijoitteluAjoClient.fetchLatestSijoitteluAjoFromSijoitteluService", 100) {
      sijoittelunTulosClient.fetchLatestSijoitteluAjo(hakuOid)
    }
  }

  def findLatestSijoitteluAjoWithoutHakukohdesForHaku(hakuOid: HakuOid): Option[SijoitteluAjo] = {
    Timer.timed("findLatestSijoitteluAjoWithoutHakukohdesForHaku -> sijoittelunTulosClient.fetchLatestSijoitteluAjo(hakuOid)", 100) {
      sijoittelunTulosClient.fetchLatestSijoitteluAjoWithoutHakukohdes(hakuOid)
    }
  }

  def findLatestSijoitteluAjo(hakuOid: HakuOid, hakukohdeOid: Option[HakukohdeOid]): Option[SijoitteluAjo] = {
    Timer.timed(s"findLatestSijoitteluAjo -> latestSijoitteluAjoClient.fetchLatestSijoitteluAjoFromSijoitteluService($hakuOid, $hakukohdeOid)", 100) {
      sijoittelunTulosClient.fetchLatestSijoitteluAjo(hakuOid, hakukohdeOid)
    }
  }

  @Deprecated //Ei käytetä/sivutusta ei käytetä
  def sijoittelunTuloksetWithoutVastaanottoTieto(hakuOid: HakuOid, sijoitteluajoId: String, hyvaksytyt: Option[Boolean], ilmanHyvaksyntaa: Option[Boolean], vastaanottaneet: Option[Boolean],
                                                 hakukohdeOid: Option[List[HakukohdeOid]], count: Option[Int], index: Option[Int],
                                                 haunVastaanototByHakijaOid: Map[String, Set[VastaanottoRecord]]): HakijaPaginationObject = {

    val id: Option[Long] = findSijoitteluAjo(hakuOid, sijoitteluajoId)
    raportointiService.hakemukset(id, hakuOid, hyvaksytyt, ilmanHyvaksyntaa, vastaanottaneet, hakukohdeOid, count, index)
  }

  private def latestSijoittelunTulos(hakuOid: HakuOid, henkiloOid: String, hakemusOid: HakemusOid,
                                     ohjausparametrit: Option[Ohjausparametrit], virkailijana:Boolean): DBIO[HakemuksenSijoitteluntulos] = {
    findHakemus(hakemusOid, findLatestSijoitteluajoId(hakuOid), hakuOid).map(hakija => {
      hakijaVastaanottoRepository.findHenkilonVastaanototHaussa(henkiloOid, hakuOid).map(vastaanotot => {
        val hyvaksyttyJaJulkaistuDates = hakijaVastaanottoRepository.findHyvaksyttyJulkaistuDatesForHenkilo(henkiloOid)
        hakemuksenYhteenveto(hakija, ohjausparametrit, hyvaksyttyJaJulkaistuDates, vastaanotot, virkailijana)
      })
    }).getOrElse(DBIO.successful(HakemuksenSijoitteluntulos(hakemusOid, None, Nil)))
  }

  def latestSijoittelunTulos(hakuOid: HakuOid, henkiloOid: String, hakemusOid: HakemusOid,
                             ohjausparametrit: Option[Ohjausparametrit]): DBIO[HakemuksenSijoitteluntulos] =
    latestSijoittelunTulos(hakuOid, henkiloOid, hakemusOid, ohjausparametrit, false)


  def latestSijoittelunTulosVirkailijana(hakuOid: HakuOid, henkiloOid: String, hakemusOid: HakemusOid,
                                         ohjausparametrit: Option[Ohjausparametrit]): DBIO[HakemuksenSijoitteluntulos] =
    latestSijoittelunTulos(hakuOid, henkiloOid, hakemusOid, ohjausparametrit, true)

  def sijoittelunTulosForAjoWithoutVastaanottoTieto(sijoitteluajoId: Option[Long], hakuOid: HakuOid, hakemusOid: HakemusOid): Option[HakijaDTO] =
    findHakemus(hakemusOid, sijoitteluajoId, hakuOid)

  @Deprecated //TODO: Ei toimi erillishaulla, jolla ei ole laskentaa, jos käytössä PostgreSQL eikä Mongo. Käytetäänkö vielä oikeasti?
  def findSijoitteluAjo(hakuOid: HakuOid, sijoitteluajoId: String): Option[Long] = {
    if (SijoitteluResource.LATEST == sijoitteluajoId) {
      findLatestSijoitteluajoId(hakuOid)
    } else raportointiService.getSijoitteluAjo(sijoitteluajoId.toLong).map(_.getSijoitteluajoId)
  }

  private def findHakemus(hakemusOid: HakemusOid, sijoitteluajoId: Option[Long], hakuOid: HakuOid): Option[HakijaDTO] = {
    Timer.timed("SijoittelutulosService -> sijoittelunTulosClient.fetchHakemuksenTulos", 1000) {
      sijoittelunTulosClient.fetchHakemuksenTulos(sijoitteluajoId, hakuOid, hakemusOid)
    }
  }

  private def fetchVastaanotto(henkiloOid: String, hakuOid: HakuOid): Set[VastaanottoRecord] = {
    Timer.timed("hakijaVastaanottoRepository.findHenkilonVastaanototHaussa", 100) {
      hakijaVastaanottoRepository.runBlocking(hakijaVastaanottoRepository.findHenkilonVastaanototHaussa(henkiloOid, hakuOid))
    }
  }

  def hakemuksenYhteenveto(hakija: HakijaDTO,
                           ohjausparametrit: Option[Ohjausparametrit],
                           hyvaksyttyJulkaistuDates: Map[HakukohdeOid, OffsetDateTime],
                           vastaanottoRecords: Set[VastaanottoRecord],
                           vastaanotettavuusVirkailijana: Boolean): HakemuksenSijoitteluntulos = {

    val hakutoiveidenYhteenvedot = hakija.getHakutoiveet.toList.map { hakutoive: HakutoiveDTO =>
      val vastaanotto = vastaanottoRecords.find(v => v.hakukohdeOid.toString == hakutoive.getHakukohdeOid).map(_.action)
      val jono = JonoFinder.merkitseväJono(hakutoive).get
      val valintatila = jononValintatila(jono, hakutoive)

      val hakutoiveenHyvaksyttyJaJulkaistuDate = hyvaksyttyJulkaistuDates.get(HakukohdeOid(hakutoive.getHakukohdeOid))

      val (hakijanTilat, vastaanottoDeadline) = tilatietoJaVastaanottoDeadline(valintatila, vastaanotto, ohjausparametrit, hakutoiveenHyvaksyttyJaJulkaistuDate, vastaanotettavuusVirkailijana)
      val (virkailijanTilat, _) = tilatietoJaVastaanottoDeadline(valintatila, vastaanotto, ohjausparametrit, hakutoiveenHyvaksyttyJaJulkaistuDate, true)

      val hyväksyttyJulkaistussaJonossa = Valintatila.isHyväksytty(valintatila) && jono.isJulkaistavissa
      val julkaistavissa = hyväksyttyJulkaistussaJonossa || kaikkiJonotJulkaistu(hakutoive)
      val pisteet: Option[BigDecimal] = Option(jono.getPisteet).map((p: java.math.BigDecimal) => new BigDecimal(p))

      HakutoiveenSijoitteluntulos(
        HakukohdeOid(hakutoive.getHakukohdeOid),
        hakutoive.getTarjoajaOid,
        ValintatapajonoOid(jono.getValintatapajonoOid),
        hakijanTilat = hakijanTilat,
        virkailijanTilat = virkailijanTilat,
        vastaanottoDeadline.map(_.toDate),
        SijoitteluajonIlmoittautumistila(Option(jono.getIlmoittautumisTila).getOrElse(IlmoittautumisTila.EI_TEHTY)),
        viimeisinHakemuksenTilanMuutos = Option(jono.getHakemuksenTilanViimeisinMuutos),
        viimeisinValintatuloksenMuutos = Option(jono.getValintatuloksenViimeisinMuutos),
        Option(jono.getJonosija).map(_.toInt),
        Option(jono.getVarasijojaKaytetaanAlkaen),
        Option(jono.getVarasijojaTaytetaanAsti),
        Option(jono.getVarasijanNumero).map(_.toInt),
        julkaistavissa,
        ehdollisestiHyvaksyttavissa = jono.isEhdollisestiHyvaksyttavissa,
        ehdollisenHyvaksymisenEhtoKoodi = Option(jono.getEhdollisenHyvaksymisenEhtoKoodi),
        ehdollisenHyvaksymisenEhtoFI = Option(jono.getEhdollisenHyvaksymisenEhtoFI),
        ehdollisenHyvaksymisenEhtoSV = Option(jono.getEhdollisenHyvaksymisenEhtoSV),
        ehdollisenHyvaksymisenEhtoEN = Option(jono.getEhdollisenHyvaksymisenEhtoEN),
        jono.getTilanKuvaukset.toMap,
        pisteet
      )
    }

    HakemuksenSijoitteluntulos(HakemusOid(hakija.getHakemusOid), Option(StringUtils.trimToNull(hakija.getHakijaOid)), hakutoiveidenYhteenvedot)
  }

  private def hakemuksenKevytYhteenveto(hakija: KevytHakijaDTO,
                                        ohjausparametrit: Option[Ohjausparametrit],
                                        hyvaksyttyJulkaistuDates: Map[HakukohdeOid, OffsetDateTime],
                                        vastaanottoRecord: Set[VastaanottoRecord]): HakemuksenSijoitteluntulos = {
    val hakutoiveidenYhteenvedot = hakija.getHakutoiveet.toList.map { hakutoive: KevytHakutoiveDTO =>
      val vastaanotto = vastaanottoRecord.find(v => v.hakukohdeOid.toString == hakutoive.getHakukohdeOid).map(_.action)
      val jono = JonoFinder.merkitseväJono(hakutoive).get
      val valintatila = jononValintatila(jono, hakutoive)

      val hakutoiveenHyvaksyttyJaJulkaistuDate = hyvaksyttyJulkaistuDates.get(HakukohdeOid(hakutoive.getHakukohdeOid))

      val (hakijanTilat, vastaanottoDeadline) = tilatietoJaVastaanottoDeadline(valintatila, vastaanotto, ohjausparametrit, hakutoiveenHyvaksyttyJaJulkaistuDate, false)
      val (virkailijanTilat, _) = tilatietoJaVastaanottoDeadline(valintatila, vastaanotto, ohjausparametrit, hakutoiveenHyvaksyttyJaJulkaistuDate, true)

      val hyväksyttyJulkaistussaJonossa = Valintatila.isHyväksytty(valintatila) && jono.isJulkaistavissa
      val julkaistavissa = hyväksyttyJulkaistussaJonossa || kaikkiJonotJulkaistu(hakutoive)

      val pisteet: Option[BigDecimal] = Option(jono.getPisteet).map((p: java.math.BigDecimal) => new BigDecimal(p))

      HakutoiveenSijoitteluntulos(
        HakukohdeOid(hakutoive.getHakukohdeOid),
        hakutoive.getTarjoajaOid,
        ValintatapajonoOid(jono.getValintatapajonoOid),
        hakijanTilat = hakijanTilat,
        virkailijanTilat = virkailijanTilat,
        vastaanottoDeadline.map(_.toDate),
        SijoitteluajonIlmoittautumistila(Option(jono.getIlmoittautumisTila).getOrElse(IlmoittautumisTila.EI_TEHTY)),
        viimeisinHakemuksenTilanMuutos = Option(jono.getHakemuksenTilanViimeisinMuutos),
        viimeisinValintatuloksenMuutos = Option(jono.getValintatuloksenViimeisinMuutos),
        Option(jono.getJonosija).map(_.toInt),
        Option(jono.getVarasijojaKaytetaanAlkaen),
        Option(jono.getVarasijojaTaytetaanAsti),
        Option(jono.getVarasijanNumero).map(_.toInt),
        julkaistavissa,
        ehdollisestiHyvaksyttavissa = jono.isEhdollisestiHyvaksyttavissa,
        ehdollisenHyvaksymisenEhtoKoodi = Option(jono.getEhdollisenHyvaksymisenEhtoKoodi),
        ehdollisenHyvaksymisenEhtoFI = Option(jono.getEhdollisenHyvaksymisenEhtoFI),
        ehdollisenHyvaksymisenEhtoSV = Option(jono.getEhdollisenHyvaksymisenEhtoSV),
        ehdollisenHyvaksymisenEhtoEN = Option(jono.getEhdollisenHyvaksymisenEhtoEN),
        jono.getTilanKuvaukset.toMap,
        pisteet
      )
    }

    HakemuksenSijoitteluntulos(HakemusOid(hakija.getHakemusOid), Option(StringUtils.trimToNull(hakija.getHakijaOid)), hakutoiveidenYhteenvedot)
  }

  private def tilatietoJaVastaanottoDeadline(valintatila: Valintatila,
                                             vastaanotto: Option[VastaanottoAction],
                                             ohjausparametrit: Option[Ohjausparametrit],
                                             hakutoiveenHyvaksyttyJaJulkaistuDate: Option[OffsetDateTime],
                                             vastaanotettavuusVirkailijana: Boolean):(HakutoiveenSijoittelunTilaTieto, Option[DateTime]) = {
    val ( vastaanottotila, vastaanottoDeadline ) = laskeVastaanottotila(valintatila, vastaanotto, ohjausparametrit, hakutoiveenHyvaksyttyJaJulkaistuDate, vastaanotettavuusVirkailijana)
    val uusiValintatila = vastaanottotilanVaikutusValintatilaan(valintatila, vastaanottotila)
    val vastaanotettavuustila: Vastaanotettavuustila.Value = laskeVastaanotettavuustila(valintatila, vastaanottotila)
    (HakutoiveenSijoittelunTilaTieto(uusiValintatila, vastaanottotila, vastaanotettavuustila), vastaanottoDeadline)
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

  private def laskeVastaanottotila(valintatila: Valintatila,
                                   vastaanotto: Option[VastaanottoAction],
                                   ohjausparametrit: Option[Ohjausparametrit],
                                   hakutoiveenHyvaksyttyJaJulkaistuDate: Option[OffsetDateTime],
                                   vastaanotettavuusVirkailijana: Boolean = false): ( Vastaanottotila, Option[DateTime] ) = {
    val deadline = laskeVastaanottoDeadline(ohjausparametrit, hakutoiveenHyvaksyttyJaJulkaistuDate)
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
    findLatestSijoitteluAjo(hakuOid, Some(hakukohdeOid)) match {
      case None => Set()

      case Some(sijoitteluAjo) =>
        val ohjausparametrit = findOhjausparametritFromOhjausparametritService(hakuOid)
        val hyvaksyttyJaJulkaistuDates = hakijaVastaanottoRepository.findHyvaksyttyJulkaistuDatesForHakukohde(hakukohdeOid)
        def queriedHakijasForHakukohde() = {
          val allHakijasForHakukohde = Timer.timed(s"Fetch hakemukset just for hakukohde $hakukohdeOid of haku $hakuOid", 1000) {
            raportointiService.hakemuksetVainHakukohteenTietojenKanssa(sijoitteluAjo, hakukohdeOid)
          }
          allHakijasForHakukohde.filter(hakijaDto => hakemusOids.contains(HakemusOid(hakijaDto.getHakemusOid)))
        }

        def calculateLateness(hakijaDto: KevytHakijaDTO): VastaanottoAikarajaMennyt = {
          val hakutoiveDtoOfThisHakukohde: Option[KevytHakutoiveDTO] = hakijaDto.getHakutoiveet.toList.find(_.getHakukohdeOid == hakukohdeOid)
          val vastaanottoDeadline: Option[DateTime] = hakutoiveDtoOfThisHakukohde.flatMap { hakutoive: KevytHakutoiveDTO =>
            laskeVastaanottoDeadline(ohjausparametrit, hyvaksyttyJaJulkaistuDates.get(hakijaDto.getHakijaOid))
          }
          val isLate: Boolean = vastaanottoDeadline.exists(new DateTime().isAfter)
          VastaanottoAikarajaMennyt(HakemusOid(hakijaDto.getHakemusOid), isLate, vastaanottoDeadline)
        }
        queriedHakijasForHakukohde().map(calculateLateness).toSet
    }
  }

  private def laskeVastaanottoDeadline(ohjausparametrit: Option[Ohjausparametrit], hakutoiveenHyvaksyttyJaJulkaistuDate: Option[OffsetDateTime]): Option[DateTime] = {
    ohjausparametrit.map(_.vastaanottoaikataulu) match {
      case Some(Vastaanottoaikataulu(Some(deadlineFromHaku), buffer)) =>
        val deadlineFromHakemuksenTilanMuutos = getDeadlineWithBuffer(ohjausparametrit, hakutoiveenHyvaksyttyJaJulkaistuDate, buffer, deadlineFromHaku)
        val deadlines = Some(deadlineFromHaku) ++ deadlineFromHakemuksenTilanMuutos
        Some(deadlines.maxBy((a: DateTime) => a.getMillis))
      case _ => None
    }
  }

  private def vastaanottotilanVaikutusValintatilaan(valintatila: Valintatila, vastaanottotila : Vastaanottotila): Valintatila = {
    if (List(Vastaanottotila.ehdollisesti_vastaanottanut, Vastaanottotila.vastaanottanut).contains(vastaanottotila)) {
      if (Valintatila.isHyväksytty(valintatila)) {
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

  def max(d1: DateTime, d2: DateTime): DateTime = if(d1.isAfter(d2)) d1 else d2

  private def getDeadlineWithBuffer(ohjausparametrit: Option[Ohjausparametrit], hakutoiveenHyvaksyttyJaJulkaistuOption: Option[OffsetDateTime], bufferOption: Option[Int], deadline: DateTime): Option[DateTime] = {
    for {
      viimeisinMuutos <- hakutoiveenHyvaksyttyJaJulkaistuOption.map(d => new DateTime(
        d.toInstant.toEpochMilli,
        DateTimeZone.forOffsetMillis(Math.toIntExact(TimeUnit.SECONDS.toMillis(d.getOffset.getTotalSeconds)))
      ))
      haunValintaesitysHyvaksyttavissa: DateTime <- ohjausparametrit.map(_.valintaesitysHyvaksyttavissa.getOrElse(new DateTime(0)))
      buffer <- bufferOption
    } yield max(viimeisinMuutos, haunValintaesitysHyvaksyttavissa).plusDays(buffer).withTime(deadline.getHourOfDay, deadline.getMinuteOfHour, deadline.getSecondOfMinute, deadline.getMillisOfSecond)
  }

  private def ifNull[T](value: T, defaultValue: T): T = {
    if (value == null) defaultValue
    else value
  }
}
