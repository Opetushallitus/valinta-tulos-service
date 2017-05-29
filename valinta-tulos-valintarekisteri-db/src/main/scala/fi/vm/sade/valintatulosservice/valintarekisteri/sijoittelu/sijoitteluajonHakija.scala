package fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu

import fi.vm.sade.sijoittelu.domain.{SijoitteluAjo, ValintatuloksenTila}
import fi.vm.sade.sijoittelu.tulos.dto.raportointi._
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaRepository, SijoitteluRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

class SijoitteluajonHakija(val repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository,
                           val sijoitteluajoId:Option[Long],
                           val hakuOid:HakuOid,
                           val hakemusOid:HakemusOid) {

  def this(repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository, sijoitteluajoId: String, hakuOid: HakuOid, hakemusOid: HakemusOid) {
    this(repository, Some(repository.getLatestSijoitteluajoIdThrowFailure(sijoitteluajoId, hakuOid)), hakuOid, hakemusOid)
  }

  def this(repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository, sijoitteluajo: SijoitteluAjo, hakemusOid: HakemusOid) {
    this(repository, SyntheticSijoitteluAjoForHakusWithoutSijoittelu.getSijoitteluajoId(sijoitteluajo), HakuOid(sijoitteluajo.getHakuOid), hakemusOid)
  }

  val hakija = repository.getHakemuksenHakija(hakemusOid, sijoitteluajoId)
    .orElse(throw new NotFoundException(s"Hakijaa ei löytynyt hakemukselle $hakemusOid, sijoitteluajoid: $sijoitteluajoId")).get

  lazy val haunValinnantilat = repository.getHaunValinnantilat(hakuOid) //TODO performance? Do we need koko haku?

  lazy val hakemuksenValinnantulokset: Map[HakukohdeOid, Set[Valinnantulos]] = repository.runBlocking(repository.getValinnantuloksetForHakemus(hakemusOid)).groupBy(_.hakukohdeOid) //.map(v => (v.hakukohdeOid, v.valintatapajonoOid) -> v).toMap

  lazy val hakutoiveetSijoittelussa = sijoitteluajoId.map(repository.getHakemuksenHakutoiveetSijoittelussa(hakemusOid, _).map(h => h.hakukohdeOid -> h).toMap).getOrElse(Map())
  lazy val valintatapajonotSijoittelussa = sijoitteluajoId.map(repository.getHakemuksenHakutoiveidenValintatapajonotSijoittelussa(hakemusOid, _).groupBy(_.hakukohdeOid)).getOrElse(Map())
  lazy val pistetiedotSijoittelussa = sijoitteluajoId.map(repository.getHakemuksenPistetiedotSijoittelussa(hakemusOid, _).groupBy(_.valintatapajonoOid)).getOrElse(Map())
  lazy val hakijaryhmatSijoittelussa = sijoitteluajoId.map(repository.getHakemuksenHakutoiveidenHakijaryhmatSijoittelussa(hakemusOid, _).groupBy(_.hakukohdeOid)).getOrElse(Map())

  lazy val tilankuvauksetSijoittelussa = repository.getValinnantilanKuvaukset(
    valintatapajonotSijoittelussa.values.flatten.map(_.tilankuvausHash).toList.distinct
  )

  def hyvaksyttyValintatapajonosta(hakukohdeOid: HakukohdeOid, valintatapajonoOid: ValintatapajonoOid) = {
    haunValinnantilat.filter{ case (hakukohde, valintatapajono, hakemus, tila) =>
      hakukohde.equals(hakukohdeOid) && valintatapajono.equals(valintatapajonoOid) && List(Hyvaksytty, VarasijaltaHyvaksytty).contains(tila)
    }.map(_._3).distinct.size
  }

  def hakeneetValintatapajonossa(hakukohdeOid: HakukohdeOid, valintatapajonoOid: ValintatapajonoOid) = {
    haunValinnantilat.filter{ case (hakukohde, valintatapajono, hakemus, tila) =>
      hakukohde.equals(hakukohdeOid) && valintatapajono.equals(valintatapajonoOid)
    }.map(_._3).distinct.size
  }

  def getVastaanotto(hakukohdeOid: HakukohdeOid): ValintatuloksenTila = {
    val vastaanotto = hakemuksenValinnantulokset.getOrElse(hakukohdeOid, Set()).map(_.vastaanottotila)
    if(1 < vastaanotto.size) {
      throw new RuntimeException(s"Hakemukselle ${hakemusOid} löytyy monta vastaanottoa hakukohteelle ${hakukohdeOid}")
    } else {
      vastaanotto.headOption.getOrElse(ValintatuloksenTila.KESKEN)
    }
  }

  def hakukohdeDtoSijoittelu(hakukohdeOid: HakukohdeOid) = {
    val hakutoive = hakutoiveetSijoittelussa(hakukohdeOid)
    val valintatapajonot = valintatapajonotSijoittelussa.getOrElse(hakukohdeOid, List())
    val valintatapajonoOidit = valintatapajonot.map(_.valintatapajonoOid)
    val valinnantulokset = hakemuksenValinnantulokset.getOrElse(hakukohdeOid, List())
    val pistetiedot = pistetiedotSijoittelussa.filterKeys(valintatapajonoOidit.contains).values.flatten.map(HakutoiveenPistetietoRecord(_)).toList.distinct.map(_.dto)
    val hakijaryhmat = hakijaryhmatSijoittelussa.getOrElse(hakukohdeOid, List()).map(_.dto)
    val valintatapajonoDtot = valintatapajonot.map{ j =>
      j.dto(
        valinnantulokset.find(_.valintatapajonoOid.equals(j.valintatapajonoOid)),
        hyvaksyttyValintatapajonosta(hakukohdeOid, j.valintatapajonoOid),
        j.tilankuvaukset(tilankuvauksetSijoittelussa.get(j.tilankuvausHash)))
    }

    hakutoive.dto(
      getVastaanotto(hakukohdeOid),
      valintatapajonoDtot,
      pistetiedot,
      hakijaryhmat
    )
  }

  def hakukohdeDtoEiSijoittelua(hakukohdeOid: HakukohdeOid) = {
    val valinnantulokset = hakemuksenValinnantulokset.getOrElse(hakukohdeOid, List())
    val hakutoive = HakutoiveRecord(hakemusOid, Some(1), hakukohdeOid, None)
    val valintatapajonoDtot = valinnantulokset.map{ j =>
      HakutoiveenValintatapajonoRecord.dto(
        j,
        hakeneetValintatapajonossa(hakukohdeOid, j.valintatapajonoOid),
        hyvaksyttyValintatapajonosta(hakukohdeOid, j.valintatapajonoOid)
      )
    }.toList
    hakutoive.dto(getVastaanotto(hakukohdeOid), valintatapajonoDtot, List(), List())
  }

  def dto(): HakijaDTO = {
    val hakukohdeOidit = hakemuksenValinnantulokset.keySet.union(hakutoiveetSijoittelussa.keySet)
    hakija.dto(hakukohdeOidit.map { hakukohdeOid =>
      if (hakutoiveetSijoittelussa.contains(hakukohdeOid)) {
        hakukohdeDtoSijoittelu(hakukohdeOid)
      } else {
        hakukohdeDtoEiSijoittelua(hakukohdeOid)
      }
    }.toList)
  }
}

