package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.sql.JDBCType
import java.time.{Instant, OffsetDateTime, ZoneId}
import java.util.UUID

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{VastaanottoAction, VastaanottoRecord}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import slick.jdbc.{GetResult, PositionedParameters, SetParameter}

trait ValintarekisteriResultExtractors {

  protected implicit val getVastaanottoResult = GetResult(r => VastaanottoRecord(
    henkiloOid = r.nextString,
    hakuOid = r.nextString,
    hakukohdeOid = r.nextString,
    action = VastaanottoAction(r.nextString),
    ilmoittaja = r.nextString,
    timestamp = r.nextTimestamp))

  protected implicit val getHakukohdeResult = GetResult(r => HakukohdeRecord(
    oid = r.nextString,
    hakuOid = r.nextString,
    yhdenPaikanSaantoVoimassa = r.nextBoolean,
    kktutkintoonJohtava = r.nextBoolean,
    koulutuksenAlkamiskausi = Kausi(r.nextString)))

  protected implicit val getHakijaResult = GetResult(r => HakijaRecord(
    etunimi = r.nextString,
    sukunimi = r.nextString,
    hakemusOid = r.nextString,
    hakijaOid = r.nextString))

  protected implicit val getHakutoiveResult = GetResult(r => HakutoiveRecord(
    hakemusOid = r.nextString,
    hakutoive = r.nextInt,
    hakukohdeOid = r.nextString,
    valintatuloksenTila = r.nextString,
    kaikkiJonotsijoiteltu = r.nextBoolean))

  protected implicit val getHakutoiveenValintatapajonoResult = GetResult(r => HakutoiveenValintatapajonoRecord(
    hakukohdeOid = r.nextString,
    valintatapajonoPrioriteetti = r.nextInt,
    valintatapajonoOid = r.nextString,
    valintatapajonoNimi = r.nextString,
    eiVarasijatayttoa = r.nextBoolean,
    jonosija = r.nextInt,
    varasijanNumero = r.nextIntOption,
    tila = Valinnantila(r.nextString),
    ilmoittautumisTila = r.nextStringOption.map(SijoitteluajonIlmoittautumistila(_)).getOrElse(EiTehty),
    hyvaksyttyHarkinnanvaraisesti = r.nextBoolean,
    tasasijaJonosija = r.nextInt,
    pisteet = r.nextBigDecimalOption,
    alinHyvaksyttyPistemaara = r.nextBigDecimalOption,
    hyvaksytty = r.nextInt,
    varalla = r.nextInt,
    varasijat = r.nextIntOption,
    varasijaTayttoPaivat = r.nextIntOption,
    varasijojaKaytetaanAlkaen = r.nextTimestampOption,
    varasijojaTaytetaanAsti = r.nextTimestampOption,
    tayttojono = r.nextStringOption,
    julkaistavissa = r.nextBoolean,
    ehdollisestiHyvaksyttavissa = r.nextBoolean,
    hyvaksyttyVarasijalta = r.nextBoolean,
    valintatuloksenViimeisinMuutos = r.nextTimestampOption,
    hakemuksenTilanViimeisinMuutos = r.nextTimestamp,
    tilankuvausHash = r.nextInt,
    tarkenteenLisatieto = r.nextStringOption,
    hakeneet = r.nextInt
  ))

  protected implicit val getHakutoiveenHakijaryhmaResult = GetResult(r => HakutoiveenHakijaryhmaRecord(
    oid = r.nextString,
    nimi = r.nextString,
    hakukohdeOid = r.nextString,
    valintatapajonoOid = r.nextStringOption,
    kiintio = r.nextInt,
    hyvaksyttyHakijaryhmasta = r.nextBoolean,
    hakijaryhmaTyyppikoodiUri = r.nextStringOption
  ))

  protected implicit val getPistetiedotResult = GetResult(r => PistetietoRecord(
    valintatapajonoOid = r.nextString,
    hakemusOid = r.nextString,
    tunniste = r.nextString,
    arvo = r.nextString,
    laskennallinenArvo = r.nextString,
    osallistuminen = r.nextString))

  protected implicit val getSijoitteluajoResult = GetResult(r => SijoitteluajoRecord(
    sijoitteluajoId = r.nextLong,
    hakuOid = r.nextString,
    startMils = r.nextTimestamp.getTime,
    endMils = r.nextTimestamp.getTime))

