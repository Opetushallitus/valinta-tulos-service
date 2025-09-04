package fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu

import fi.vm.sade.sijoittelu.domain.{SijoitteluAjo, ValintatuloksenTila}
import fi.vm.sade.sijoittelu.tulos.dto.raportointi._
import fi.vm.sade.valintatulosservice.config.Timer.timed
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
        sijoitteluajoId.map(id => repository.runBlocking(repository.getHakemuksenHakutoiveetSijoittelussa(hakemusOid, id)).map(h => h.hakukohdeOid -> h).toMap).getOrElse(Map()))
      case None => (Map[HakukohdeOid, Set[Valinnantulos]](), Map[HakukohdeOid, HakutoiveRecord]())
    }

  def getVastaanotto(hakukohdeOid: HakukohdeOid): ValintatuloksenTila = {
    val vastaanotto = hakemuksenValinnantulokset.getOrElse(hakukohdeOid, Set()).map(_.vastaanottotila)
    if(1 < vastaanotto.size) {
      val vastaanottoString = vastaanotto.mkString(",")
      throw new RuntimeException(s"Hakemukselle ${hakemusOid} löytyy monta vastaanottoa hakukohteelle ${hakukohdeOid}: ${vastaanottoString}")
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
                               hakijaryhmatSijoittelussa: Map[HakukohdeOid, List[HakutoiveenHakijaryhmaRecord]],
                               tilankuvauksetSijoittelussa: Map[Int, TilankuvausRecord]) = {
      val hakutoive = hakutoiveetSijoittelussa(hakukohdeOid)
      val valintatapajonot = valintatapajonotSijoittelussa.getOrElse(hakukohdeOid, List())
      val valintatapajonoOidit = valintatapajonot.map(_.valintatapajonoOid)
      val valinnantulokset = hakemuksenValinnantulokset.getOrElse(hakukohdeOid, List())
      val hakijaryhmat = hakijaryhmatSijoittelussa.getOrElse(hakukohdeOid, List()).map(_.dto)
      val valintatapajonoDtot = valintatapajonot.map{ j =>
        j.dto(
          valinnantulokset.find(_.valintatapajonoOid.equals(j.valintatapajonoOid)),
          tilankuvauksetSijoittelussa.get(j.tilankuvausHash),
          hakutoiveidenHakeneetSijoittelussa.getOrElse((hakukohdeOid, j.valintatapajonoOid), 0),
          hakutoiveidenHyvaksytyt.getOrElse((hakukohdeOid, j.valintatapajonoOid), 0)
        )
      }

      hakutoive.dto(
        getVastaanotto(hakukohdeOid),
        valintatapajonoDtot,
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
      hakutoive.dto(getVastaanotto(hakukohdeOid), valintatapajonoDtot, List())
    }



    if (hakutoiveetSijoittelussa.isEmpty) {
      hakija.map(_.dto(hakemuksenValinnantulokset.keySet.map(hakukohdeDtoEiSijoittelua).toList))
    } else {
      val valintatapajonotSijoittelussa = sijoitteluajoId.map(id => repository.runBlocking(repository.getHakemuksenHakutoiveidenValintatapajonotSijoittelussa(hakemusOid, id)).groupBy(_.hakukohdeOid)).getOrElse(Map())
      val hakijaryhmatSijoittelussa = sijoitteluajoId.map(repository.getHakemuksenHakutoiveidenHakijaryhmatSijoittelussa(hakemusOid, _).groupBy(_.hakukohdeOid)).getOrElse(Map())
      val tilankuvauksetSijoittelussa = repository.getValinnantilanKuvaukset(
        valintatapajonotSijoittelussa.values.flatten.map(_.tilankuvausHash).toList.distinct
      )
      val hakukohdeOidit = hakemuksenValinnantulokset.keySet.union(hakutoiveetSijoittelussa.keySet)
      hakija.map(_.dto(hakukohdeOidit.map { hakukohdeOid =>
        if (hakutoiveetSijoittelussa.contains(hakukohdeOid)) {
          hakukohdeDtoSijoittelu(hakukohdeOid, valintatapajonotSijoittelussa, hakijaryhmatSijoittelussa, tilankuvauksetSijoittelussa)
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
    dto(repository, Some(repository.runBlocking(repository.getLatestSijoitteluajoId(sijoitteluajoId, hakuOid))), hakuOid, hakemusOid)
  }
}

object SijoitteluajonHakijat {

  def dto(repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository,
          sijoitteluajoId:Option[Long],
          hakuOid:HakuOid): List[HakijaDTO] = {
    val hakijat = repository.getHaunHakijat(hakuOid, sijoitteluajoId)
    val hakutoiveet = HakutoiveetGrouped.apply(sijoitteluajoId.map(repository.getHaunHakemuksienHakutoiveetSijoittelussa(hakuOid, _)).getOrElse(List()))
    val valinnantulokset = timed("Valinnantulokset haulle", 100) { ValinnantuloksetGrouped.apply(repository.runBlocking(repository.getValinnantuloksetForHaku(hakuOid))) }
    val valintatapajonot = ValintatapajonotGrouped.apply(sijoitteluajoId.map(repository.getHaunHakemuksienValintatapajonotSijoittelussa(hakuOid, _)).getOrElse(List()))
    val tilankuvauksetSijoittelussa = repository.getValinnantilanKuvaukset(valintatapajonot.tilankuvausHashit)
    val hakijaryhmatSijoittelussa = sijoitteluajoId.map(repository.getHaunHakemuksienHakijaryhmatSijoittelussa(hakuOid, _)).getOrElse(Map())
    dto(hakijat, hakutoiveet, valinnantulokset, valintatapajonot, tilankuvauksetSijoittelussa, hakijaryhmatSijoittelussa)
  }

  def dto(repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository, sijoitteluajo: SijoitteluAjo, hakukohdeOid: HakukohdeOid): List[HakijaDTO] = {
    dto(repository, SyntheticSijoitteluAjoForHakusWithoutSijoittelu.getSijoitteluajoId(sijoitteluajo), HakuOid(sijoitteluajo.getHakuOid), hakukohdeOid)
  }

  def dto(repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository,
          sijoitteluajoId:Option[Long],
          hakuOid:HakuOid,
          hakukohdeOid: HakukohdeOid,
          haunValinnantulokset: Option[ValinnantuloksetGrouped] = None): List[HakijaDTO] = {
    val hakijat = repository.getHakukohteenHakijat(hakukohdeOid, sijoitteluajoId)
    val hakutoiveet = HakutoiveetGrouped.apply(sijoitteluajoId.map(repository.getHakukohteenHakemuksienHakutoiveetSijoittelussa(hakukohdeOid, _)).getOrElse(List()))
    val valinnantulokset: ValinnantuloksetGrouped = haunValinnantulokset.getOrElse {
      timed("Valinnantulokset haulle", 100) { ValinnantuloksetGrouped.apply(repository.runBlocking(repository.getValinnantuloksetForHaku(hakuOid))) }
    }
    val valintatapajonot = ValintatapajonotGrouped.apply(sijoitteluajoId.map(repository.getHakukohteenHakemuksienValintatapajonotSijoittelussa(hakukohdeOid, _)).getOrElse(List()))
    val tilankuvauksetSijoittelussa = repository.getValinnantilanKuvaukset(valintatapajonot.tilankuvausHashit)
    val hakijaryhmatSijoittelussa = sijoitteluajoId.map(repository.getHakukohteenHakemuksienHakijaryhmatSijoittelussa(hakukohdeOid, _)).getOrElse(Map())
    dto(hakijat, hakutoiveet, valinnantulokset, valintatapajonot, tilankuvauksetSijoittelussa, hakijaryhmatSijoittelussa)
  }

  def kevytDto(repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository,
               sijoitteluajo: SijoitteluAjo): List[KevytHakijaDTO] = {
    kevytDto(repository, SyntheticSijoitteluAjoForHakusWithoutSijoittelu.getSijoitteluajoId(sijoitteluajo), HakuOid(sijoitteluajo.getHakuOid))
  }

  def kevytDto(repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository,
               sijoitteluajoId:Option[Long],
               hakuOid:HakuOid): List[KevytHakijaDTO] = {

    val hakijat = repository.getHaunHakijat(hakuOid, sijoitteluajoId)
    val hakutoiveet = HakutoiveetGrouped.apply(sijoitteluajoId.map(repository.getHaunHakemuksienHakutoiveetSijoittelussa(hakuOid, _)).getOrElse(List()))
    val valinnantulokset = timed("Valinnantulokset haulle", 100) { ValinnantuloksetGrouped.apply(repository.runBlocking(repository.getValinnantuloksetForHaku(hakuOid)), false) }
    val valintatapajonot = ValintatapajonotGrouped.apply(sijoitteluajoId.map(repository.getHaunHakemuksienValintatapajonotSijoittelussa(hakuOid, _)).getOrElse(List()))
    val tilankuvauksetSijoittelussa = repository.getValinnantilanKuvaukset(valintatapajonot.tilankuvausHashit)
    kevytDto(hakijat, hakutoiveet, valinnantulokset, valintatapajonot, tilankuvauksetSijoittelussa)
  }

  def kevytDto(repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository, sijoitteluajo: SijoitteluAjo, hakukohdeOid: HakukohdeOid): List[KevytHakijaDTO] = {
    kevytDto(repository, SyntheticSijoitteluAjoForHakusWithoutSijoittelu.getSijoitteluajoId(sijoitteluajo), HakuOid(sijoitteluajo.getHakuOid), hakukohdeOid)
  }

  def kevytDto(repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository,
               sijoitteluajoId:Option[Long],
               hakuOid:HakuOid,
               hakukohdeOid: HakukohdeOid): List[KevytHakijaDTO] = {
    val hakijat = repository.getHakukohteenHakijat(hakukohdeOid, sijoitteluajoId)
    val hakutoiveet = HakutoiveetGrouped.apply(sijoitteluajoId.map(repository.getHakukohteenHakemuksienHakutoiveetSijoittelussa(hakukohdeOid, _)).getOrElse(List()))
    val valinnantulokset = timed("Valinnantulokset haulle", 100) { ValinnantuloksetGrouped.apply(repository.runBlocking(repository.getValinnantuloksetForHaku(hakuOid)), false) }
    val valintatapajonot = ValintatapajonotGrouped.apply(sijoitteluajoId.map(repository.getHakukohteenHakemuksienValintatapajonotSijoittelussa(hakukohdeOid, _)).getOrElse(List()))
    val tilankuvauksetSijoittelussa = repository.getValinnantilanKuvaukset(valintatapajonot.tilankuvausHashit)
    kevytDto(hakijat, hakutoiveet, valinnantulokset, valintatapajonot, tilankuvauksetSijoittelussa)
  }

  def kevytDtoVainHakukohde(repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository, sijoitteluajo: SijoitteluAjo, hakukohdeOid: HakukohdeOid): List[KevytHakijaDTO] = {
    kevytDtoVainHakukohde(repository, SyntheticSijoitteluAjoForHakusWithoutSijoittelu.getSijoitteluajoId(sijoitteluajo), HakuOid(sijoitteluajo.getHakuOid), hakukohdeOid)
  }

  def kevytDtoVainHakukohde(repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository,
                            sijoitteluajoId:Option[Long],
                            hakuOid:HakuOid,
                            hakukohdeOid: HakukohdeOid): List[KevytHakijaDTO] = {

    val hakijat = repository.getHakukohteenHakijat(hakukohdeOid, sijoitteluajoId)
    val hakutoive = sijoitteluajoId.map(repository.getHakukohteenHakemuksienHakutoiveSijoittelussa(hakukohdeOid, _)).getOrElse(List()).groupBy(_.hakemusOid)
    val valintatapajonot = ValintatapajonotGrouped.apply(sijoitteluajoId.map(repository.getHakukohteenHakemuksienHakutoiveenValintatapajonotSijoittelussa(hakukohdeOid, _)).getOrElse(List()))
    val hakutoiveenTilankuvauksetSijoittelussa = repository.getValinnantilanKuvaukset(valintatapajonot.tilankuvausHashit)
    val hakukohteenValinnantulokset = timed(s"Getting hakukohteen $hakukohdeOid valinnantulokset") {
      repository.runBlocking(repository.getValinnantuloksetForHakukohde(hakukohdeOid))
        .groupBy(_.hakemusOid)
        .map(t => t._1 -> t._2.groupBy(_.hakukohdeOid))
    }

    hakijat.map(hakija => hakija.kevytDto( hakutoive.get(hakija.hakemusOid) match {
      case Some(x) =>
        kevytHakukohdeDTOtSijoittelu(
          hakija.hakemusOid,
          hakutoive.getOrElse(hakija.hakemusOid, List()),
          valintatapajonot.valintatapajonotSijoittelussa.getOrElse(hakija.hakemusOid, Map()),
          hakukohteenValinnantulokset.getOrElse(hakija.hakemusOid, Map()),
          hakutoiveenTilankuvauksetSijoittelussa
        )
      case None =>
        kevytHakukohdeDTOtEiSijoittelua(
          hakija.hakemusOid,
          Set(hakukohdeOid),
          hakukohteenValinnantulokset.getOrElse(hakija.hakemusOid, Map())
        )
    }))
  }

  private def kevytDto(hakijat: Seq[HakijaRecord],
                       hakutoiveet: HakutoiveetGrouped,
                       valinnantulokset: ValinnantuloksetGrouped,
                       valintatapajonot: ValintatapajonotGrouped,
                       tilankuvaukset: Map[Int, TilankuvausRecord]): List[KevytHakijaDTO] = hakijat.map(hakija => {

    val hakijanHakutoiveetEiSijoittelua: Set[HakukohdeOid] = filterHakijanHakutoiveetEiSijoittelua(hakija.hakemusOid, valinnantulokset, hakutoiveet)

    hakija.kevytDto(
      kevytHakukohdeDTOtSijoittelu(
        hakija.hakemusOid,
        hakutoiveet.hakutoiveetSijoittelussa.getOrElse(hakija.hakemusOid, List()),
        valintatapajonot.valintatapajonotSijoittelussa.getOrElse(hakija.hakemusOid, Map()),
        valinnantulokset.valinnantuloksetHakemuksittain.getOrElse(hakija.hakemusOid, Map()),
        tilankuvaukset
      ).union(
        kevytHakukohdeDTOtEiSijoittelua(
          hakija.hakemusOid,
          hakijanHakutoiveetEiSijoittelua,
          valinnantulokset.valinnantuloksetHakemuksittain.getOrElse(hakija.hakemusOid, Map())
        )
      )
    )
  }).toList

  private def dto(hakijat: Seq[HakijaRecord],
                  hakutoiveet: HakutoiveetGrouped,
                  valinnantulokset: ValinnantuloksetGrouped,
                  valintatapajonot: ValintatapajonotGrouped,
                  tilankuvaukset: Map[Int, TilankuvausRecord],
                  hakijaryhmat: Map[HakemusOid, List[HakutoiveenHakijaryhmaRecord]]): List[HakijaDTO] = timed("HakijaDTOiden luonti " + hakijat.size + " hakijalle", 10) {
    hakijat.par.map(hakija => {
      val hakijanHakutoiveetEiSijoittelua: Set[HakukohdeOid] = filterHakijanHakutoiveetEiSijoittelua(hakija.hakemusOid, valinnantulokset, hakutoiveet)
      timed("HakijaDTOn luonti", 100)(hakija.dto(
        hakutoiveDTOtSijoittelu(
          hakija.hakemusOid,
          hakutoiveet.hakutoiveetSijoittelussa.getOrElse(hakija.hakemusOid, List()),
          valintatapajonot.valintatapajonotSijoittelussa.getOrElse(hakija.hakemusOid, Map()),
          valinnantulokset.valinnantuloksetHakemuksittain.getOrElse(hakija.hakemusOid, Map()),
          hakijaryhmat.getOrElse(hakija.hakemusOid, List()).groupBy(_.hakukohdeOid),
          valinnantulokset.hyvaksytytJaHakeneetHakukohteittain,
          tilankuvaukset
        ).union(
          hakutoiveDTOtEiSijoittelua(
            hakija.hakemusOid,
            hakijanHakutoiveetEiSijoittelua,
            valinnantulokset.valinnantuloksetHakemuksittain.getOrElse(hakija.hakemusOid, Map()),
            valinnantulokset.hyvaksytytJaHakeneetHakukohteittain
          )
        )
      ))
    })
  }.toList

  private def getVastaanotto(hakemusOid:HakemusOid, hakukohdeOid: HakukohdeOid, hakemuksenValinnantuloksetHakutoiveella:Set[Valinnantulos]): ValintatuloksenTila = {
    val vastaanotto = hakemuksenValinnantuloksetHakutoiveella.map(_.vastaanottotila)
    if(1 < vastaanotto.size) {
      throw new RuntimeException(s"Hakemukselle ${hakemusOid} löytyy monta vastaanottoa hakukohteelle ${hakukohdeOid}")
    } else {
      vastaanotto.headOption.getOrElse(ValintatuloksenTila.KESKEN)
    }
  }

  private def hakutoiveDTOtSijoittelu(hakemusOid: HakemusOid,
                                      hakemuksenHakutoiveet: List[HakutoiveRecord],
                                      hakemuksenValintatapajonot: Map[HakukohdeOid, List[HakutoiveenValintatapajonoRecord]],
                                      hakemuksenValinnantulokset: Map[HakukohdeOid, Set[Valinnantulos]],
                                      hakemuksenHakijaryhmat: Map[HakukohdeOid, List[HakutoiveenHakijaryhmaRecord]],
                                      hyvaksytytJaHakeneet:Map[HakukohdeOid, Map[ValintatapajonoOid,HyvaksytytJaHakeneet]],
                                      tilankuvaukset: Map[Int, TilankuvausRecord]): List[HakutoiveDTO] = {
    hakemuksenHakutoiveet.map(hakukohde => {
      val hakemuksenValinnantuloksetHakutoiveella = hakemuksenValinnantulokset.getOrElse(hakukohde.hakukohdeOid, Set())
      val hakutoiveenHyvaksytytJaHakeneet = hyvaksytytJaHakeneet.getOrElse(hakukohde.hakukohdeOid, Map())

      hakukohde.dto(
        getVastaanotto(hakemusOid, hakukohde.hakukohdeOid, hakemuksenValinnantuloksetHakutoiveella),
        hakemuksenValintatapajonot.getOrElse(hakukohde.hakukohdeOid, List()).map(jono => {
          val jononHyvaksytytJaHakeneet = hakutoiveenHyvaksytytJaHakeneet.getOrElse(jono.valintatapajonoOid, HyvaksytytJaHakeneet(0,0))
          jono.dto(
            hakemuksenValinnantuloksetHakutoiveella.find(_.valintatapajonoOid.equals(jono.valintatapajonoOid)),
            tilankuvaukset.get(jono.tilankuvausHash),
            jononHyvaksytytJaHakeneet.hakeneet,
            jononHyvaksytytJaHakeneet.hyvaksytyt)}
        ),
        hakemuksenHakijaryhmat.getOrElse(hakukohde.hakukohdeOid, List()).map(_.dto)
      )
    })
  }

  private def hakutoiveDTOtEiSijoittelua(hakemusOid: HakemusOid,
                                         hakukohdeOids: Set[HakukohdeOid],
                                         hakemuksenValinnantulokset: Map[HakukohdeOid, Set[Valinnantulos]],
                                         hyvaksytytJaHakeneet:Map[HakukohdeOid, Map[ValintatapajonoOid,HyvaksytytJaHakeneet]]): List[HakutoiveDTO] = timed("HakukohdeDTOt ei sijoittelua", 10) {
    hakukohdeOids.map(hakukohdeOid => {
      val hakemuksenValinnantuloksetHakutoiveella = hakemuksenValinnantulokset.getOrElse(hakukohdeOid, Set())
      val hakutoiveenHyvaksytytJaHakeneet = hyvaksytytJaHakeneet.getOrElse(hakukohdeOid, Map())

      HakutoiveRecord(hakemusOid, Some(1), hakukohdeOid, None).dto(
        getVastaanotto(hakemusOid, hakukohdeOid, hakemuksenValinnantuloksetHakutoiveella),
        hakemuksenValinnantulokset.getOrElse(hakukohdeOid, Set()).map(valinnantulos => HakutoiveenValintatapajonoRecord.dto(
          valinnantulos,
          hakutoiveenHyvaksytytJaHakeneet.getOrElse(valinnantulos.valintatapajonoOid, HyvaksytytJaHakeneet(0,0)).hakeneet,
          hakutoiveenHyvaksytytJaHakeneet.getOrElse(valinnantulos.valintatapajonoOid, HyvaksytytJaHakeneet(0,0)).hyvaksytyt)).toList,
        List()
      )}
    ).toList
  }

  private def kevytHakukohdeDTOtEiSijoittelua(hakemusOid: HakemusOid,
                                              hakukohdeOids: Set[HakukohdeOid],
                                              hakemuksenValinnantulokset: Map[HakukohdeOid, Set[Valinnantulos]]): List[KevytHakutoiveDTO] = {
    hakukohdeOids.map(hakukohdeOid => {
      val hakemuksenValinnantuloksetHakutoiveella = hakemuksenValinnantulokset.getOrElse(hakukohdeOid, Set())
      HakutoiveRecord(hakemusOid, Some(1), hakukohdeOid, None).kevytDto(
        hakemuksenValinnantuloksetHakutoiveella.map(valinnantulos => HakutoiveenValintatapajonoRecord.kevytDto(valinnantulos)).toList)
    }).toList
  }

  private def kevytHakukohdeDTOtSijoittelu(hakemusOid: HakemusOid,
                                           hakemuksenHakutoiveet: List[HakutoiveRecord],
                                           hakemuksenValintatapajonot: Map[HakukohdeOid, List[HakutoiveenValintatapajonoRecord]],
                                           hakemuksenValinnantulokset: Map[HakukohdeOid, Set[Valinnantulos]],
                                           tilankuvaukset: Map[Int, TilankuvausRecord]): List[KevytHakutoiveDTO] = {
    hakemuksenHakutoiveet.map(hakukohde => {
      val hakemuksenValinnantuloksetHakutoiveella = hakemuksenValinnantulokset.getOrElse(hakukohde.hakukohdeOid, Set())

      hakukohde.kevytDto(
        hakemuksenValintatapajonot.getOrElse(hakukohde.hakukohdeOid, List()).map(jono => jono.kevytDto(
          hakemuksenValinnantuloksetHakutoiveella.find(_.valintatapajonoOid.equals(jono.valintatapajonoOid)),
          tilankuvaukset.get(jono.tilankuvausHash)))
      )}
    )
  }

  private def filterHakijanHakutoiveetEiSijoittelua(hakemusOid: HakemusOid, valinnantulokset:ValinnantuloksetGrouped, hakutoiveet: HakutoiveetGrouped) = {
    val hakijanHakutoiveetSijoittelussa: Set[HakukohdeOid] = hakutoiveet.hakutoiveOiditHakemuksittain.getOrElse(hakemusOid, Set())
    val hakijanHakutoiveetValinnantuloksissa: Set[HakukohdeOid] = valinnantulokset.hakutoiveOiditHakemuksittain.getOrElse(hakemusOid, Set())
    hakijanHakutoiveetValinnantuloksissa.filterNot(hakijanHakutoiveetSijoittelussa)
  }

  case class ValinnantuloksetGrouped(valinnantuloksetHakemuksittain: Map[HakemusOid, Map[HakukohdeOid, Set[Valinnantulos]]],
                                     hakutoiveOiditHakemuksittain: Map[HakemusOid, Set[HakukohdeOid]],
                                     hyvaksytytJaHakeneetHakukohteittain: Map[HakukohdeOid, Map[ValintatapajonoOid, HyvaksytytJaHakeneet]])

  object ValinnantuloksetGrouped {
    def apply(valinnantulokset: Set[Valinnantulos], countHyvaksytytJaHakeneet:Boolean = true):ValinnantuloksetGrouped =
      ValinnantuloksetGrouped(
        valinnantulokset.groupBy(_.hakemusOid).map(t => t._1 -> t._2.groupBy(_.hakukohdeOid)),
        valinnantulokset.groupBy(_.hakemusOid).map(t => t._1 -> t._2.map(_.hakukohdeOid)),
        Option(countHyvaksytytJaHakeneet).collect { case true =>
          valinnantulokset.groupBy(_.hakukohdeOid)
            .map(t => t._1 -> t._2.groupBy(_.valintatapajonoOid).map(t => t._1 -> HyvaksytytJaHakeneet(t._2)))
        }.getOrElse(Map())
      )
  }

  case class HakutoiveetGrouped(hakutoiveetSijoittelussa: Map[HakemusOid, List[HakutoiveRecord]], hakutoiveOiditHakemuksittain: Map[HakemusOid, Set[HakukohdeOid]])

  object HakutoiveetGrouped {
    def apply(hakutoiveet: List[HakutoiveRecord]):HakutoiveetGrouped =
      HakutoiveetGrouped(
        hakutoiveet.groupBy(_.hakemusOid),
        hakutoiveet.groupBy(_.hakemusOid).map(t => t._1 -> t._2.map(_.hakukohdeOid).toSet)
      )
  }

  case class ValintatapajonotGrouped(valintatapajonotSijoittelussa:Map[HakemusOid,Map[HakukohdeOid, List[HakutoiveenValintatapajonoRecord]]], tilankuvausHashit:List[Int])

  object ValintatapajonotGrouped {
    def apply(valintatapajonot: List[HakutoiveenValintatapajonoRecord]):ValintatapajonotGrouped =
      ValintatapajonotGrouped(
        valintatapajonot.groupBy(_.hakemusOid).map(t => t._1 -> t._2.groupBy(_.hakukohdeOid)),
        valintatapajonot.map(_.tilankuvausHash).distinct
      )
  }

  case class HyvaksytytJaHakeneet(hyvaksytyt:Int, hakeneet:Int)

  object HyvaksytytJaHakeneet {
    def apply(jononTulokset: Set[Valinnantulos]): HyvaksytytJaHakeneet = {
      HyvaksytytJaHakeneet(jononTulokset.count(vt => vt.valinnantila == Hyvaksytty || vt.valinnantila == VarasijaltaHyvaksytty), jononTulokset.size)
    }
  }
}
