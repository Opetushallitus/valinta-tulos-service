package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.sql.JDBCType
import java.time.{Instant, OffsetDateTime, ZoneId, ZoneOffset, ZonedDateTime}
import java.util.UUID

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{VastaanottoAction, VastaanottoRecord}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import slick.jdbc.{GetResult, PositionedParameters, PositionedResult, SetParameter}

trait ValintarekisteriResultExtractors {

  protected implicit val getVastaanottoResult = GetResult(r => VastaanottoRecord(
    henkiloOid = r.nextString,
    hakuOid = HakuOid(r.nextString),
    hakukohdeOid = HakukohdeOid(r.nextString),
    action = VastaanottoAction(r.nextString),
    ilmoittaja = r.nextString,
    timestamp = r.nextTimestamp))

  protected implicit val getHakukohdeResult = GetResult(r => HakukohdeRecord(
    oid = HakukohdeOid(r.nextString),
    hakuOid = HakuOid(r.nextString),
    yhdenPaikanSaantoVoimassa = r.nextBoolean,
    kktutkintoonJohtava = r.nextBoolean,
    koulutuksenAlkamiskausi = Kausi(r.nextString)))

  protected implicit val getHakijaResult = GetResult(r => HakijaRecord(
    hakemusOid = HakemusOid(r.nextString),
    hakijaOid = r.nextString))

  protected implicit val getHakutoiveResult = GetResult(r => HakutoiveRecord(
    hakemusOid = HakemusOid(r.nextString),
    hakutoive = r.nextIntOption,
    hakukohdeOid = HakukohdeOid(r.nextString),
    kaikkiJonotsijoiteltu = r.nextBooleanOption))

  protected implicit val getHakutoiveenValintatapajonoResult = GetResult(r => HakutoiveenValintatapajonoRecord(
    hakemusOid = HakemusOid(r.nextString),
    hakukohdeOid = HakukohdeOid(r.nextString),
    valintatapajonoPrioriteetti = r.nextInt,
    valintatapajonoOid = ValintatapajonoOid(r.nextString),
    valintatapajonoNimi = r.nextString,
    eiVarasijatayttoa = r.nextBoolean,
    jonosija = r.nextInt,
    varasijanNumero = r.nextIntOption,
    hyvaksyttyHarkinnanvaraisesti = r.nextBoolean,
    tasasijaJonosija = r.nextInt,
    pisteet = r.nextBigDecimalOption,
    alinHyvaksyttyPistemaara = r.nextBigDecimalOption,
    varasijat = r.nextIntOption,
    varasijaTayttoPaivat = r.nextIntOption,
    varasijojaKaytetaanAlkaen = r.nextTimestampOption,
    varasijojaTaytetaanAsti = r.nextTimestampOption,
    tayttojono = r.nextStringOption,
    tilankuvausHash = r.nextInt,
    tarkenteenLisatieto = r.nextStringOption
  ))

  protected implicit val getHakutoiveenHakijaryhmaResult = GetResult(r => HakutoiveenHakijaryhmaRecord(
    oid = r.nextString,
    nimi = r.nextString,
    hakukohdeOid = HakukohdeOid(r.nextString),
    valintatapajonoOid = r.nextStringOption.map(ValintatapajonoOid),
    kiintio = r.nextInt,
    hyvaksyttyHakijaryhmasta = r.nextBoolean,
    hakijaryhmaTyyppikoodiUri = r.nextStringOption
  ))

  protected implicit val getPistetiedotResult = GetResult(r => PistetietoRecord(
    valintatapajonoOid = ValintatapajonoOid(r.nextString),
    hakemusOid = HakemusOid(r.nextString),
    tunniste = r.nextString,
    arvo = r.nextString,
    laskennallinenArvo = r.nextString,
    osallistuminen = r.nextString))

  protected implicit val getSijoitteluajoResult = GetResult(r => SijoitteluajoRecord(
    sijoitteluajoId = r.nextLong,
    hakuOid = HakuOid(r.nextString),
    startMils = r.nextTimestamp.getTime,
    endMils = r.nextTimestamp.getTime))

  protected implicit val getSijoitteluajoHakukohteetResult = GetResult(r => SijoittelunHakukohdeRecord(
    sijoitteluajoId = r.nextLong,
    oid = HakukohdeOid(r.nextString),
    kaikkiJonotsijoiteltu = r.nextBoolean))

  protected implicit val getValintatapajonotResult = GetResult(r => ValintatapajonoRecord(
    tasasijasaanto = r.nextString,
    oid = ValintatapajonoOid(r.nextString),
    nimi = r.nextString,
    prioriteetti = r.nextInt,
    aloituspaikat = r.nextIntOption,
    alkuperaisetAloituspaikat = r.nextIntOption,
    alinHyvaksyttyPistemaara = r.nextBigDecimal,
    eiVarasijatayttoa = r.nextBoolean,
    kaikkiEhdonTayttavatHyvaksytaan = r.nextBoolean,
    poissaOlevaTaytto = r.nextBoolean,
    valintaesitysHyvaksytty = r.nextBooleanOption,
    hakeneet = 0,
    varasijat = r.nextIntOption,
    varasijanTayttoPaivat = r.nextIntOption,
    varasijojaKaytetaanAlkaen = r.nextTimestampOption,
    varasijojaKaytetaanAsti = r.nextTimestampOption,
    tayttoJono = r.nextStringOption,
    hakukohdeOid = HakukohdeOid(r.nextString)))

