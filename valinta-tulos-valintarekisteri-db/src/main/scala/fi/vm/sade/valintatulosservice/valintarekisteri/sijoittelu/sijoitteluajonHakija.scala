package fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu

import fi.vm.sade.sijoittelu.domain.{SijoitteluAjo, ValintatuloksenTila}
import fi.vm.sade.sijoittelu.tulos.dto.raportointi._
import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaRepository, SijoitteluRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

object SijoitteluajonHakija {
  def dto(repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository,
          sijoitteluajoId:Option[Long],
          hakuOid:HakuOid,
          hakemusOid:HakemusOid): Option[HakijaDTO] = {
    val hakija = repository.getHakemuksenHakija(hakemusOid, sijoitteluajoId)

    val (hakemuksenValinnantulokset, hakutoiveetSijoittelussa) = hakija match {
      case Some(x) => (
        timed(s"Getting valinnantulokset for hakemus $hakemusOid", 100) {
          repository.runBlocking(repository.getValinnantuloksetForHakemus(hakemusOid)).groupBy(_.hakukohdeOid)
        },
        sijoitteluajoId.map(repository.getHakemuksenHakutoiveetSijoittelussa(hakemusOid, _).map(h => h.hakukohdeOid -> h).toMap).getOrElse(Map()))
      case None => (Map[HakukohdeOid, Set[Valinnantulos]](), Map[HakukohdeOid, HakutoiveRecord]())
    }

    def getVastaanotto(hakukohdeOid: HakukohdeOid): ValintatuloksenTila = {
      val vastaanotto = hakemuksenValinnantulokset.getOrElse(hakukohdeOid, Set()).map(_.vastaanottotila)
      if(1 < vastaanotto.size) {
        throw new RuntimeException(s"Hakemukselle ${hakemusOid} löytyy monta vastaanottoa hakukohteelle ${hakukohdeOid}")
      } else {
        vastaanotto.headOption.getOrElse(ValintatuloksenTila.KESKEN)
      }
    }

    //TODO: Tämä voi olla väärin, jos sijoitteluajoId ei ole latest
    val hakutoiveidenHakeneet = repository.getHakijanHakutoiveidenHakijatValinnantuloksista(hakemusOid)
    //TODO: Ei tarvitse hakea, jos kaikki hakutoiveet sijoittelussa
    val hakutoiveidenHyvaksytyt = repository.getHakijanHakutoiveidenHyvaksytytValinnantuloksista(hakemusOid)
    val hakutoiveidenHakeneetSijoittelussa = sijoitteluajoId.map(repository.getHakijanHakutoiveidenHakijatSijoittelussa(hakemusOid, _)).getOrElse(Map())


    def hakukohdeDtoSijoittelu(hakukohdeOid: HakukohdeOid,
                               valintatapajonotSijoittelussa: Map[HakukohdeOid, List[HakutoiveenValintatapajonoRecord]],
                               pistetiedotSijoittelussa: Map[ValintatapajonoOid, List[PistetietoRecord]],
                               hakijaryhmatSijoittelussa: Map[HakukohdeOid, List[HakutoiveenHakijaryhmaRecord]],
                               tilankuvauksetSijoittelussa: Map[Int, TilankuvausRecord]) = {
      val hakutoive = hakutoiveetSijoittelussa(hakukohdeOid)
      val valintatapajonot = valintatapajonotSijoittelussa.getOrElse(hakukohdeOid, List())
      val valintatapajonoOidit = valintatapajonot.map(_.valintatapajonoOid)
      val valinnantulokset = hakemuksenValinnantulokset.getOrElse(hakukohdeOid, List())
      val pistetiedot = pistetiedotSijoittelussa.filterKeys(valintatapajonoOidit.contains).values.flatten.map(HakutoiveenPistetietoRecord(_)).toList.distinct.map(_.dto)
      val hakijaryhmat = hakijaryhmatSijoittelussa.getOrElse(hakukohdeOid, List()).map(_.dto)
      val valintatapajonoDtot = valintatapajonot.map{ j =>
        j.dto(
          valinnantulokset.find(_.valintatapajonoOid.equals(j.valintatapajonoOid)),
          j.tilankuvaukset(tilankuvauksetSijoittelussa.get(j.tilankuvausHash)),
          hakutoiveidenHakeneetSijoittelussa.getOrElse((hakukohdeOid, j.valintatapajonoOid), 0),
          hakutoiveidenHyvaksytyt.getOrElse((hakukohdeOid, j.valintatapajonoOid), 0)
        )
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
        HakutoiveenValintatapajonoRecord.dto(j,
          hakutoiveidenHakeneet.getOrElse((hakukohdeOid, j.valintatapajonoOid), 0),
          hakutoiveidenHyvaksytyt.getOrElse((hakukohdeOid, j.valintatapajonoOid), 0))
      }.toList
      hakutoive.dto(getVastaanotto(hakukohdeOid), valintatapajonoDtot, List(), List())
    }



    if (hakutoiveetSijoittelussa.isEmpty) {
      hakija.map(_.dto(hakemuksenValinnantulokset.keySet.map(hakukohdeDtoEiSijoittelua).toList))
    } else {
      val valintatapajonotSijoittelussa = sijoitteluajoId.map(repository.getHakemuksenHakutoiveidenValintatapajonotSijoittelussa(hakemusOid, _).groupBy(_.hakukohdeOid)).getOrElse(Map())
      val pistetiedotSijoittelussa = sijoitteluajoId.map(repository.getHakemuksenPistetiedotSijoittelussa(hakemusOid, _).groupBy(_.valintatapajonoOid)).getOrElse(Map())
      val hakijaryhmatSijoittelussa = sijoitteluajoId.map(repository.getHakemuksenHakutoiveidenHakijaryhmatSijoittelussa(hakemusOid, _).groupBy(_.hakukohdeOid)).getOrElse(Map())
      val tilankuvauksetSijoittelussa = repository.getValinnantilanKuvaukset(
        valintatapajonotSijoittelussa.values.flatten.map(_.tilankuvausHash).toList.distinct
      )
      val hakukohdeOidit = hakemuksenValinnantulokset.keySet.union(hakutoiveetSijoittelussa.keySet)
      hakija.map(_.dto(hakukohdeOidit.map { hakukohdeOid =>
        if (hakutoiveetSijoittelussa.contains(hakukohdeOid)) {
          hakukohdeDtoSijoittelu(hakukohdeOid, valintatapajonotSijoittelussa, pistetiedotSijoittelussa, hakijaryhmatSijoittelussa, tilankuvauksetSijoittelussa)
        } else {
          hakukohdeDtoEiSijoittelua(hakukohdeOid)
        }
      }.toList))
    }
  }

