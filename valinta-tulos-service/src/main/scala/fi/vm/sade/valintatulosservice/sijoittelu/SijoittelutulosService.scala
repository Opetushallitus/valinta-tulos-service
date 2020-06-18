package fi.vm.sade.valintatulosservice.sijoittelu

import java.time.OffsetDateTime
import java.util.Date

import fi.vm.sade.sijoittelu.domain.SijoitteluAjo
import fi.vm.sade.sijoittelu.tulos.dto.raportointi._
import fi.vm.sade.sijoittelu.tulos.dto.{HakemuksenTila, IlmoittautumisTila}
import fi.vm.sade.utils.Timer
import fi.vm.sade.valintatulosservice.domain.Valintatila._
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, OhjausparametritService}
import fi.vm.sade.valintatulosservice.sijoittelu.JonoFinder.kaikkiJonotJulkaistu
import fi.vm.sade.valintatulosservice.valintarekisteri.db._
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila._
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.{PersonOidFromHakemusResolver, VastaanottoAikarajaMennyt}
import org.apache.commons.lang.StringUtils
import org.apache.commons.lang3.builder.ToStringBuilder
import org.joda.time.DateTime
import slick.dbio.DBIO

import scala.concurrent.ExecutionContext.Implicits.global

class SijoittelutulosService(raportointiService: ValintarekisteriRaportointiService,
                             ohjausparametritService: OhjausparametritService,
                             valintarekisteriDb: HakijaRepository with HakijaVastaanottoRepository with SijoitteluRepository with ValinnantulosRepository,
                             sijoittelunTulosClient: ValintarekisteriSijoittelunTulosClient) {
  import fi.vm.sade.valintatulosservice.vastaanotto.VastaanottoUtils.laskeVastaanottoDeadline

  import scala.collection.JavaConversions._

  def tulosHakijana(hakuOid: HakuOid,
                    hakemusOid: HakemusOid,
                    henkiloOid: String,
                    ohjausparametrit: Ohjausparametrit): DBIO[HakemuksenSijoitteluntulos] = {
    tulos(hakuOid, hakemusOid, henkiloOid, ohjausparametrit, vastaanotettavuusVirkailijana = false)
  }

  def tulosVirkailijana(hakuOid: HakuOid,
                        hakemusOid: HakemusOid,
                        henkiloOid: String,
                        ohjausparametrit: Ohjausparametrit): DBIO[HakemuksenSijoitteluntulos] = {
    tulos(hakuOid, hakemusOid, henkiloOid, ohjausparametrit, vastaanotettavuusVirkailijana = true)
  }

  private def tulos(hakuOid: HakuOid,
                    hakemusOid: HakemusOid,
                    henkiloOid: String,
                    ohjausparametrit: Ohjausparametrit,
                    vastaanotettavuusVirkailijana: Boolean): DBIO[HakemuksenSijoitteluntulos] = {
    for {
      valinnantulokset <- valintarekisteriDb.getValinnantuloksetForHakemus(hakemusOid)
      hyvaksyttyJulkaistuDates <- valintarekisteriDb.findHyvaksyttyJulkaistuDatesForHenkilo(henkiloOid)
      vastaanottoRecords <- valintarekisteriDb.findHenkilonVastaanototHaussa(henkiloOid, hakuOid)
      latestSijoitteluajoId <- valintarekisteriDb.getLatestSijoitteluajoId(hakuOid)
      hakutoiveetSijoittelussa <- latestSijoitteluajoId.fold(DBIO.successful(List.empty[HakutoiveRecord]))(valintarekisteriDb.getHakemuksenHakutoiveetSijoittelussa(hakemusOid, _))
      valintatapajonotSijoittelussa <- latestSijoitteluajoId.fold(DBIO.successful(List.empty[HakutoiveenValintatapajonoRecord]))(valintarekisteriDb.getHakemuksenHakutoiveidenValintatapajonotSijoittelussa(hakemusOid, _))
    } yield HakemuksenSijoitteluntulos(
      hakemusOid,
      Some(henkiloOid),
      valinnantulokset.map(_.hakukohdeOid).union(hakutoiveetSijoittelussa.map(_.hakukohdeOid).toSet)
        .toList
        .map(oid => (
          valinnantulokset.filter(_.hakukohdeOid == oid),
          valintatapajonotSijoittelussa.filter(_.hakukohdeOid == oid),
          vastaanottoRecords.find(_.hakukohdeOid == oid),
          hakutoiveetSijoittelussa.find(_.hakukohdeOid == oid),
          hyvaksyttyJulkaistuDates.get(oid)
        ))
        .sortBy({ case (_, _, _, hakutoive, _) => hakutoive.flatMap(_.hakutoive) }) // FIXME hakutoivejärjestys hakemukselta
        .map({ case (valinnantulokset, valintatapajonot, vastaanotto, hakutoive, hakutoiveenHyvaksyttyJaJulkaistuDate) =>
          hakutoiveenSijoittelunTulos(ohjausparametrit,
            vastaanotettavuusVirkailijana,
            valinnantulokset,
            valintatapajonot,
            vastaanotto,
            hakutoive,
            hakutoiveenHyvaksyttyJaJulkaistuDate)
        }))
  }

  private def hakutoiveenSijoittelunTulos(ohjausparametrit: Ohjausparametrit,
                                          vastaanotettavuusVirkailijana: Boolean,
                                          valinnantulokset: Set[Valinnantulos],
                                          valintatapajonot: List[HakutoiveenValintatapajonoRecord],
                                          vastaanotto: Option[VastaanottoRecord],
                                          hakutoive: Option[HakutoiveRecord],
                                          hakutoiveenHyvaksyttyJaJulkaistuDate: Option[OffsetDateTime]
                                         ): HakutoiveenSijoitteluntulos = {

    val jonot = valinnantulokset.map(_.valintatapajonoOid).union(valintatapajonot.map(_.valintatapajonoOid).toSet)
      .toList
      .map(oid => (valinnantulokset.find(_.valintatapajonoOid == oid), valintatapajonot.find(_.valintatapajonoOid == oid)))
      .sortBy({ case (_, valintatapajono) => valintatapajono.map(_.valintatapajonoPrioriteetti) })
    val (merkitsevaValinnantulos, merkitsevaValintatapajono) = jonot.minBy({
      case (valinnantulos, valintatapajono) =>
        (
          valinnantulos.map(v => Valintatila.withName(v.valinnantila.valinnantila.name)).getOrElse(Valintatila.kesken),
          valinnantulos.filter(_.valinnantila == Varalla).flatMap(_ => valintatapajono.flatMap(_.varasijanNumero)),
          valintatapajono.map(_.valintatapajonoPrioriteetti)
        )
    })(Ordering.Tuple3(Ordering.ordered[Valintatila], Ordering.Option[Int], Ordering.Option[Int]))
    val valintatila = jononValintatila(
      merkitsevaValinnantulos,
      merkitsevaValintatapajono,
      hakutoive
    )

    val (hakijanTilat, vastaanottoDeadline) = tilatietoJaVastaanottoDeadline(valintatila, vastaanotto, ohjausparametrit, hakutoiveenHyvaksyttyJaJulkaistuDate, vastaanotettavuusVirkailijana)
    val (virkailijanTilat, _) = tilatietoJaVastaanottoDeadline(valintatila, vastaanotto, ohjausparametrit, hakutoiveenHyvaksyttyJaJulkaistuDate, vastaanotettavuusVirkailijana = true)

    val hyväksyttyJulkaistussaJonossa = Valintatila.isHyväksytty(valintatila) && merkitsevaValinnantulos.flatMap(_.julkaistavissa).getOrElse(false)
    val julkaistavissa = hyväksyttyJulkaistussaJonossa || jonot.forall({ case (valinnantulos, _) => valinnantulos.flatMap(_.julkaistavissa).getOrElse(false) })
    val pisteet = merkitsevaValintatapajono.flatMap(_.pisteet)

    val tilankuvaukset = {
      val (valinnantulos, valintatapajono) = if (Valintatila.hylätty == valintatila) {
        jonot.last
      } else {
        (merkitsevaValinnantulos, merkitsevaValintatapajono)
      }
      tilanKuvaukset(valinnantulos, valintatapajono)
    }

    HakutoiveenSijoitteluntulos(
      merkitsevaValinnantulos.map(_.hakukohdeOid).orElse(hakutoive.map(_.hakukohdeOid)).get,
      null, // FIXME
      merkitsevaValinnantulos.map(_.valintatapajonoOid).orElse(merkitsevaValintatapajono.map(_.valintatapajonoOid)).get,
      hakijanTilat = hakijanTilat,
      virkailijanTilat = virkailijanTilat,
      vastaanottoDeadline.map(_.toDate),
      merkitsevaValinnantulos.map(_.ilmoittautumistila).getOrElse(EiTehty),
      viimeisinHakemuksenTilanMuutos = merkitsevaValinnantulos.flatMap(_.valinnantilanViimeisinMuutos).map(odt => Date.from(odt.toInstant)),
      viimeisinValintatuloksenMuutos = merkitsevaValinnantulos.flatMap(_.vastaanotonViimeisinMuutos).map(odt => Date.from(odt.toInstant)),
      merkitsevaValintatapajono.map(_.jonosija),
      merkitsevaValintatapajono.flatMap(_.varasijojaKaytetaanAlkaen),
      merkitsevaValintatapajono.flatMap(_.varasijojaTaytetaanAsti),
      merkitsevaValintatapajono.flatMap(_.varasijanNumero),
      julkaistavissa,
      ehdollisestiHyvaksyttavissa = merkitsevaValinnantulos.flatMap(_.ehdollisestiHyvaksyttavissa).getOrElse(false),
      ehdollisenHyvaksymisenEhtoKoodi = merkitsevaValinnantulos.flatMap(_.ehdollisenHyvaksymisenEhtoKoodi),
      ehdollisenHyvaksymisenEhtoFI = merkitsevaValinnantulos.flatMap(_.ehdollisenHyvaksymisenEhtoFI),
      ehdollisenHyvaksymisenEhtoSV = merkitsevaValinnantulos.flatMap(_.ehdollisenHyvaksymisenEhtoSV),
      ehdollisenHyvaksymisenEhtoEN = merkitsevaValinnantulos.flatMap(_.ehdollisenHyvaksymisenEhtoEN),
      tilankuvaukset,
      pisteet,
      jonokohtaisetTulostiedot = jonot.map({ case (valinnantulos, valintatapajono) =>
        jonokohtainenTulostieto(merkitsevaValinnantulos, merkitsevaValintatapajono, hakijanTilat, valinnantulos, valintatapajono)
      })
    )
  }

  private def jonokohtainenTulostieto(merkitsevaValinnantulos: Option[Valinnantulos],
                                      merkitsevaValintatapajono: Option[HakutoiveenValintatapajonoRecord],
                                      hakijanTilat: HakutoiveenSijoittelunTilaTieto,
                                      valinnantulos: Option[Valinnantulos],
                                      valintatapajono: Option[HakutoiveenValintatapajonoRecord]
                                     ): JonokohtainenTulostieto = {
    JonokohtainenTulostieto(
      oid = valinnantulos.map(_.valintatapajonoOid).orElse(valintatapajono.map(_.valintatapajonoOid)).get,
      nimi = valintatapajono.map(_.valintatapajonoNimi).getOrElse(""),
      pisteet = valintatapajono.flatMap(_.pisteet),
      alinHyvaksyttyPistemaara = valintatapajono.flatMap(_.alinHyvaksyttyPistemaara),
      valintatila = vastaanottotilanVaikutusValintatilaan(
        hakemuksenTilastaJononValintatilaksi(valinnantulos, valintatapajono),
        hakijanTilat.vastaanottotila,
        (merkitsevaValinnantulos, merkitsevaValintatapajono) == (valinnantulos, valintatapajono)
      ),
      julkaistavissa = valinnantulos.flatMap(_.julkaistavissa).getOrElse(false),
      valintatapajonoPrioriteetti = valintatapajono.map(_.valintatapajonoPrioriteetti),
      tilanKuvaukset = valinnantulos.map(v => tilanKuvaukset(Some(v), valintatapajono)),
      ehdollisestiHyvaksyttavissa = valinnantulos.flatMap(_.ehdollisestiHyvaksyttavissa).getOrElse(false),
      ehdollisenHyvaksymisenEhto = Some(EhdollisenHyvaksymisenEhto(
        FI = valinnantulos.flatMap(_.ehdollisenHyvaksymisenEhtoFI),
        SV = valinnantulos.flatMap(_.ehdollisenHyvaksymisenEhtoSV),
        EN = valinnantulos.flatMap(_.ehdollisenHyvaksymisenEhtoEN)
      )),
      varasijanumero = valintatapajono.flatMap(_.varasijanNumero),
      eiVarasijatayttoa = valintatapajono.exists(_.eiVarasijatayttoa),
      varasijat = valintatapajono.flatMap(_.varasijat).filter(_ != 0),
      varasijasaannotKaytossa = valintatapajono.exists(_.sijoiteltuIlmanVarasijasaantojaNiidenOllessaVoimassa)
    )
  }

  def hakemustenTulos(hakuOid: HakuOid,
                      hakukohdeOid: Option[HakukohdeOid],
                      personOidResolver: PersonOidFromHakemusResolver,
                      haunVastaanotot: Option[Map[String, Set[VastaanottoRecord]]] = None,
                      vainHakukohde: Boolean = false): List[HakemuksenSijoitteluntulos] = {
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
        case Some(hakukohde) =>
          if (vainHakukohde)
            Option(Timer.timed("hakukohteen hakemukset", 1000)(raportointiService.hakemuksetVainHakukohteenTietojenKanssa(sijoittelu, hakukohde)))
          else
            Option(Timer.timed("hakukohteen hakemukset", 1000)(raportointiService.kevytHakemukset(sijoittelu, hakukohde)))
        case None => Option(Timer.timed("hakemukset", 1000)(raportointiService.kevytHakemukset(sijoittelu)))
      };
      hakijat <- {
        val hyvaksyttyJaJulkaistuDates = valintarekisteriDb.findHyvaksyttyJulkaistuDatesForHaku(hakuOid)
        Option(hakijaDtot.map(h => hakemuksenKevytYhteenveto(h, ohjausparametrit, hyvaksyttyJaJulkaistuDates.getOrElse(h.getHakijaOid, Map()),
          fetchVastaanottos(HakemusOid(h.getHakemusOid), Option(h.getHakijaOid)))))
      }
    ) yield {
      hakijat
    }).getOrElse(Nil)
  }

  def findOhjausparametritFromOhjausparametritService(hakuOid: HakuOid): Ohjausparametrit = {
    Timer.timed("findAikatauluFromOhjausparametritService -> ohjausparametritService.ohjausparametrit", 100) {
      ohjausparametritService.ohjausparametrit(hakuOid) match {
        case Right(o) => o
        case Left(e) => throw e
      }
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

  def sijoittelunTuloksetWithoutVastaanottoTieto(hakuOid: HakuOid, sijoitteluajoId: String, hyvaksytyt: Option[Boolean], ilmanHyvaksyntaa: Option[Boolean], vastaanottaneet: Option[Boolean],
                                                 hakukohdeOid: Option[List[HakukohdeOid]], count: Option[Int], index: Option[Int]): HakijaPaginationObject = {

    val id: Option[Long] = findSijoitteluAjo(hakuOid, sijoitteluajoId)
    raportointiService.hakemukset(id, hakuOid, hyvaksytyt, ilmanHyvaksyntaa, vastaanottaneet, hakukohdeOid, count, index)
  }

  def sijoittelunTulosForAjoWithoutVastaanottoTieto(sijoitteluajoId: Option[Long], hakuOid: HakuOid, hakemusOid: HakemusOid): Option[HakijaDTO] =
    findHakemus(hakemusOid, sijoitteluajoId, hakuOid)

  @Deprecated //TODO: Ei toimi erillishaulla, jolla ei ole laskentaa, jos käytössä PostgreSQL eikä Mongo. Käytetäänkö vielä oikeasti?
  def findSijoitteluAjo(hakuOid: HakuOid, sijoitteluajoId: String): Option[Long] = {
    if ("latest" == sijoitteluajoId) {
      valintarekisteriDb.runBlocking(valintarekisteriDb.getLatestSijoitteluajoId(hakuOid))
    } else raportointiService.getSijoitteluAjo(sijoitteluajoId.toLong).map(_.getSijoitteluajoId)
  }

  private def findHakemus(hakemusOid: HakemusOid, sijoitteluajoId: Option[Long], hakuOid: HakuOid): Option[HakijaDTO] = {
    Timer.timed("SijoittelutulosService -> sijoittelunTulosClient.fetchHakemuksenTulos", 1000) {
      sijoittelunTulosClient.fetchHakemuksenTulos(sijoitteluajoId, hakuOid, hakemusOid)
    }
  }

  private def fetchVastaanotto(henkiloOid: String, hakuOid: HakuOid): Set[VastaanottoRecord] = {
    Timer.timed("hakijaVastaanottoRepository.findHenkilonVastaanototHaussa", 100) {
      valintarekisteriDb.runBlocking(valintarekisteriDb.findHenkilonVastaanototHaussa(henkiloOid, hakuOid))
    }
  }

  private def hakemuksenKevytYhteenveto(hakija: KevytHakijaDTO,
                                        ohjausparametrit: Ohjausparametrit,
                                        hyvaksyttyJulkaistuDates: Map[HakukohdeOid, OffsetDateTime],
                                        vastaanottoRecord: Set[VastaanottoRecord]): HakemuksenSijoitteluntulos = {
    val hakutoiveidenYhteenvedot = hakija.getHakutoiveet.toList.map { hakutoive: KevytHakutoiveDTO =>
      val vastaanotto = vastaanottoRecord.find(v => v.hakukohdeOid.toString == hakutoive.getHakukohdeOid)
      val jono = JonoFinder.merkitseväJono(hakutoive).getOrElse {
        throw new IllegalStateException(s"Ei löydy merkitsevää jonoa hakemuksen ${hakija.getHakemusOid} hakutoiveelle ${hakutoive.getHakukohdeOid} . " +
          s"Kaikki jonot (${hakutoive.getHakutoiveenValintatapajonot.size} kpl): ${hakutoive.getHakutoiveenValintatapajonot.map(ToStringBuilder.reflectionToString)}")
      }
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
        pisteet,
        jonokohtaisetTulostiedot = List()
      )
    }

    HakemuksenSijoitteluntulos(HakemusOid(hakija.getHakemusOid), Option(StringUtils.trimToNull(hakija.getHakijaOid)), hakutoiveidenYhteenvedot)
  }

  private def tilanKuvaukset(valinnantulos: Option[Valinnantulos],
                             valintatapajono: Option[HakutoiveenValintatapajonoRecord]): Map[String, String] = {
    def replaceLisatieto(text: String, tarkenteenLisatieto: Option[String]): String =
      tarkenteenLisatieto.map(text.replace("<lisatieto>", _)).getOrElse(text)
    List(
      valinnantulos.flatMap(_.valinnantilanKuvauksenTekstiFI).map("FI" -> replaceLisatieto(_, valintatapajono.flatMap(_.tarkenteenLisatieto))),
      valinnantulos.flatMap(_.valinnantilanKuvauksenTekstiSV).map("SV" -> replaceLisatieto(_, valintatapajono.flatMap(_.tarkenteenLisatieto))),
      valinnantulos.flatMap(_.valinnantilanKuvauksenTekstiEN).map("EN" -> replaceLisatieto(_, valintatapajono.flatMap(_.tarkenteenLisatieto)))
    ).flatten.toMap
  }

  private def tilatietoJaVastaanottoDeadline(valintatila: Valintatila,
                                             vastaanotto: Option[VastaanottoRecord],
                                             ohjausparametrit: Ohjausparametrit,
                                             hakutoiveenHyvaksyttyJaJulkaistuDate: Option[OffsetDateTime],
                                             vastaanotettavuusVirkailijana: Boolean):(HakutoiveenSijoittelunTilaTieto, Option[DateTime]) = {
    val ( vastaanottotila, vastaanottoDeadline ) = laskeVastaanottotila(valintatila, vastaanotto, ohjausparametrit, hakutoiveenHyvaksyttyJaJulkaistuDate, vastaanotettavuusVirkailijana)
    val uusiValintatila = vastaanottotilanVaikutusValintatilaan(valintatila, vastaanottotila, merkitsevaJono = true)
    val vastaanotettavuustila: Vastaanotettavuustila.Value = laskeVastaanotettavuustila(valintatila, vastaanottotila)
    (
      HakutoiveenSijoittelunTilaTieto(
        uusiValintatila,
        vastaanottotila,
        vastaanotto.map(v => if (v.ilmoittaja == "järjestelmä") { Sijoittelu } else { Henkilo(v.ilmoittaja) }),
        vastaanotettavuustila
      ),
      vastaanottoDeadline
    )
  }

  private def laskeVastaanotettavuustila(valintatila: Valintatila, vastaanottotila: Vastaanottotila): Vastaanotettavuustila.Value = {
    if (Valintatila.isHyväksytty(valintatila) && Set(Vastaanottotila.kesken, Vastaanottotila.ehdollisesti_vastaanottanut).contains(vastaanottotila)) {
      Vastaanotettavuustila.vastaanotettavissa_sitovasti
    } else {
      Vastaanotettavuustila.ei_vastaanotettavissa
    }
  }

  private def hakemuksenTilastaJononValintatilaksi(valinnantulos: Option[Valinnantulos],
                                                   valintatapajono: Option[HakutoiveenValintatapajonoRecord]): Valintatila = {
    val valintatila = valinnantulos
      .map(v => Valintatila.withName(v.valinnantila.valinnantila.name))
      .getOrElse(Valintatila.kesken)
    if (Valintatila.isHyväksytty(valintatila) && valintatapajono.exists(_.hyvaksyttyHarkinnanvaraisesti)) {
      Valintatila.harkinnanvaraisesti_hyväksytty
    } else {
      valintatila
    }
  }

  private def jononValintatila(valinnantulos: Option[Valinnantulos],
                               valintatapajono: Option[HakutoiveenValintatapajonoRecord],
                               hakutoive: Option[HakutoiveRecord]) = {
    val valintatila = hakemuksenTilastaJononValintatilaksi(valinnantulos, valintatapajono)
    val kaikkiJonotSijoiteltu = hakutoive.flatMap(_.kaikkiJonotsijoiteltu).getOrElse(true)
    if (!(Valintatila.isHyväksytty(valintatila) || kaikkiJonotSijoiteltu)) {
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
    } else if (valintatila == Valintatila.varalla && jono.isEiVarasijatayttoa) {
      Valintatila.kesken
    } else {
      valintatila
    }
  }

  def vastaanottotilaVainViimeisimmanVastaanottoActioninPerusteella(vastaanotto: Option[VastaanottoRecord]): Vastaanottotila = vastaanotto.map(_.action) match {
    case Some(Poista) | None => Vastaanottotila.kesken
    case Some(Peru) => Vastaanottotila.perunut
    case Some(VastaanotaSitovasti) => Vastaanottotila.vastaanottanut
    case Some(VastaanotaEhdollisesti) => Vastaanottotila.ehdollisesti_vastaanottanut
    case Some(Peruuta) => Vastaanottotila.peruutettu
    case Some(MerkitseMyohastyneeksi) => Vastaanottotila.ei_vastaanotettu_määräaikana
  }

  private def laskeVastaanottotila(valintatila: Valintatila,
                                   vastaanotto: Option[VastaanottoRecord],
                                   ohjausparametrit: Ohjausparametrit,
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
        val hyvaksyttyJaJulkaistuDates = valintarekisteriDb.findHyvaksyttyJulkaistuDatesForHakukohde(hakukohdeOid)
        def queriedHakijasForHakukohde() = {
          val allHakijasForHakukohde = Timer.timed(s"Fetch hakemukset just for hakukohde $hakukohdeOid of haku $hakuOid", 1000) {
            raportointiService.hakemuksetVainHakukohteenTietojenKanssa(sijoitteluAjo, hakukohdeOid)
          }
          allHakijasForHakukohde.filter(hakijaDto => hakemusOids.contains(HakemusOid(hakijaDto.getHakemusOid)))
        }

        def calculateLateness(hakijaDto: KevytHakijaDTO): VastaanottoAikarajaMennyt = {
          val hakutoiveDtoOfThisHakukohde: Option[KevytHakutoiveDTO] = hakijaDto.getHakutoiveet.toList.find(_.getHakukohdeOid == hakukohdeOid.toString)
          val vastaanottoDeadline: Option[DateTime] = hakutoiveDtoOfThisHakukohde.flatMap { hakutoive: KevytHakutoiveDTO =>
            laskeVastaanottoDeadline(ohjausparametrit, hyvaksyttyJaJulkaistuDates.get(hakijaDto.getHakijaOid))
          }
          val isLate: Boolean = vastaanottoDeadline.exists(new DateTime().isAfter)
          VastaanottoAikarajaMennyt(HakemusOid(hakijaDto.getHakemusOid), isLate, vastaanottoDeadline)
        }
        queriedHakijasForHakukohde().map(calculateLateness).toSet
    }
  }

  private def vastaanottotilanVaikutusValintatilaan(valintatila: Valintatila, vastaanottotila : Vastaanottotila, merkitsevaJono: Boolean): Valintatila = {
    if (List(Vastaanottotila.ehdollisesti_vastaanottanut, Vastaanottotila.vastaanottanut).contains(vastaanottotila) && merkitsevaJono) {
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

  private def ifNull[T](value: T, defaultValue: T): T = {
    if (value == null) defaultValue
    else value
  }
}