  protected implicit val getHakemuksetForValintatapajonosResult = GetResult(r => HakemusRecord(
    hakijaOid = r.nextStringOption,
    hakemusOid = HakemusOid(r.nextString),
    pisteet = r.nextBigDecimalOption,
    prioriteetti = r.nextInt,
    jonosija = r.nextInt,
    tasasijaJonosija = r.nextInt,
    tila = Valinnantila(r.nextString),
    tilankuvausHash = r.nextInt,
    tarkenteenLisatieto = r.nextStringOption,
    hyvaksyttyHarkinnanvaraisesti = r.nextBoolean,
    varasijaNumero = r.nextIntOption,
    onkoMuuttunutviimesijoittelusta = r.nextBoolean,
    siirtynytToisestaValintatapaJonosta = r.nextBoolean,
    valintatapajonoOid = ValintatapajonoOid(r.nextString)))

  protected implicit val getHakemuksenTilahistoriaResult = GetResult(r => TilaHistoriaRecord(
    valintatapajonoOid = ValintatapajonoOid(r.nextString),
    hakemusOid = HakemusOid(r.nextString),
    tila = Valinnantila(r.nextString),
    luotu = r.nextTimestamp))

  protected implicit val getHakijaryhmatResult = GetResult(r => HakijaryhmaRecord(
    prioriteetti = r.nextInt,
    oid = r.nextString,
    nimi = r.nextString,
    hakukohdeOid = r.nextStringOption.map(HakukohdeOid),
    kiintio = r.nextInt,
    kaytaKaikki = r.nextBoolean,
    sijoitteluajoId = r.nextLong,
    tarkkaKiintio = r.nextBoolean,
    kaytetaanRyhmaanKuuluvia = r.nextBoolean,
    valintatapajonoOid = r.nextStringOption.map(ValintatapajonoOid),
    hakijaryhmatyyppikoodiUri = r.nextString))

  protected implicit val getTilankuvauksetResult = GetResult(r => TilankuvausRecord(
    hash = r.nextInt,
    tilankuvauksenTarkenne = ValinnantilanTarkenne(r.nextString),
    textFi = r.nextStringOption,
    textSv = r.nextStringOption,
    textEn = r.nextStringOption
  ))

  protected implicit val getValinnantulosResult: GetResult[Valinnantulos] = GetResult(r => Valinnantulos(
    hakukohdeOid = HakukohdeOid(r.nextString),
    valintatapajonoOid = ValintatapajonoOid(r.nextString),
    hakemusOid = HakemusOid(r.nextString),
    henkiloOid = r.nextString,
    valinnantila = Valinnantila(r.nextString),
    ehdollisestiHyvaksyttavissa = r.nextBooleanOption,
    ehdollisenHyvaksymisenEhtoKoodi = r.nextStringOption(),
    ehdollisenHyvaksymisenEhtoFI = r.nextStringOption(),
    ehdollisenHyvaksymisenEhtoSV = r.nextStringOption(),
    ehdollisenHyvaksymisenEhtoEN = r.nextStringOption(),
    julkaistavissa = r.nextBooleanOption,
    hyvaksyttyVarasijalta = r.nextBooleanOption,
    hyvaksyPeruuntunut = r.nextBooleanOption,
    vastaanottotila = r.nextStringOption.map(VastaanottoAction(_).valintatuloksenTila).getOrElse(ValintatuloksenTila.KESKEN),
    ilmoittautumistila = r.nextStringOption.map(SijoitteluajonIlmoittautumistila(_)).getOrElse(EiTehty),
    valinnantilanViimeisinMuutos = parseOffsetDateTime(r),
    vastaanotonViimeisinMuutos = parseOffsetDateTime(r)
  ))

  protected implicit val getInstantOptionResult: GetResult[Option[Instant]] = GetResult(r => r.nextTimestampOption().map(_.toInstant))

  protected implicit val getInstantResult: GetResult[Instant] = GetResult(r => r.nextTimestamp().toInstant)

  protected implicit val getValinnantila: GetResult[Valinnantila] = GetResult(r => Valinnantila(r.nextString))

  protected implicit val getHaunValinnantilat: GetResult[(HakukohdeOid, ValintatapajonoOid, HakemusOid, Valinnantila)] = GetResult(r =>
    (HakukohdeOid(r.nextString), ValintatapajonoOid(r.nextString), HakemusOid(r.nextString), Valinnantila(r.nextString)))

  protected implicit val getValintatuloksenTila: GetResult[ValintatuloksenTila] = GetResult(r => VastaanottoAction(r.nextString).valintatuloksenTila)