  def dto(repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository,
          sijoitteluajoId: String,
          hakuOid: HakuOid,
          hakemusOid: HakemusOid): Option[HakijaDTO] = {
    dto(repository, Some(repository.getLatestSijoitteluajoIdThrowFailure(sijoitteluajoId, hakuOid)), hakuOid, hakemusOid)
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

  lazy val (haunValinnantulokset:Map[HakukohdeOid, Map[HakemusOid, Set[Valinnantulos]]], haunHakutoiveetByHakija:Map[HakemusOid, Set[HakukohdeOid]]) = {
    // Do this hacky thing to avoid iterating haunValinnantulokset every time just to get hakijan hakukohteet in haku.
    timed(s"Getting and grouping haun $hakuOid valinnantulokset") {
      val tulokset = repository.runBlocking(repository.getValinnantuloksetForHaku(hakuOid))
      (tulokset.groupBy(_.hakukohdeOid).mapValues(_.groupBy(_.hakemusOid)),tulokset.groupBy(_.hakemusOid).mapValues(_.map(_.hakukohdeOid)))
    }
  }

  lazy val hakutoiveSijoittelussa = sijoitteluajoId.map(repository.getHakukohteenHakemuksienHakutoiveSijoittelussa(hakukohdeOid, _).groupBy(_.hakemusOid)).getOrElse(Map())
  lazy val (hakutoiveenValintatapajonotSijoittelussa, hakutoiveenTilankuvausHashit) = {
    val valintatapajonot = sijoitteluajoId.map(repository.getHakukohteenHakemuksienHakutoiveenValintatapajonotSijoittelussa(hakukohdeOid, _)).getOrElse(List())
    (valintatapajonot.groupBy(_.hakemusOid).mapValues(_.groupBy(_.hakukohdeOid)), valintatapajonot.map(_.tilankuvausHash).distinct)
  }
  lazy val hakutoiveenTilankuvauksetSijoittelussa = repository.getValinnantilanKuvaukset(hakutoiveenTilankuvausHashit)
  lazy val hakukohteenValinnantulokset: Map[HakemusOid, Set[Valinnantulos]] = timed(s"Getting hakukohteen $hakukohdeOid valinnantulokset") {
    repository.runBlocking(repository.getValinnantuloksetForHakukohde(hakukohdeOid)).groupBy(_.hakemusOid)
  }

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
      val hakijanHakutoiveetSijoittelussa: Set[HakukohdeOid] = hakutoiveetSijoittelussa.get(hakija.hakemusOid).toSet.flatten.map(_.hakukohdeOid)
      val hakijanHakutoiveetValinnantuloksissa: Set[HakukohdeOid] = haunHakutoiveetByHakija.getOrElse(hakija.hakemusOid, Set())
      val hakijanHakutoiveetEiSijoittelua: Set[HakukohdeOid] = hakijanHakutoiveetValinnantuloksissa.filterNot(hakijanHakutoiveetSijoittelussa)

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
