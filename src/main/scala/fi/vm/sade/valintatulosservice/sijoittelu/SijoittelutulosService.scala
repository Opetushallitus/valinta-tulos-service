package fi.vm.sade.valintatulosservice.sijoittelu

import java.util.{Date, Optional}

import fi.vm.sade.sijoittelu.domain.SijoitteluAjo
import fi.vm.sade.sijoittelu.tulos.dto.raportointi._
import fi.vm.sade.sijoittelu.tulos.dto.{HakemuksenTila, IlmoittautumisTila}
import fi.vm.sade.sijoittelu.tulos.resource.SijoitteluResource
import fi.vm.sade.sijoittelu.tulos.service.RaportointiService
import fi.vm.sade.utils.Timer
import fi.vm.sade.valintatulosservice.domain.Valintatila._
import fi.vm.sade.valintatulosservice.domain.Vastaanottotila._
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.ohjausparametrit.OhjausparametritService
import fi.vm.sade.valintatulosservice.tarjonta.Haku
import fi.vm.sade.valintatulosservice.valintarekisteri.{HakijaVastaanottoRepository, VastaanottoRecord}
import org.apache.commons.lang.StringUtils
import org.joda.time.DateTime

class SijoittelutulosService(raportointiService: RaportointiService,
                             ohjausparametritService: OhjausparametritService,
                             hakijaVastaanottoRepository: HakijaVastaanottoRepository) {
  import scala.collection.JavaConversions._

  def hakemuksenTulos(haku: Haku, hakemusOid: String, hakijaOidIfFound: Option[String]): Option[HakemuksenSijoitteluntulos] = {
    def aikataulu = ohjausparametritService.ohjausparametrit(haku.oid).flatMap(_.vastaanottoaikataulu)

    for (
      hakijaOid <- hakijaOidIfFound;
      sijoitteluAjo <- fromOptional(raportointiService.latestSijoitteluAjoForHaku(haku.oid));
      hakija: HakijaDTO <- Option(raportointiService.hakemus(sijoitteluAjo, hakemusOid))
    ) yield hakemuksenYhteenveto(hakija, aikataulu, fetchVastaanotto(hakijaOid, haku.oid))
  }

  def hakemustenTulos(hakuOid: String,
                      hakukohdeOid: Option[String] = None,
                      hakijaOidsByHakemusOids: Map[String, String],
                      haunVastaanotot: Option[Map[String,Set[VastaanottoRecord]]] = None): List[HakemuksenSijoitteluntulos] = {
    def fetchVastaanottos(hakemusOid: String, hakijaOidFromSijoittelunTulos: Option[String]): Set[VastaanottoRecord] =
      (hakijaOidsByHakemusOids.get(hakemusOid).orElse(hakijaOidFromSijoittelunTulos), haunVastaanotot) match {
        case (Some(hakijaOid), Some(vastaanotot)) => vastaanotot.getOrElse(hakijaOid, Set())
        case (Some(hakijaOid), None) => fetchVastaanotto(hakijaOid, hakuOid)
        case (None, _) => throw new IllegalStateException(s"No hakija oid for hakemus $hakemusOid")
      }

    val aikataulu = ohjausparametritService.ohjausparametrit(hakuOid).flatMap(_.vastaanottoaikataulu)

    (for (
      sijoittelu <- Timer.timed("latest sijoittelu", 1000)(fromOptional(raportointiService.latestSijoitteluAjoForHaku(hakuOid)));
      hakijat <- {
        hakukohdeOid match {
          case Some(hakukohde) => Option(Timer.timed("hakukohteen hakemukset", 1000)(raportointiService.hakemukset(sijoittelu, hakukohde)))
            .map(_.toList.map(h => hakemuksenKevytYhteenveto(h, aikataulu, fetchVastaanottos(h.getHakemusOid, Option(h.getHakijaOid)))))
          case None => Option(Timer.timed("hakemukset", 1000)(raportointiService.hakemukset(sijoittelu, null, null, null, null, null, null)))
            .map(_.getResults.toList.map(h => hakemuksenYhteenveto(h, aikataulu, fetchVastaanottos(h.getHakemusOid, Option(h.getHakijaOid)))))
        }
      }
    ) yield {
      hakijat
    }).getOrElse(Nil)
  }

  def sijoittelunTuloksetWithoutVastaanottoTieto(hakuOid: String, sijoitteluajoId: String, hyvaksytyt: Option[Boolean], ilmanHyvaksyntaa: Option[Boolean], vastaanottaneet: Option[Boolean],
                                                 hakukohdeOid: Option[List[String]], count: Option[Int], index: Option[Int],
                                                 haunVastaanototByHakijaOid: Map[String, Set[VastaanottoRecord]]): HakijaPaginationObject = {
    import scala.collection.JavaConverters._

    val sijoitteluntulos: Option[SijoitteluAjo] = findSijoitteluAjo(hakuOid, sijoitteluajoId)

    sijoitteluntulos.map { ajo =>
      def toJavaBoolean(b: Option[Boolean]): java.lang.Boolean = b match {
        case Some(scalaBoolean) => scalaBoolean
        case None => null.asInstanceOf[java.lang.Boolean]
      }
      def toJavaInt(i: Option[Int]): java.lang.Integer = i match {
        case Some(scalaInt) => scalaInt
        case None => null
      }

      val hakukohdeOidsAsJava: java.util.List[String] = hakukohdeOid match {
        case Some(oids) => oids.asJava
        case None => null
      }

      raportointiService.hakemukset(ajo, toJavaBoolean(hyvaksytyt), toJavaBoolean(ilmanHyvaksyntaa), toJavaBoolean(vastaanottaneet),
        hakukohdeOidsAsJava, toJavaInt(count), toJavaInt(index))
    }.getOrElse(new HakijaPaginationObject)
  }

  def sijoittelunTulosForAjoWithoutVastaanottoTieto(sijoitteluAjo: SijoitteluAjo, hakemusOid: String): HakijaDTO = raportointiService.hakemus(sijoitteluAjo, hakemusOid)

  def findSijoitteluAjo(hakuOid: String, sijoitteluajoId: String): Option[SijoitteluAjo] = {
    fromOptional(if (SijoitteluResource.LATEST == sijoitteluajoId) {
      raportointiService.latestSijoitteluAjoForHaku(hakuOid)
    } else raportointiService.getSijoitteluAjo(sijoitteluajoId.toLong))
  }

  private def fetchVastaanotto(henkiloOid: String, hakuOid: String): Set[VastaanottoRecord] = {
    hakijaVastaanottoRepository.findHenkilonVastaanototHaussa(henkiloOid, hakuOid)
  }

  private def hakemuksenYhteenveto(hakija: HakijaDTO, aikataulu: Option[Vastaanottoaikataulu], vastaanottoRecord: Set[VastaanottoRecord]): HakemuksenSijoitteluntulos = {

    val hakutoiveidenYhteenvedot = hakija.getHakutoiveet.toList.map { hakutoive: HakutoiveDTO =>
      val vastaanotto = vastaanottoRecord.find(v => v.hakukohdeOid == hakutoive.getHakukohdeOid).map(_.action)
      val jono: HakutoiveenValintatapajonoDTO = JonoFinder.merkitseväJono(hakutoive).get
      var valintatila: Valintatila = jononValintatila(jono, hakutoive)
      val viimeisinHakemuksenTilanMuutos: Option[Date] = Option(jono.getHakemuksenTilanViimeisinMuutos)
      val viimeisinValintatuloksenMuutos: Option[Date] = Option(jono.getValintatuloksenViimeisinMuutos)
      val ( vastaanottotila, vastaanottoDeadline ) = laskeVastaanottotila(valintatila, vastaanotto, aikataulu, viimeisinHakemuksenTilanMuutos)
      valintatila = vastaanottotilanVaikutusValintatilaan(valintatila, vastaanottotila)
      val vastaanotettavuustila: Vastaanotettavuustila.Value = laskeVastaanotettavuustila(valintatila, vastaanottotila)
      val julkaistavissa = jono.isJulkaistavissa
      val pisteet: Option[BigDecimal] = Option(jono.getPisteet).map((p: java.math.BigDecimal) => new BigDecimal(p))

      HakutoiveenSijoitteluntulos(
        hakutoive.getHakukohdeOid,
        hakutoive.getTarjoajaOid,
        jono.getValintatapajonoOid,
        valintatila,
        vastaanottotila,
        vastaanottoDeadline.map(_.toDate),
        Ilmoittautumistila.withName(Option(jono.getIlmoittautumisTila).getOrElse(IlmoittautumisTila.EI_TEHTY).name()),
        vastaanotettavuustila,
        viimeisinHakemuksenTilanMuutos,
        viimeisinValintatuloksenMuutos,
        Option(jono.getJonosija).map(_.toInt),
        Option(jono.getVarasijojaKaytetaanAlkaen),
        Option(jono.getVarasijojaTaytetaanAsti),
        Option(jono.getVarasijanNumero).map(_.toInt),
        julkaistavissa,
        jono.getTilanKuvaukset.toMap,
        pisteet
      )
    }

    HakemuksenSijoitteluntulos(hakija.getHakemusOid, Option(StringUtils.trimToNull(hakija.getHakijaOid)), hakutoiveidenYhteenvedot)
  }

  private def hakemuksenKevytYhteenveto(hakija: KevytHakijaDTO, aikataulu: Option[Vastaanottoaikataulu], vastaanottoRecord: Set[VastaanottoRecord]): HakemuksenSijoitteluntulos = {
    val hakutoiveidenYhteenvedot = hakija.getHakutoiveet.toList.map { hakutoive: KevytHakutoiveDTO =>
      val vastaanotto = vastaanottoRecord.find(v => v.hakukohdeOid == hakutoive.getHakukohdeOid).map(_.action)
      val jono: KevytHakutoiveenValintatapajonoDTO = JonoFinder.merkitseväJono(hakutoive).get
      var valintatila: Valintatila = jononValintatila(jono, hakutoive)
      val viimeisinHakemuksenTilanMuutos: Option[Date] = Option(jono.getHakemuksenTilanViimeisinMuutos)
      val viimeisinValintatuloksenMuutos: Option[Date] = Option(jono.getValintatuloksenViimeisinMuutos)
      val ( vastaanottotila, vastaanottoDeadline ) = laskeVastaanottotila(valintatila, vastaanotto, aikataulu, viimeisinHakemuksenTilanMuutos)
      valintatila = vastaanottotilanVaikutusValintatilaan(valintatila, vastaanottotila)
      val vastaanotettavuustila: Vastaanotettavuustila.Value = laskeVastaanotettavuustila(valintatila, vastaanottotila)
      val julkaistavissa = jono.isJulkaistavissa
      val pisteet: Option[BigDecimal] = Option(jono.getPisteet).map((p: java.math.BigDecimal) => new BigDecimal(p))

      HakutoiveenSijoitteluntulos(
        hakutoive.getHakukohdeOid,
        hakutoive.getTarjoajaOid,
        jono.getValintatapajonoOid,
        valintatila,
        vastaanottotila,
        vastaanottoDeadline.map(_.toDate),
        Ilmoittautumistila.withName(Option(jono.getIlmoittautumisTila).getOrElse(IlmoittautumisTila.EI_TEHTY).name()),
        vastaanotettavuustila,
        viimeisinHakemuksenTilanMuutos,
        viimeisinValintatuloksenMuutos,
        Option(jono.getJonosija).map(_.toInt),
        Option(jono.getVarasijojaKaytetaanAlkaen),
        Option(jono.getVarasijojaTaytetaanAsti),
        Option(jono.getVarasijanNumero).map(_.toInt),
        julkaistavissa,
        jono.getTilanKuvaukset.toMap,
        pisteet
      )
    }

    HakemuksenSijoitteluntulos(hakija.getHakemusOid, Option(StringUtils.trimToNull(hakija.getHakijaOid)), hakutoiveidenYhteenvedot)
  }

  private def laskeVastaanotettavuustila(valintatila: Valintatila, vastaanottotila: Vastaanottotila): Vastaanotettavuustila.Value = {
    if (Valintatila.isHyväksytty(valintatila) && vastaanottotila == Vastaanottotila.kesken) {
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

  private def laskeVastaanottotila(valintatila: Valintatila, vastaanotto: Option[VastaanottoAction], aikataulu: Option[Vastaanottoaikataulu], viimeisinHakemuksenTilanMuutos: Option[Date]): ( Vastaanottotila, Option[DateTime] ) = {
    val tilaVainActioninPerusteella: Vastaanottotila = vastaanottotilaVainViimeisimmanVastaanottoActioninPerusteella(vastaanotto)

    tilaVainActioninPerusteella match {
      case Vastaanottotila.kesken if ( Valintatila.isHyväksytty(valintatila) || valintatila == Valintatila.perunut ) =>
        laskeVastaanottoDeadline(aikataulu, viimeisinHakemuksenTilanMuutos, tilaVainActioninPerusteella)
      case tila if Valintatila.isHyväksytty(valintatila) => (tila, laskeVastaanottoDeadline(aikataulu, viimeisinHakemuksenTilanMuutos))
      case tila => (tila, None)
    }
  }

  private def laskeVastaanottoDeadline(aikataulu: Option[Vastaanottoaikataulu], viimeisinHakemuksenTilanMuutos: Option[Date], tilaVainActioninPerusteella: Vastaanottotila): (Vastaanottotila, Option[DateTime]) = {
    laskeVastaanottoDeadline(aikataulu, viimeisinHakemuksenTilanMuutos) match {
      case Some(deadline) if new DateTime().isAfter(deadline) => (Vastaanottotila.ei_vastaanotettu_määräaikana, Some(deadline))
      case deadline => (tilaVainActioninPerusteella, deadline)
    }
  }

  def haeMyohastymisTieto(hakuOid: String, hakukohdeOid: String, hakemusOids: Set[String]): Set[(String, Boolean)] = {
    val aikataulu = ohjausparametritService.ohjausparametrit(hakuOid).flatMap(_.vastaanottoaikataulu)
    // TODO implement
    Set()
  }

  private def laskeVastaanottoDeadline(aikataulu: Option[Vastaanottoaikataulu], viimeisinHakemuksenTilanMuutos: Option[Date]): Option[DateTime] = {
    (aikataulu) match {
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

  def fromOptional[T](opt: Optional[T]) = {
    if (opt.isPresent) {
      Some(opt.get)
    } else {
      None
    }
  }
}