class SijoitteluajonHakijat(val repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository,
                            val sijoitteluajoId:Option[Long],
                            val hakuOid:HakuOid,
                            val hakukohdeOid: HakukohdeOid) {

  def this(repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository, sijoitteluajoId: String, hakuOid: HakuOid, hakukohdeOid: HakukohdeOid) {
    this(repository, Some(repository.getLatestSijoitteluajoIdThrowFailure(sijoitteluajoId, hakuOid)), hakuOid, hakukohdeOid)
  }

  def this(repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository, sijoitteluajo: SijoitteluAjo, hakukohdeOid: HakukohdeOid) {
    this(repository, SyntheticSijoitteluAjoForHakusWithoutSijoittelu.getSijoitteluajoId(sijoitteluajo), HakuOid(sijoitteluajo.getHakuOid), hakukohdeOid)
  }

  val hakijat = repository.getHakukohteenHakijat(hakukohdeOid, sijoitteluajoId)

  lazy val hakutoiveetSijoittelussa = sijoitteluajoId.map(repository.getHakukohteenHakemuksienHakutoiveetSijoittelussa(hakukohdeOid, _).groupBy(_.hakemusOid)).getOrElse(Map())
  lazy val (valintatapajonotSijoittelussa, tilankuvausHashit) = {
    val valintatapajonot = sijoitteluajoId.map(repository.getHakukohteenHakemuksienValintatapajonotSijoittelussa(hakukohdeOid, _)).getOrElse(List())
    (valintatapajonot.groupBy(_.hakemusOid).mapValues(_.groupBy(_.hakukohdeOid)), valintatapajonot.map(_.tilankuvausHash).distinct)
  }
  lazy val tilankuvauksetSijoittelussa = repository.getValinnantilanKuvaukset(tilankuvausHashit)
  lazy val haunValinnantulokset: Map[HakukohdeOid, Map[HakemusOid, Set[Valinnantulos]]] = repository.runBlocking(repository.getValinnantuloksetForHaku(hakuOid)).groupBy(_.hakukohdeOid).mapValues(_.groupBy(_.hakemusOid))

  lazy val hakutoiveSijoittelussa = sijoitteluajoId.map(repository.getHakukohteenHakemuksienHakutoiveSijoittelussa(hakukohdeOid, _).groupBy(_.hakemusOid)).getOrElse(Map())
  lazy val (hakutoiveenValintatapajonotSijoittelussa, hakutoiveenTilankuvausHashit) = {
    val valintatapajonot = sijoitteluajoId.map(repository.getHakukohteenHakemuksienHakutoiveenValintatapajonotSijoittelussa(hakukohdeOid, _)).getOrElse(List())
    (valintatapajonot.groupBy(_.hakemusOid).mapValues(_.groupBy(_.hakukohdeOid)), valintatapajonot.map(_.tilankuvausHash).distinct)
  }
  lazy val hakutoiveenTilankuvauksetSijoittelussa = repository.getValinnantilanKuvaukset(hakutoiveenTilankuvausHashit)
  lazy val hakukohteenValinnantulokset: Map[HakemusOid, Set[Valinnantulos]] = repository.runBlocking(repository.getValinnantuloksetForHakukohde(hakukohdeOid)).groupBy(_.hakemusOid)

  def hakukohdeDtotSijoittelu(hakemusOid: HakemusOid): List[KevytHakutoiveDTO] = {
    hakutoiveetSijoittelussa.getOrElse(hakemusOid, List()).map(hakukohde => hakukohde.kevytDto(
        valintatapajonotSijoittelussa.getOrElse(hakemusOid, Map()).getOrElse(hakukohde.hakukohdeOid, List()).map(jono => jono.kevytDto(
          haunValinnantulokset.get(hakukohde.hakukohdeOid).flatMap(_.get(hakemusOid)).flatMap(_.find(_.valintatapajonoOid.equals(jono.valintatapajonoOid))),
          jono.tilankuvaukset(tilankuvauksetSijoittelussa.get(jono.tilankuvausHash))))
      )
    )
  }

  def hakukohdeDtotEiSijoittelua(hakemusOid: HakemusOid, hakukohdeOids: Set[HakukohdeOid]): List[KevytHakutoiveDTO] = {
    hakukohdeOids.map(hakukohdeOid => {
      HakutoiveRecord(hakemusOid, Some(1), hakukohdeOid, None).kevytDto(
        haunValinnantulokset.getOrElse(hakukohdeOid, Map()).getOrElse(hakemusOid, Set()).map(valinnantulos => HakutoiveenValintatapajonoRecord.kevytDto(valinnantulos)).toList
      )}
    ).toList
  }

  def kevytDto: List[KevytHakijaDTO] = {
    hakijat.map(hakija => {
      val hakijanHakutoiveetSijoittelussa = hakutoiveetSijoittelussa.get(hakija.hakemusOid).toSet.flatten.map(_.hakukohdeOid)
      val hakijanHakutoiveetValinnantuloksissa = haunValinnantulokset.filter(_._2.keySet.contains(hakija.hakemusOid)).keySet
      val hakijanHakutoiveetEiSijoittelua = hakijanHakutoiveetValinnantuloksissa.filterNot(hakijanHakutoiveetSijoittelussa)

      hakija.kevytDto(
        hakukohdeDtotSijoittelu(hakija.hakemusOid).union(hakukohdeDtotEiSijoittelua(hakija.hakemusOid, hakijanHakutoiveetEiSijoittelua))
      )
    })
  }

  def hakutoiveDtoSijoittelu(hakemusOid:HakemusOid): Option[KevytHakutoiveDTO] = {
    hakutoiveSijoittelussa.getOrElse(hakemusOid, List()).headOption.map(
      _.kevytDto(hakutoiveenValintatapajonotSijoittelussa.getOrElse(hakemusOid, Map()).getOrElse(hakukohdeOid, List()).map(jono => jono.kevytDto(
        hakukohteenValinnantulokset.getOrElse(hakemusOid, Set()).find(_.valintatapajonoOid.equals(jono.valintatapajonoOid)),
        jono.tilankuvaukset(hakutoiveenTilankuvauksetSijoittelussa.get(jono.tilankuvausHash))
      )))
    )
  }

  def hakutoiveDtoEiSijoittelua(hakemusOid:HakemusOid): Option[KevytHakutoiveDTO] = {
    hakukohteenValinnantulokset.get(hakemusOid).map(valintatulokset =>
      HakutoiveRecord(hakemusOid, Some(1), hakukohdeOid, None).kevytDto(
        valintatulokset.map(valinnantulos => HakutoiveenValintatapajonoRecord.kevytDto(valinnantulos)).toList
      )
    )
  }

  def kevytDtoVainHakukohde:List[KevytHakijaDTO] = {
    hakijat.map(hakija => {
      hakija.kevytDto(
        hakutoiveDtoSijoittelu(hakija.hakemusOid).orElse(hakutoiveDtoEiSijoittelua(hakija.hakemusOid)).toList
      )
    })
  }
}