  protected implicit val getSijoitteluajonIlmoittautumistila: GetResult[SijoitteluajonIlmoittautumistila] = GetResult(r => SijoitteluajonIlmoittautumistila(r.nextString))

  private def parseOffsetDateTime(r:PositionedResult):Option[OffsetDateTime] = {
    val d = r.rs.getObject(r.currentPos + 1, classOf[OffsetDateTime])
    r.skip
    Option(d).map(d => OffsetDateTime.ofInstant(d.toInstant, ZoneId.of("Europe/Helsinki")))
  }

  protected implicit val getOffsetDateTime: GetResult[OffsetDateTime] = GetResult(r => {
    parseOffsetDateTime(r).get
  })

  protected implicit val getZonedDateTime: GetResult[ZonedDateTime] = GetResult(r => {
    val d = r.rs.getObject(r.currentPos + 1, classOf[OffsetDateTime])
    r.skip
    d.atZoneSameInstant(ZoneId.of("Europe/Helsinki"))
  })

  protected implicit val getZonedDateTimeOption: GetResult[Option[ZonedDateTime]] = GetResult(r => {
    val d = r.rs.getObject(r.currentPos + 1, classOf[OffsetDateTime])
    Option(d).map(_.atZoneSameInstant(ZoneId.of("Europe/Helsinki")))
  })

  implicit val getHakuOid: GetResult[HakuOid] = GetResult(r => {
    HakuOid(r.nextString())
  })

  implicit val getHakukohdeOid: GetResult[HakukohdeOid] = GetResult(r => {
    HakukohdeOid(r.nextString())
  })

  implicit val getValintatapajonoOid: GetResult[ValintatapajonoOid] = GetResult(r => {
    ValintatapajonoOid(r.nextString())
  })

  implicit val getHakemusOid: GetResult[HakemusOid] = GetResult(r => {
    HakemusOid(r.nextString())
  })

  implicit object SetHakuOid extends SetParameter[HakuOid] {
    def apply(o: HakuOid, pp: PositionedParameters) {
      pp.setString(o.toString)
    }
  }

  implicit object SetHakukohdeOid extends SetParameter[HakukohdeOid] {
    def apply(o: HakukohdeOid, pp: PositionedParameters) {
      pp.setString(o.toString)
    }
  }

  implicit object SetOptionHakukohdeOid extends SetParameter[Option[HakukohdeOid]] {
    def apply(o: Option[HakukohdeOid], pp: PositionedParameters): Unit = o match {
      case Some(oo) => SetHakukohdeOid(oo, pp)
      case None => pp.setNull(JDBCType.VARCHAR.getVendorTypeNumber)
    }
  }

  implicit object SetOptionValintatapajonoOid extends SetParameter[Option[ValintatapajonoOid]] {
    def apply(o: Option[ValintatapajonoOid], pp: PositionedParameters): Unit = o match {
      case Some(oo) => SetValintatapajonoOid(oo, pp)
      case None => pp.setNull(JDBCType.VARCHAR.getVendorTypeNumber)
    }
  }

  implicit object SetValintatapajonoOid extends SetParameter[ValintatapajonoOid] {
    def apply(o: ValintatapajonoOid, pp: PositionedParameters) {
      pp.setString(o.toString)
    }
  }

  implicit object SetHakemusOid extends SetParameter[HakemusOid] {
    def apply(o: HakemusOid, pp: PositionedParameters) {
      pp.setString(o.toString)
    }
  }

  implicit object SetUUID extends SetParameter[UUID] {
    def apply(v: UUID, pp: PositionedParameters) {
      pp.setObject(v, JDBCType.BINARY.getVendorTypeNumber)
    }
  }

  implicit object SetInstant extends SetParameter[Instant] {
    def apply(v: Instant, pp: PositionedParameters): Unit = {
      pp.setObject(OffsetDateTime.ofInstant(v, ZoneId.of("Europe/Helsinki")), JDBCType.TIMESTAMP_WITH_TIMEZONE.getVendorTypeNumber)
    }
  }

  implicit object SetOptionInstant extends SetParameter[Option[Instant]] {
    def apply(v: Option[Instant], pp: PositionedParameters): Unit = v match {
      case Some(i) => SetInstant.apply(i, pp)
      case None => pp.setNull(JDBCType.TIMESTAMP_WITH_TIMEZONE.getVendorTypeNumber)
    }
  }

  implicit object SetZonedDateTime extends SetParameter[ZonedDateTime] {
    def apply(v: ZonedDateTime, pp: PositionedParameters): Unit = {
      pp.setObject(v.toOffsetDateTime, JDBCType.TIMESTAMP_WITH_TIMEZONE.getVendorTypeNumber)
    }
  }

  implicit object SetOptionZonedDateTime extends SetParameter[Option[ZonedDateTime]] {
    def apply(v: Option[ZonedDateTime], pp: PositionedParameters): Unit = v match {
      case Some(i) => SetZonedDateTime.apply(i, pp)
      case None => pp.setNull(JDBCType.TIMESTAMP_WITH_TIMEZONE.getVendorTypeNumber)
    }
  }
}
