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
        timed(s"Getting valinnantulokset for hakemus $hakemusOid") {
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

object SijoitteluajonHakijat {

  def dto(repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository,
          sijoitteluajoId:Option[Long],
          hakuOid:HakuOid): List[HakijaDTO] = {

    val hakijat = repository.getHaunHakijat(hakuOid, sijoitteluajoId)
    val hakutoiveetSijoittelussa = sijoitteluajoId.map(repository.getHaunHakemuksienHakutoiveetSijoittelussa(hakuOid, _).groupBy(_.hakemusOid)).getOrElse(Map())

    val (
      haunValinnantulokset:Map[HakemusOid, Map[HakukohdeOid, Set[Valinnantulos]]],
      haunHakutoiveet:Map[HakemusOid, Set[HakukohdeOid]],
      hyvaksytytJaHakeneet:Map[HakukohdeOid, Map[ValintatapajonoOid,(Int,Int)]]) = timed(s"Getting and grouping haun $hakuOid valinnantulokset") {
        // Do this hacky thing to avoid iterating haunValinnantulokset every time just to get hakijan hakukohteet in haku.
        val tulokset = repository.runBlocking(repository.getValinnantuloksetForHaku(hakuOid))

        (
          tulokset.groupBy(_.hakemusOid).mapValues(_.groupBy(_.hakukohdeOid)),
          tulokset.groupBy(_.hakemusOid).mapValues(_.map(_.hakukohdeOid)),
          tulokset.groupBy(_.hakukohdeOid).mapValues(_.groupBy(_.valintatapajonoOid).mapValues(jononTulokset => {
            (jononTulokset.count(vt => vt.valinnantila == Hyvaksytty || vt.valinnantila == VarasijaltaHyvaksytty), jononTulokset.size)
          }))
        )
    }

    val (valintatapajonotSijoittelussa, tilankuvausHashit) = {
      val valintatapajonot = sijoitteluajoId.map(repository.getHaunHakemuksienValintatapajonotSijoittelussa(hakuOid, _)).getOrElse(List())
      (valintatapajonot.groupBy(_.hakemusOid).mapValues(_.groupBy(_.hakukohdeOid)), valintatapajonot.map(_.tilankuvausHash).distinct)
    }
    val tilankuvauksetSijoittelussa = repository.getValinnantilanKuvaukset(tilankuvausHashit)
    val pistetiedotSijoittelussa = sijoitteluajoId.map(repository.getHaunHakemuksienPistetiedotSijoittelussa(hakuOid, _)).getOrElse(Map())
    val hakijaryhmatSijoittelussa = sijoitteluajoId.map(repository.getHaunHakemuksienHakijaryhmatSijoittelussa(hakuOid, _)).getOrElse(Map())

    def getVastaanotto(hakemusOid:HakemusOid, hakukohdeOid: HakukohdeOid, hakemuksenValinnantuloksetHakutoiveella:Set[Valinnantulos]): ValintatuloksenTila = {
      val vastaanotto = hakemuksenValinnantuloksetHakutoiveella.map(_.vastaanottotila)
      if(1 < vastaanotto.size) {
        throw new RuntimeException(s"Hakemukselle ${hakemusOid} löytyy monta vastaanottoa hakukohteelle ${hakukohdeOid}")
      } else {
        vastaanotto.headOption.getOrElse(ValintatuloksenTila.KESKEN)
      }
    }

    def hakukohdeDtotSijoittelu(hakemusOid: HakemusOid): List[HakutoiveDTO] = timed("HakukohdeDTOt sijoittelu", 10) {
      val hakemuksenValinnantulokset = haunValinnantulokset.getOrElse(hakemusOid, Map())

      hakutoiveetSijoittelussa.getOrElse(hakemusOid, List()).map(hakukohde =>
      {
        val hakutoiveenPistetidot = pistetiedotSijoittelussa.getOrElse(hakukohde.hakukohdeOid, List()).groupBy(_.hakemusOid)
        val hakemuksenValinnantuloksetHakutoiveella = hakemuksenValinnantulokset.getOrElse(hakukohde.hakukohdeOid, Set())
        val hakutoiveenHyvaksytytJaHakeneet = hyvaksytytJaHakeneet.getOrElse(hakukohde.hakukohdeOid, Map())

        hakukohde.dto(
          getVastaanotto(hakemusOid, hakukohde.hakukohdeOid, hakemuksenValinnantuloksetHakutoiveella),
          valintatapajonotSijoittelussa.getOrElse(hakemusOid, Map()).getOrElse(hakukohde.hakukohdeOid, List()).map(jono => {
            val jononHyvaksytytJaHakeneet = hakutoiveenHyvaksytytJaHakeneet.getOrElse(jono.valintatapajonoOid, (0,0))
            jono.dto(
            //valinnantulos: Option[Valinnantulos], tilankuvaukset: Map[String, String], hakeneet:Int, hyvaksytty:Int
              hakemuksenValinnantuloksetHakutoiveella.find(_.valintatapajonoOid.equals(jono.valintatapajonoOid)),
              jono.tilankuvaukset(tilankuvauksetSijoittelussa.get(jono.tilankuvausHash)),
              jononHyvaksytytJaHakeneet._2,
              jononHyvaksytytJaHakeneet._1)}
          ),
          hakutoiveenPistetidot.getOrElse(hakemusOid, List()).map(_.dto),
          hakijaryhmatSijoittelussa.getOrElse(hakemusOid, List()).filter(_.hakukohdeOid == hakukohde.hakukohdeOid).map(_.dto)
        )
      })
    }

    def hakukohdeDtotEiSijoittelua(hakemusOid: HakemusOid, hakukohdeOids: Set[HakukohdeOid]): List[HakutoiveDTO] = timed("HakukohdeDTOt ei sijoittelua", 10) {
      hakukohdeOids.map(hakukohdeOid => {
        val hakemuksenValinnantuloksetHakutoiveella = haunValinnantulokset.getOrElse(hakemusOid, Map()).getOrElse(hakukohdeOid, Set())
        val hakutoiveenHyvaksytytJaHakeneet = hyvaksytytJaHakeneet.getOrElse(hakukohdeOid, Map())

        //vastaanottotieto:fi.vm.sade.sijoittelu.domain.ValintatuloksenTila, valintatapajonot:List[HakutoiveenValintatapajonoDTO], pistetiedot:List[PistetietoDTO], hakijaryhmat:List[HakutoiveenHakijaryhmaDTO]
        HakutoiveRecord(hakemusOid, Some(1), hakukohdeOid, None).dto(
          getVastaanotto(hakemusOid, hakukohdeOid, hakemuksenValinnantuloksetHakutoiveella),
          haunValinnantulokset.getOrElse(hakemusOid, Map()).getOrElse(hakukohdeOid, Set()).map(valinnantulos => HakutoiveenValintatapajonoRecord.dto(
            valinnantulos,
            hakutoiveenHyvaksytytJaHakeneet.getOrElse(valinnantulos.valintatapajonoOid, (0,0))._2,
            hakutoiveenHyvaksytytJaHakeneet.getOrElse(valinnantulos.valintatapajonoOid, (0,0))._1)).toList,
          List(),
          List()
        )}
      ).toList
    }

    timed("HakijaDTOt", 10)(hakijat.map(hakija => {
      val hakijanHakutoiveetSijoittelussa: Set[HakukohdeOid] = hakutoiveetSijoittelussa.get(hakija.hakemusOid).toSet.flatten.map(_.hakukohdeOid)
      val hakijanHakutoiveetValinnantuloksissa: Set[HakukohdeOid] = haunHakutoiveet.getOrElse(hakija.hakemusOid, Set())
      val hakijanHakutoiveetEiSijoittelua: Set[HakukohdeOid] = hakijanHakutoiveetValinnantuloksissa.filterNot(hakijanHakutoiveetSijoittelussa)

      timed("HakijaDTO", 10)(hakija.dto(
        hakukohdeDtotSijoittelu(hakija.hakemusOid).union(hakukohdeDtotEiSijoittelua(hakija.hakemusOid, hakijanHakutoiveetEiSijoittelua))
      ))
    }))
  }

  def dto(repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository,
          sijoitteluajoId:Option[Long],
          hakuOid:HakuOid,
          hakukohdeOid: HakukohdeOid): List[HakijaDTO] = {
    val hakijat = repository.getHakukohteenHakijat(hakukohdeOid, sijoitteluajoId)
    val hakutoiveetSijoittelussa = sijoitteluajoId.map(repository.getHakukohteenHakemuksienHakutoiveetSijoittelussa(hakukohdeOid, _).groupBy(_.hakemusOid)).getOrElse(Map())
    val (haunValinnantulokset:Map[HakukohdeOid, Map[HakemusOid, Set[Valinnantulos]]],
         haunHakutoiveetByHakija:Map[HakemusOid, Set[HakukohdeOid]],
         hyvaksytytJaHakeneet:Map[HakukohdeOid, Map[ValintatapajonoOid,(Int,Int)]]) = {
      // Do this hacky thing to avoid iterating haunValinnantulokset every time just to get hakijan hakukohteet in haku.
      timed(s"Getting and grouping haun $hakuOid valinnantulokset") {
        val tulokset = repository.runBlocking(repository.getValinnantuloksetForHaku(hakuOid))
        (tulokset.groupBy(_.hakukohdeOid).mapValues(_.groupBy(_.hakemusOid)),
         tulokset.groupBy(_.hakemusOid).mapValues(_.map(_.hakukohdeOid)),
         tulokset.groupBy(_.hakukohdeOid).mapValues(_.groupBy(_.valintatapajonoOid).mapValues(jononTulokset => {
           (jononTulokset.count(vt => vt.valinnantila == Hyvaksytty || vt.valinnantila == VarasijaltaHyvaksytty),
            jononTulokset.size)
         }))
        )
      }
    }
    val (valintatapajonotSijoittelussa, tilankuvausHashit) = {
      val valintatapajonot = sijoitteluajoId.map(repository.getHakukohteenHakemuksienValintatapajonotSijoittelussa(hakukohdeOid, _)).getOrElse(List())
      (valintatapajonot.groupBy(_.hakemusOid).mapValues(_.groupBy(_.hakukohdeOid)), valintatapajonot.map(_.tilankuvausHash).distinct)
    }
    val tilankuvauksetSijoittelussa = repository.getValinnantilanKuvaukset(tilankuvausHashit)
    val pistetiedotSijoittelussa = sijoitteluajoId.map(repository.getHakukohteenHakemuksienPistetiedotSijoittelussa(hakukohdeOid, _)).getOrElse(Map())
    val hakijaryhmatSijoittelussa = sijoitteluajoId.map(repository.getHakukohteenHakemuksienHakijaryhmatSijoittelussa(hakukohdeOid, _)).getOrElse(Map())

    def getVastaanotto(hakukohdeOid: HakukohdeOid, hakemusOid: HakemusOid): ValintatuloksenTila = {
      val vastaanotto = haunValinnantulokset.getOrElse(hakukohdeOid, Map()).getOrElse(hakemusOid, Set()).map(_.vastaanottotila)
      if(1 < vastaanotto.size) {
        throw new RuntimeException(s"Hakemukselle ${hakemusOid} löytyy monta vastaanottoa hakukohteelle ${hakukohdeOid}")
      } else {
        vastaanotto.headOption.getOrElse(ValintatuloksenTila.KESKEN)
      }
    }

    //vastaanottotieto:fi.vm.sade.sijoittelu.domain.ValintatuloksenTila, valintatapajonot:List[HakutoiveenValintatapajonoDTO], pistetiedot:List[PistetietoDTO], hakijaryhmat:List[HakutoiveenHakijaryhmaDTO]
    def hakukohdeDtotSijoittelu(hakemusOid: HakemusOid): List[HakutoiveDTO] = {
      hakutoiveetSijoittelussa.getOrElse(hakemusOid, List()).map(hakukohde =>
      {
        val hakutoiveenPistetidot = pistetiedotSijoittelussa.getOrElse(hakukohde.hakukohdeOid, List()).groupBy(_.hakemusOid)
        hakukohde.dto(
          getVastaanotto(hakukohde.hakukohdeOid, hakemusOid),
          valintatapajonotSijoittelussa.getOrElse(hakemusOid, Map()).getOrElse(hakukohde.hakukohdeOid, List()).map(jono => jono.dto(
            //valinnantulos: Option[Valinnantulos], tilankuvaukset: Map[String, String], hakeneet:Int, hyvaksytty:Int
            haunValinnantulokset.get(hakukohde.hakukohdeOid).flatMap(_.get(hakemusOid)).flatMap(_.find(_.valintatapajonoOid.equals(jono.valintatapajonoOid))),
            jono.tilankuvaukset(tilankuvauksetSijoittelussa.get(jono.tilankuvausHash)),
            hyvaksytytJaHakeneet.getOrElse(hakukohde.hakukohdeOid, Map()).getOrElse(jono.valintatapajonoOid, (0,0))._1,
            hyvaksytytJaHakeneet.getOrElse(hakukohde.hakukohdeOid, Map()).getOrElse(jono.valintatapajonoOid, (0,0))._2)
          ),
          hakutoiveenPistetidot.getOrElse(hakemusOid, List()).map(_.dto),
          hakijaryhmatSijoittelussa.getOrElse(hakemusOid, List()).filter(_.hakukohdeOid == hakukohde.hakukohdeOid).map(_.dto)
        )
      })
    }

    def hakukohdeDtotEiSijoittelua(hakemusOid: HakemusOid, hakukohdeOids: Set[HakukohdeOid]): List[HakutoiveDTO] = {
      hakukohdeOids.map(hakukohdeOid => {
        //vastaanottotieto:fi.vm.sade.sijoittelu.domain.ValintatuloksenTila, valintatapajonot:List[HakutoiveenValintatapajonoDTO], pistetiedot:List[PistetietoDTO], hakijaryhmat:List[HakutoiveenHakijaryhmaDTO]
        HakutoiveRecord(hakemusOid, Some(1), hakukohdeOid, None).dto(
          getVastaanotto(hakukohdeOid, hakemusOid),
          haunValinnantulokset.getOrElse(hakukohdeOid, Map()).getOrElse(hakemusOid, Set()).map(valinnantulos => HakutoiveenValintatapajonoRecord.dto(
            valinnantulos,
            hyvaksytytJaHakeneet.getOrElse(hakukohdeOid, Map()).getOrElse(valinnantulos.valintatapajonoOid, (0,0))._1,
            hyvaksytytJaHakeneet.getOrElse(hakukohdeOid, Map()).getOrElse(valinnantulos.valintatapajonoOid, (0,0))._2)).toList,
          List(),
          List()
        )}
      ).toList
    }


    hakijat.map(hakija => {
      val hakijanHakutoiveetSijoittelussa: Set[HakukohdeOid] = hakutoiveetSijoittelussa.get(hakija.hakemusOid).toSet.flatten.map(_.hakukohdeOid)
      val hakijanHakutoiveetValinnantuloksissa: Set[HakukohdeOid] = haunHakutoiveetByHakija.getOrElse(hakija.hakemusOid, Set())
      val hakijanHakutoiveetEiSijoittelua: Set[HakukohdeOid] = hakijanHakutoiveetValinnantuloksissa.filterNot(hakijanHakutoiveetSijoittelussa)

      hakija.dto(
        hakukohdeDtotSijoittelu(hakija.hakemusOid).union(hakukohdeDtotEiSijoittelua(hakija.hakemusOid, hakijanHakutoiveetEiSijoittelua))
      )
    })
  }

  def kevytDto(repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository,
               sijoitteluajoId:Option[Long],
               hakuOid:HakuOid,
               hakukohdeOid: HakukohdeOid): List[KevytHakijaDTO] = {
    val hakijat = repository.getHakukohteenHakijat(hakukohdeOid, sijoitteluajoId)
    val hakutoiveetSijoittelussa = sijoitteluajoId.map(repository.getHakukohteenHakemuksienHakutoiveetSijoittelussa(hakukohdeOid, _).groupBy(_.hakemusOid)).getOrElse(Map())
    val (haunValinnantulokset:Map[HakukohdeOid, Map[HakemusOid, Set[Valinnantulos]]], haunHakutoiveetByHakija:Map[HakemusOid, Set[HakukohdeOid]]) = {
      // Do this hacky thing to avoid iterating haunValinnantulokset every time just to get hakijan hakukohteet in haku.
      timed(s"Getting and grouping haun $hakuOid valinnantulokset") {
        val tulokset = repository.runBlocking(repository.getValinnantuloksetForHaku(hakuOid))
        (tulokset.groupBy(_.hakukohdeOid).mapValues(_.groupBy(_.hakemusOid)),tulokset.groupBy(_.hakemusOid).mapValues(_.map(_.hakukohdeOid)))
      }
    }
    val (valintatapajonotSijoittelussa, tilankuvausHashit) = {
      val valintatapajonot = sijoitteluajoId.map(repository.getHakukohteenHakemuksienValintatapajonotSijoittelussa(hakukohdeOid, _)).getOrElse(List())
      (valintatapajonot.groupBy(_.hakemusOid).mapValues(_.groupBy(_.hakukohdeOid)), valintatapajonot.map(_.tilankuvausHash).distinct)
    }
    val tilankuvauksetSijoittelussa = repository.getValinnantilanKuvaukset(tilankuvausHashit)

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


    hakijat.map(hakija => {
      val hakijanHakutoiveetSijoittelussa: Set[HakukohdeOid] = hakutoiveetSijoittelussa.get(hakija.hakemusOid).toSet.flatten.map(_.hakukohdeOid)
      val hakijanHakutoiveetValinnantuloksissa: Set[HakukohdeOid] = haunHakutoiveetByHakija.getOrElse(hakija.hakemusOid, Set())
      val hakijanHakutoiveetEiSijoittelua: Set[HakukohdeOid] = hakijanHakutoiveetValinnantuloksissa.filterNot(hakijanHakutoiveetSijoittelussa)

      hakija.kevytDto(
        hakukohdeDtotSijoittelu(hakija.hakemusOid).union(hakukohdeDtotEiSijoittelua(hakija.hakemusOid, hakijanHakutoiveetEiSijoittelua))
      )
    })
  }

  def kevytDtoVainHakukohde(repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository,
                            sijoitteluajoId:Option[Long],
                            hakuOid:HakuOid,
                            hakukohdeOid: HakukohdeOid): List[KevytHakijaDTO] = {

    val hakijat = repository.getHakukohteenHakijat(hakukohdeOid, sijoitteluajoId)
    val hakutoiveSijoittelussa = sijoitteluajoId.map(repository.getHakukohteenHakemuksienHakutoiveSijoittelussa(hakukohdeOid, _).groupBy(_.hakemusOid)).getOrElse(Map())
    val (hakutoiveenValintatapajonotSijoittelussa, hakutoiveenTilankuvausHashit) = {
      val valintatapajonot = sijoitteluajoId.map(repository.getHakukohteenHakemuksienHakutoiveenValintatapajonotSijoittelussa(hakukohdeOid, _)).getOrElse(List())
      (valintatapajonot.groupBy(_.hakemusOid).mapValues(_.groupBy(_.hakukohdeOid)), valintatapajonot.map(_.tilankuvausHash).distinct)
    }
    val hakutoiveenTilankuvauksetSijoittelussa = repository.getValinnantilanKuvaukset(hakutoiveenTilankuvausHashit)
    val hakukohteenValinnantulokset: Map[HakemusOid, Set[Valinnantulos]] = timed(s"Getting hakukohteen $hakukohdeOid valinnantulokset") {
      repository.runBlocking(repository.getValinnantuloksetForHakukohde(hakukohdeOid)).groupBy(_.hakemusOid)
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

    hakijat.map(hakija => {
      hakija.kevytDto(
        hakutoiveDtoSijoittelu(hakija.hakemusOid).orElse(hakutoiveDtoEiSijoittelua(hakija.hakemusOid)).toList
      )
    })
  }

  def kevytDtoVainHakukohde(repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository, sijoitteluajo: SijoitteluAjo, hakukohdeOid: HakukohdeOid): List[KevytHakijaDTO] = {
    kevytDtoVainHakukohde(repository, SyntheticSijoitteluAjoForHakusWithoutSijoittelu.getSijoitteluajoId(sijoitteluajo), HakuOid(sijoitteluajo.getHakuOid), hakukohdeOid)
  }

  def kevytDto(repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository, sijoitteluajo: SijoitteluAjo, hakukohdeOid: HakukohdeOid): List[KevytHakijaDTO] = {
    kevytDto(repository, SyntheticSijoitteluAjoForHakusWithoutSijoittelu.getSijoitteluajoId(sijoitteluajo), HakuOid(sijoitteluajo.getHakuOid), hakukohdeOid)
  }

  def dto(repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository, sijoitteluajo: SijoitteluAjo, hakukohdeOid: HakukohdeOid): List[HakijaDTO] = {
    dto(repository, SyntheticSijoitteluAjoForHakusWithoutSijoittelu.getSijoitteluajoId(sijoitteluajo), HakuOid(sijoitteluajo.getHakuOid), hakukohdeOid)
  }

}