  protected implicit val getSijoitteluajoHakukohteetResult = GetResult(r => SijoittelunHakukohdeRecord(
    sijoitteluajoId = r.nextLong,
    oid = r.nextString,
    kaikkiJonotsijoiteltu = r.nextBoolean))

  protected implicit val getValintatapajonotResult = GetResult(r => ValintatapajonoRecord(
    tasasijasaanto = r.nextString,
    oid = r.nextString,
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
    hyvaksytty = r.nextInt,
    varalla = r.nextInt , varasijat = r.nextIntOption,
    varasijanTayttoPaivat = r.nextIntOption,
    varasijojaKaytetaanAlkaen = r.nextDateOption,
    varasijojaKaytetaanAsti = r.nextDateOption,
    tayttoJono = r.nextStringOption,
    hakukohdeOid = r.nextString))

  protected implicit val getHakemuksetForValintatapajonosResult = GetResult(r => HakemusRecord(
    hakijaOid = r.nextStringOption,
    hakemusOid = r.nextString,
    pisteet = r.nextBigDecimalOption,
    etunimi = r.nextStringOption,
    sukunimi = r.nextStringOption,
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
    valintatapajonoOid = r.nextString))

  protected implicit val getHakemuksenTilahistoriaResult = GetResult(r => TilaHistoriaRecord(
    valintatapajonoOid = r.nextString,
    hakemusOid = r.nextString,
    tila = Valinnantila(r.nextString),
    luotu = r.nextTimestamp))

  protected implicit val getHakijaryhmatResult = GetResult(r => HakijaryhmaRecord(
    prioriteetti = r.nextInt,
    oid = r.nextString,
    nimi = r.nextString,
    hakukohdeOid = r.nextStringOption,
    kiintio = r.nextInt,
    kaytaKaikki = r.nextBoolean,
    sijoitteluajoId = r.nextLong,
    tarkkaKiintio = r.nextBoolean,
    kaytetaanRyhmaanKuuluvia = r.nextBoolean,
    valintatapajonoOid = r.nextStringOption,
    hakijaryhmatyyppikoodiUri = r.nextString))

  protected implicit val getTilankuvauksetResult = GetResult(r => TilankuvausRecord(
    hash = r.nextInt,
    tilankuvauksenTarkenne = ValinnantilanTarkenne(r.nextString),
    textFi = r.nextStringOption,
    textSv = r.nextStringOption,
    textEn = r.nextStringOption
  ))

  protected implicit val getValinnantulosResult: GetResult[Valinnantulos] = GetResult(r => Valinnantulos(
    hakukohdeOid = r.nextString,
    valintatapajonoOid = r.nextString,
    hakemusOid = r.nextString,
    henkiloOid = r.nextString,
    valinnantila = Valinnantila(r.nextString),
    ehdollisestiHyvaksyttavissa = r.nextBooleanOption,
    julkaistavissa = r.nextBooleanOption,
    hyvaksyttyVarasijalta = r.nextBooleanOption,
    hyvaksyPeruuntunut = r.nextBooleanOption,
    vastaanottotila = r.nextStringOption.map(VastaanottoAction(_).valintatuloksenTila).getOrElse(ValintatuloksenTila.KESKEN),
    ilmoittautumistila = r.nextStringOption.map(SijoitteluajonIlmoittautumistila(_)).getOrElse(EiTehty)
  ))

  protected implicit val getInstantOptionResult: GetResult[Option[Instant]] = GetResult(r => r.nextTimestampOption().map(_.toInstant))

  protected implicit val getInstantResult: GetResult[Instant] = GetResult(r => r.nextTimestamp().toInstant)

  protected implicit val getValinnantila: GetResult[Valinnantila] = GetResult(r => Valinnantila(r.nextString))

  protected implicit val getValintatuloksenTila: GetResult[ValintatuloksenTila] = GetResult(r => VastaanottoAction(r.nextString).valintatuloksenTila)

  protected implicit val getSijoitteluajonIlmoittautumistila: GetResult[SijoitteluajonIlmoittautumistila] = GetResult(r => SijoitteluajonIlmoittautumistila(r.nextString))

  protected implicit val getOffsetDateTime: GetResult[OffsetDateTime] = GetResult(r => {
    val d = r.rs.getObject(r.currentPos + 1, classOf[OffsetDateTime])
    r.skip
    d
  })

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
}
