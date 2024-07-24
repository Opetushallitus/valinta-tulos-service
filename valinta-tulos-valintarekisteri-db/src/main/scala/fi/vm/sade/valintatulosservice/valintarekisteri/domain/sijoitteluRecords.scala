package fi.vm.sade.valintatulosservice.valintarekisteri.domain

import java.util
import java.util.Date
import fi.vm.sade.sijoittelu.domain.Valintatapajono.JonosijaTieto
import fi.vm.sade.sijoittelu.domain.{HakemuksenTila => _, IlmoittautumisTila => _, _}
import fi.vm.sade.sijoittelu.tulos.dto.{ValintatuloksenTila, _}
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.{HakijaDTO, HakutoiveDTO, HakutoiveenValintatapajonoDTO, KevytHakijaDTO, KevytHakutoiveDTO, KevytHakutoiveenValintatapajonoDTO, HakijaryhmaDTO => HakutoiveenHakijaryhmaDTO}
import org.joda.time.DateTime

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._

case class SijoitteluajoRecord(sijoitteluajoId:Long, hakuOid: HakuOid, startMils:Long, endMils:Long) {
  def dto(hakukohteet:List[HakukohdeDTO]): SijoitteluajoDTO = {
    val sijoitteluajoDTO = new SijoitteluajoDTO
    sijoitteluajoDTO.setSijoitteluajoId(sijoitteluajoId)
    sijoitteluajoDTO.setHakuOid(hakuOid.toString)
    sijoitteluajoDTO.setStartMils(startMils)
    sijoitteluajoDTO.setEndMils(endMils)
    sijoitteluajoDTO.setHakukohteet(hakukohteet.asJava)
    sijoitteluajoDTO
  }

  def entity(hakukohdeOids: List[HakukohdeOid]): SijoitteluAjo = {
    val sijoitteluAjo = new SijoitteluAjo()
    sijoitteluAjo.setSijoitteluajoId(sijoitteluajoId)
    sijoitteluAjo.setHakuOid(hakuOid.toString)
    sijoitteluAjo.setStartMils(startMils)
    sijoitteluAjo.setEndMils(endMils)
    sijoitteluAjo.setHakukohteet(hakukohdeOids.map(oid => {
      val hakukohde = new HakukohdeItem()
      hakukohde.setOid(oid.toString)
      hakukohde
    }).asJava)
    sijoitteluAjo
  }
}

case class SijoitteluSummaryRecord(valintatapajonoOid: ValintatapajonoOid, valintatapajonoNimi: String,
                                   sijoittelunKayttamatAloituspaikat: Int, aloituspaikat: Int,
                                   hyvaksytyt: Int, ehdollisestiVastaanottaneet: Int, paikanVastaanottaneet: Int,
                                   varasijoilla: Int, alinHyvaksyttyPistemaara: Double,
                                   ehdollisestiHyvaksytyt: Int, peruneet: Int, hyvaksyttyHarkinnanvaraisesti: Int) {
}

case class HakijaRecord(hakemusOid: HakemusOid, hakijaOid: String) {
  def dto(hakutoiveet:List[HakutoiveDTO]): HakijaDTO = {
    val hakijaDTO = new HakijaDTO
    hakijaDTO.setHakijaOid(hakijaOid)
    hakijaDTO.setHakemusOid(hakemusOid.toString)
    hakijaDTO.setHakutoiveet(sort(hakutoiveet))
    hakijaDTO
  }

  //Hakijat järjestetään hakemusOidin perusteella KevytHakijaDTOComparator
  def kevytDto(hakutoiveet:List[KevytHakutoiveDTO]): KevytHakijaDTO = {
    val hakijaDTO = new KevytHakijaDTO
    hakijaDTO.setHakijaOid(hakijaOid)
    hakijaDTO.setHakemusOid(hakemusOid.toString)
    hakijaDTO.setHakutoiveet(sort(hakutoiveet))
    hakijaDTO
  }

  def sort[T](hakutoiveet:List[T]): util.TreeSet[T] = {
    val s = new util.TreeSet[T]
    s.addAll(hakutoiveet.asJava)
    s
  }
}

case class HakutoiveRecord(hakemusOid: HakemusOid, hakutoive: Option[Int], hakukohdeOid: HakukohdeOid, kaikkiJonotsijoiteltu: Option[Boolean]) {

  def dto(vastaanottotieto:fi.vm.sade.sijoittelu.domain.ValintatuloksenTila, valintatapajonot:List[HakutoiveenValintatapajonoDTO], hakijaryhmat:List[HakutoiveenHakijaryhmaDTO]): HakutoiveDTO = {
    val hakutoiveDTO = new HakutoiveDTO
    hakutoive.foreach(hakutoiveDTO.setHakutoive(_))
    hakutoiveDTO.setHakukohdeOid(hakukohdeOid.toString)
    hakutoiveDTO.setHakutoiveenValintatapajonot(valintatapajonot.asJava)
    hakutoiveDTO.setHakijaryhmat(hakijaryhmat.asJava)
    hakutoiveDTO.setVastaanottotieto(ValintatuloksenTila.valueOf(vastaanottotieto.toString))
    kaikkiJonotsijoiteltu.foreach(hakutoiveDTO.setKaikkiJonotSijoiteltu)
    hakutoiveDTO
  }

  def kevytDto(valintatapajonot:List[KevytHakutoiveenValintatapajonoDTO]): KevytHakutoiveDTO = {
    val hakutoiveDTO = new KevytHakutoiveDTO
    hakutoive.foreach(hakutoiveDTO.setHakutoive(_))
    hakutoiveDTO.setHakukohdeOid(hakukohdeOid.toString)
    //  TODO hakutoiveDTO.setVastaanottotieto(valintatuloksenTila) ?
    // TODO tarjoajaOid
    hakutoiveDTO.setHakutoiveenValintatapajonot(valintatapajonot.asJava)
    kaikkiJonotsijoiteltu.foreach(hakutoiveDTO.setKaikkiJonotSijoiteltu)
    hakutoiveDTO
  }
}

case class HakutoiveenValintatapajonoRecord(hakemusOid: HakemusOid,
                                            hakukohdeOid: HakukohdeOid,
                                            valintatapajonoPrioriteetti:Int,
                                            valintatapajonoOid: ValintatapajonoOid,
                                            valintatapajonoNimi:String,
                                            eiVarasijatayttoa:Boolean,
                                            jonosija:Int,
                                            varasijanNumero:Option[Int],
                                            hyvaksyttyHarkinnanvaraisesti:Boolean,
                                            tasasijaJonosija:Int,
                                            pisteet:Option[BigDecimal],
                                            alinHyvaksyttyPistemaara:Option[BigDecimal],
                                            varasijat:Option[Int],
                                            varasijaTayttoPaivat:Option[Int],
                                            varasijojaKaytetaanAlkaen:Option[Date],
                                            varasijojaTaytetaanAsti:Option[Date],
                                            tayttojono:Option[String],
                                            tilankuvausHash:Int,
                                            tarkenteenLisatieto:Option[String],
                                            sijoiteltuIlmanVarasijasaantojaNiidenOllessaVoimassa: Boolean) {

  def dto(valinnantulos: Option[Valinnantulos], tilankuvaus: Option[TilankuvausRecord], hakeneet:Int, hyvaksytty:Int): HakutoiveenValintatapajonoDTO = {
    val hakutoiveenValintatapajonoDto = new HakutoiveenValintatapajonoDTO()
    hakutoiveenValintatapajonoDto.setValintatapajonoPrioriteetti(valintatapajonoPrioriteetti)
    hakutoiveenValintatapajonoDto.setValintatapajonoOid(valintatapajonoOid.toString)
    hakutoiveenValintatapajonoDto.setValintatapajonoNimi(valintatapajonoNimi)
    hakutoiveenValintatapajonoDto.setEiVarasijatayttoa(eiVarasijatayttoa)
    hakutoiveenValintatapajonoDto.setJonosija(jonosija)
    //todo paasyJasoveltuvuuskokeentulos
    varasijanNumero.foreach(hakutoiveenValintatapajonoDto.setVarasijanNumero(_))
    hakutoiveenValintatapajonoDto.setHyvaksyttyHarkinnanvaraisesti(hyvaksyttyHarkinnanvaraisesti)
    hakutoiveenValintatapajonoDto.setTasasijaJonosija(tasasijaJonosija)
    pisteet.foreach(p => hakutoiveenValintatapajonoDto.setPisteet(bigDecimal(p)))
    alinHyvaksyttyPistemaara.foreach(p => hakutoiveenValintatapajonoDto.setAlinHyvaksyttyPistemaara(bigDecimal(p)))
    varasijat.foreach(hakutoiveenValintatapajonoDto.setVarasijat(_))
    varasijaTayttoPaivat.foreach(hakutoiveenValintatapajonoDto.setVarasijaTayttoPaivat(_))
    varasijojaKaytetaanAlkaen.foreach(hakutoiveenValintatapajonoDto.setVarasijojaKaytetaanAlkaen)
    varasijojaTaytetaanAsti.foreach(hakutoiveenValintatapajonoDto.setVarasijojaTaytetaanAsti)
    tayttojono.foreach(hakutoiveenValintatapajonoDto.setTayttojono)
    valinnantulos.foreach { v =>
      hakutoiveenValintatapajonoDto.setTila(HakemuksenTila.valueOf(v.valinnantila.valinnantila.name))
      hakutoiveenValintatapajonoDto.setIlmoittautumisTila(IlmoittautumisTila.valueOf(v.ilmoittautumistila.ilmoittautumistila.name))
      v.julkaistavissa.foreach(hakutoiveenValintatapajonoDto.setJulkaistavissa)
      v.ehdollisestiHyvaksyttavissa.foreach(hakutoiveenValintatapajonoDto.setEhdollisestiHyvaksyttavissa)
      v.ehdollisenHyvaksymisenEhtoKoodi.foreach(hakutoiveenValintatapajonoDto.setEhdollisenHyvaksymisenEhtoKoodi)
      v.ehdollisenHyvaksymisenEhtoFI.foreach(hakutoiveenValintatapajonoDto.setEhdollisenHyvaksymisenEhtoFI)
      v.ehdollisenHyvaksymisenEhtoSV.foreach(hakutoiveenValintatapajonoDto.setEhdollisenHyvaksymisenEhtoSV)
      v.ehdollisenHyvaksymisenEhtoEN.foreach(hakutoiveenValintatapajonoDto.setEhdollisenHyvaksymisenEhtoEN)
      v.hyvaksyttyVarasijalta.foreach(hakutoiveenValintatapajonoDto.setHyvaksyttyVarasijalta)
      v.valinnantilanViimeisinMuutos.foreach(odt => hakutoiveenValintatapajonoDto.setHakemuksenTilanViimeisinMuutos(Date.from(odt.toInstant)))
      v.vastaanotonViimeisinMuutos.foreach(odt => hakutoiveenValintatapajonoDto.setValintatuloksenViimeisinMuutos(Date.from(odt.toInstant)))
    }
    hakutoiveenValintatapajonoDto.setTilanKuvaukset(tilankuvaus.map(_.tilankuvaukset(tarkenteenLisatieto)).getOrElse(TilanKuvaukset.tyhja))
    hakutoiveenValintatapajonoDto.setHyvaksytty(hyvaksytty)
    hakutoiveenValintatapajonoDto.setHakeneet(hakeneet)
    hakutoiveenValintatapajonoDto
  }

  def kevytDto(valinnantulos: Option[Valinnantulos], tilankuvaus: Option[TilankuvausRecord]): KevytHakutoiveenValintatapajonoDTO = {
    val hakutoiveenValintatapajonoDto = new KevytHakutoiveenValintatapajonoDTO()
    hakutoiveenValintatapajonoDto.setValintatapajonoOid(valintatapajonoOid.toString)
    varasijojaKaytetaanAlkaen.foreach(hakutoiveenValintatapajonoDto.setVarasijojaKaytetaanAlkaen)
    varasijojaTaytetaanAsti.foreach(hakutoiveenValintatapajonoDto.setVarasijojaTaytetaanAsti)
    varasijanNumero.foreach(hakutoiveenValintatapajonoDto.setVarasijanNumero(_))
    pisteet.foreach(p => hakutoiveenValintatapajonoDto.setPisteet(bigDecimal(p)))
    hakutoiveenValintatapajonoDto.setJonosija(jonosija)
    valinnantulos.foreach{v =>
      hakutoiveenValintatapajonoDto.setTila(HakemuksenTila.valueOf(v.valinnantila.valinnantila.name))
      hakutoiveenValintatapajonoDto.setIlmoittautumisTila(IlmoittautumisTila.valueOf(v.ilmoittautumistila.ilmoittautumistila.name))
      v.valinnantilanViimeisinMuutos.foreach(odt => hakutoiveenValintatapajonoDto.setHakemuksenTilanViimeisinMuutos(Date.from(odt.toInstant)))
      v.julkaistavissa.foreach(hakutoiveenValintatapajonoDto.setJulkaistavissa)
      v.ehdollisestiHyvaksyttavissa.foreach(hakutoiveenValintatapajonoDto.setEhdollisestiHyvaksyttavissa)
      v.ehdollisenHyvaksymisenEhtoKoodi.foreach(hakutoiveenValintatapajonoDto.setEhdollisenHyvaksymisenEhtoKoodi)
      v.ehdollisenHyvaksymisenEhtoFI.foreach(hakutoiveenValintatapajonoDto.setEhdollisenHyvaksymisenEhtoFI)
      v.ehdollisenHyvaksymisenEhtoSV.foreach(hakutoiveenValintatapajonoDto.setEhdollisenHyvaksymisenEhtoSV)
      v.ehdollisenHyvaksymisenEhtoEN.foreach(hakutoiveenValintatapajonoDto.setEhdollisenHyvaksymisenEhtoEN)
      v.hyvaksyttyVarasijalta.foreach(hakutoiveenValintatapajonoDto.setHyvaksyttyVarasijalta)
      v.valinnantilanViimeisinMuutos.foreach(odt => hakutoiveenValintatapajonoDto.setHakemuksenTilanViimeisinMuutos(Date.from(odt.toInstant)))
    }
    hakutoiveenValintatapajonoDto.setTilanKuvaukset(tilankuvaus.map(_.tilankuvaukset(tarkenteenLisatieto)).getOrElse(TilanKuvaukset.tyhja))
    hakutoiveenValintatapajonoDto.setHyvaksyttyHarkinnanvaraisesti(hyvaksyttyHarkinnanvaraisesti)
    hakutoiveenValintatapajonoDto.setValintatapajonoOid(valintatapajonoOid.toString)
    hakutoiveenValintatapajonoDto.setPrioriteetti(valintatapajonoPrioriteetti)
    hakutoiveenValintatapajonoDto
  }

  def bigDecimal(bigDecimal:BigDecimal): java.math.BigDecimal = bigDecimal match {
    case i: BigDecimal => i.bigDecimal
    case _ => null
  }
}

object HakutoiveenValintatapajonoRecord {
  def dto(valinnantulos: Valinnantulos, hakeneet:Int, hyvaksytty:Int): HakutoiveenValintatapajonoDTO = {
    val hakutoiveenValintatapajonoDto = new HakutoiveenValintatapajonoDTO()
    hakutoiveenValintatapajonoDto.setValintatapajonoOid(valinnantulos.valintatapajonoOid.toString)
    hakutoiveenValintatapajonoDto.setTila(HakemuksenTila.valueOf(valinnantulos.valinnantila.valinnantila.name))
    hakutoiveenValintatapajonoDto.setIlmoittautumisTila(IlmoittautumisTila.valueOf(valinnantulos.ilmoittautumistila.ilmoittautumistila.name))
    valinnantulos.julkaistavissa.foreach(hakutoiveenValintatapajonoDto.setJulkaistavissa)
    valinnantulos.ehdollisestiHyvaksyttavissa.foreach(hakutoiveenValintatapajonoDto.setEhdollisestiHyvaksyttavissa)
    valinnantulos.ehdollisenHyvaksymisenEhtoKoodi.foreach(hakutoiveenValintatapajonoDto.setEhdollisenHyvaksymisenEhtoKoodi)
    valinnantulos.ehdollisenHyvaksymisenEhtoFI.foreach(hakutoiveenValintatapajonoDto.setEhdollisenHyvaksymisenEhtoFI)
    valinnantulos.ehdollisenHyvaksymisenEhtoSV.foreach(hakutoiveenValintatapajonoDto.setEhdollisenHyvaksymisenEhtoSV)
    valinnantulos.ehdollisenHyvaksymisenEhtoEN.foreach(hakutoiveenValintatapajonoDto.setEhdollisenHyvaksymisenEhtoEN)
    setTilankuvaukset(valinnantulos, hakutoiveenValintatapajonoDto)
    valinnantulos.hyvaksyttyVarasijalta.foreach(hakutoiveenValintatapajonoDto.setHyvaksyttyVarasijalta)
    valinnantulos.valinnantilanViimeisinMuutos.foreach(odt => hakutoiveenValintatapajonoDto.setHakemuksenTilanViimeisinMuutos(Date.from(odt.toInstant)))
    valinnantulos.vastaanotonViimeisinMuutos.foreach(odt => hakutoiveenValintatapajonoDto.setValintatuloksenViimeisinMuutos(Date.from(odt.toInstant)))
    hakutoiveenValintatapajonoDto.setHyvaksytty(hyvaksytty)
    hakutoiveenValintatapajonoDto.setHakeneet(hakeneet)
    hakutoiveenValintatapajonoDto
  }

  def kevytDto(valinnantulos: Valinnantulos): KevytHakutoiveenValintatapajonoDTO = {
    val hakutoiveenValintatapajonoDto = new KevytHakutoiveenValintatapajonoDTO()
    hakutoiveenValintatapajonoDto.setValintatapajonoOid(valinnantulos.valintatapajonoOid.toString)
    hakutoiveenValintatapajonoDto.setTila(HakemuksenTila.valueOf(valinnantulos.valinnantila.valinnantila.name))
    hakutoiveenValintatapajonoDto.setIlmoittautumisTila(IlmoittautumisTila.valueOf(valinnantulos.ilmoittautumistila.ilmoittautumistila.name))
    valinnantulos.julkaistavissa.foreach(hakutoiveenValintatapajonoDto.setJulkaistavissa)
    valinnantulos.ehdollisestiHyvaksyttavissa.foreach(hakutoiveenValintatapajonoDto.setEhdollisestiHyvaksyttavissa)
    valinnantulos.ehdollisenHyvaksymisenEhtoKoodi.foreach(hakutoiveenValintatapajonoDto.setEhdollisenHyvaksymisenEhtoKoodi)
    valinnantulos.ehdollisenHyvaksymisenEhtoFI.foreach(hakutoiveenValintatapajonoDto.setEhdollisenHyvaksymisenEhtoFI)
    valinnantulos.ehdollisenHyvaksymisenEhtoSV.foreach(hakutoiveenValintatapajonoDto.setEhdollisenHyvaksymisenEhtoSV)
    valinnantulos.ehdollisenHyvaksymisenEhtoEN.foreach(hakutoiveenValintatapajonoDto.setEhdollisenHyvaksymisenEhtoEN)
    setTilankuvaukset(valinnantulos, hakutoiveenValintatapajonoDto)
    valinnantulos.hyvaksyttyVarasijalta.foreach(hakutoiveenValintatapajonoDto.setHyvaksyttyVarasijalta)
    valinnantulos.valinnantilanViimeisinMuutos.foreach(odt => hakutoiveenValintatapajonoDto.setHakemuksenTilanViimeisinMuutos(Date.from(odt.toInstant)))
    valinnantulos.vastaanotonViimeisinMuutos.foreach(odt => hakutoiveenValintatapajonoDto.setValintatuloksenViimeisinMuutos(Date.from(odt.toInstant)))
    hakutoiveenValintatapajonoDto
  }

  private def setTilankuvaukset(valinnantulos: Valinnantulos, hakutoiveenValintatapajonoDto: HakutoiveenValintatapajonoDTO): Unit = {
    val tilankuvaukset = new java.util.HashMap[String, String]
    valinnantulos.valinnantilanKuvauksenTekstiFI.foreach(tilankuvaukset.put("FI", _))
    valinnantulos.valinnantilanKuvauksenTekstiSV.foreach(tilankuvaukset.put("SV", _))
    valinnantulos.valinnantilanKuvauksenTekstiEN.foreach(tilankuvaukset.put("EN", _))
    hakutoiveenValintatapajonoDto.setTilanKuvaukset(tilankuvaukset)
  }

  private def setTilankuvaukset(valinnantulos: Valinnantulos, hakutoiveenValintatapajonoDto: KevytHakutoiveenValintatapajonoDTO): Unit = {
    val tilankuvaukset = new java.util.HashMap[String, String]
    valinnantulos.valinnantilanKuvauksenTekstiFI.foreach(tilankuvaukset.put("FI", _))
    valinnantulos.valinnantilanKuvauksenTekstiSV.foreach(tilankuvaukset.put("SV", _))
    valinnantulos.valinnantilanKuvauksenTekstiEN.foreach(tilankuvaukset.put("EN", _))
    hakutoiveenValintatapajonoDto.setTilanKuvaukset(tilankuvaukset)
  }
}

case class HakutoiveenHakijaryhmaRecord(oid:String, nimi:String, hakukohdeOid: HakukohdeOid, valintatapajonoOid: Option[ValintatapajonoOid], kiintio:Int,
                                        hyvaksyttyHakijaryhmasta:Boolean, hakijaryhmaTyyppikoodiUri:Option[String]) {
  def dto: HakutoiveenHakijaryhmaDTO = {
    val hakutoiveenHakijaryhmaDTO = new HakutoiveenHakijaryhmaDTO()
    hakutoiveenHakijaryhmaDTO.setOid(oid)
    hakutoiveenHakijaryhmaDTO.setNimi(nimi)
    valintatapajonoOid.map(_.toString).foreach(hakutoiveenHakijaryhmaDTO.setValintatapajonoOid)
    hakutoiveenHakijaryhmaDTO.setKiintio(kiintio)
    hakutoiveenHakijaryhmaDTO.setHyvaksyttyHakijaryhmasta(hyvaksyttyHakijaryhmasta)
    hakijaryhmaTyyppikoodiUri.foreach(hakutoiveenHakijaryhmaDTO.setHakijaryhmatyyppikoodiUri)
    hakutoiveenHakijaryhmaDTO
  }
}

case class JononAlimmatPisteet(valintatapajonoOid: ValintatapajonoOid,
                               hakukohdeOid: HakukohdeOid,
                               alinHyvaksyttyPistemaara: Double,
                               sijoitteluajoId: Long)

case class SijoittelunHakukohdeRecord(sijoitteluajoId: Long, oid: HakukohdeOid, kaikkiJonotsijoiteltu: Boolean) {

  def dto(valintatapajonot:List[ValintatapajonoDTO], hakijaryhmat:List[HakijaryhmaDTO]): HakukohdeDTO = {

    val hakukohdeDTO = new HakukohdeDTO
    hakukohdeDTO.setSijoitteluajoId(sijoitteluajoId)
    hakukohdeDTO.setOid(oid.toString)
    hakukohdeDTO.setKaikkiJonotSijoiteltu(kaikkiJonotsijoiteltu)
    hakukohdeDTO.setValintatapajonot(valintatapajonot.asJava)
    hakukohdeDTO.setHakijaryhmat(hakijaryhmat.asJava)
    hakukohdeDTO
  }

  def entity(valintatapajonot:List[Valintatapajono], hakijaryhmat:List[Hakijaryhma]): Hakukohde = {

    val hakukohde = new Hakukohde
    hakukohde.setSijoitteluajoId(sijoitteluajoId)
    hakukohde.setOid(oid.toString)
    hakukohde.setKaikkiJonotSijoiteltu(kaikkiJonotsijoiteltu)
    hakukohde.setValintatapajonot(valintatapajonot.asJava)
    hakukohde.setHakijaryhmat(hakijaryhmat.asJava)
    hakukohde
  }
}

object ErillishaunHakukohdeRecord {
  def entity(hakukohdeOid: HakukohdeOid, valintatapajonot:List[Valintatapajono]): Hakukohde = {
    val hakukohde = new Hakukohde
    hakukohde.setOid(hakukohdeOid.toString)
    hakukohde.setValintatapajonot(valintatapajonot.asJava)
    hakukohde
  }
}

case class ValintatapajonoRecord(tasasijasaanto:String, oid: ValintatapajonoOid, nimi:String, prioriteetti:Int, aloituspaikat:Option[Int],
                                 alkuperaisetAloituspaikat:Option[Int], alinHyvaksyttyPistemaara:BigDecimal,
                                 eiVarasijatayttoa:Boolean, kaikkiEhdonTayttavatHyvaksytaan:Boolean,
                                 poissaOlevaTaytto:Boolean, valintaesitysHyvaksytty:Option[Boolean], hakeneet:Int,
                                 varasijat:Option[Int], varasijanTayttoPaivat:Option[Int],
                                 varasijojaKaytetaanAlkaen:Option[Date], varasijojaKaytetaanAsti:Option[Date],
                                 tayttoJono:Option[String], sijoiteltuIlmanVarasijasaantojaNiidenOllessaVoimassa: Boolean,
                                 hakukohdeOid: HakukohdeOid,
                                 sivssnovSijoittelunVarasijataytonRajoitus: Option[JonosijaTieto] = None) {

  def bigDecimal(bigDecimal:BigDecimal): java.math.BigDecimal = bigDecimal match {
    case i: BigDecimal => i.bigDecimal
    case _ => null
  }

  def dto(hakemukset: List[HakemusDTO]): ValintatapajonoDTO = {
    val valintatapajonoDTO = new ValintatapajonoDTO
    valintatapajonoDTO.setTasasijasaanto(fi.vm.sade.sijoittelu.tulos.dto.Tasasijasaanto.valueOf(tasasijasaanto.toUpperCase()))
    valintatapajonoDTO.setOid(oid.toString)
    valintatapajonoDTO.setNimi(nimi)
    valintatapajonoDTO.setPrioriteetti(prioriteetti)
    valintatapajonoDTO.setAloituspaikat(aloituspaikat.get)
    alkuperaisetAloituspaikat.foreach(valintatapajonoDTO.setAlkuperaisetAloituspaikat(_))
    valintatapajonoDTO.setAlinHyvaksyttyPistemaara(bigDecimal(alinHyvaksyttyPistemaara))
    valintatapajonoDTO.setEiVarasijatayttoa(eiVarasijatayttoa)
    valintatapajonoDTO.setKaikkiEhdonTayttavatHyvaksytaan(kaikkiEhdonTayttavatHyvaksytaan)
    valintatapajonoDTO.setPoissaOlevaTaytto(poissaOlevaTaytto)
    valintaesitysHyvaksytty.foreach(valintatapajonoDTO.setValintaesitysHyvaksytty(_))
    varasijat.foreach(valintatapajonoDTO.setVarasijat(_))
    varasijanTayttoPaivat.foreach(valintatapajonoDTO.setVarasijaTayttoPaivat(_))
    varasijojaKaytetaanAlkaen.foreach(valintatapajonoDTO.setVarasijojaKaytetaanAlkaen)
    varasijojaKaytetaanAsti.foreach(valintatapajonoDTO.setVarasijojaTaytetaanAsti)
    tayttoJono.foreach(valintatapajonoDTO.setTayttojono)
    valintatapajonoDTO.setHakemukset(hakemukset.asJava)
    valintatapajonoDTO.setHakeneet(hakemukset.size)
    valintatapajonoDTO
  }

  def entity(hakemukset: List[Hakemus]): Valintatapajono = {
    val valintatapajono = new Valintatapajono
    valintatapajono.setTasasijasaanto(fi.vm.sade.sijoittelu.domain.Tasasijasaanto.valueOf(tasasijasaanto.toUpperCase()))
    valintatapajono.setOid(oid.toString)
    valintatapajono.setNimi(nimi)
    valintatapajono.setPrioriteetti(prioriteetti)
    valintatapajono.setAloituspaikat(aloituspaikat.get)
    alkuperaisetAloituspaikat.foreach(valintatapajono.setAlkuperaisetAloituspaikat(_))
    valintatapajono.setAlinHyvaksyttyPistemaara(bigDecimal(alinHyvaksyttyPistemaara))
    valintatapajono.setEiVarasijatayttoa(eiVarasijatayttoa)
    valintatapajono.setKaikkiEhdonTayttavatHyvaksytaan(kaikkiEhdonTayttavatHyvaksytaan)
    valintatapajono.setPoissaOlevaTaytto(poissaOlevaTaytto)
    valintatapajono.setSijoiteltuIlmanVarasijasaantojaNiidenOllessaVoimassa(sijoiteltuIlmanVarasijasaantojaNiidenOllessaVoimassa)
    valintaesitysHyvaksytty.foreach(valintatapajono.setValintaesitysHyvaksytty(_))
    //valintatapajono.setHyvaksytty(hyvaksytty)
    //valintatapajono.setVaralla(varalla)
    varasijat.foreach(valintatapajono.setVarasijat(_))
    varasijanTayttoPaivat.foreach(valintatapajono.setVarasijaTayttoPaivat(_))
    varasijojaKaytetaanAlkaen.foreach(valintatapajono.setVarasijojaKaytetaanAlkaen)
    varasijojaKaytetaanAsti.foreach(valintatapajono.setVarasijojaTaytetaanAsti)
    tayttoJono.foreach(valintatapajono.setTayttojono)
    valintatapajono.setHakemukset(hakemukset.asJava)
    valintatapajono.setHakemustenMaara(hakemukset.size)
    valintatapajono.setSivssnovSijoittelunVarasijataytonRajoitus(sivssnovSijoittelunVarasijataytonRajoitus.asJava)
    valintatapajono
  }
}

object ValintatapajonoRecord {
  def entity(valintatapajonoOid: ValintatapajonoOid, hakemukset: List[Hakemus]): Valintatapajono = {
    val valintatapajono = new Valintatapajono
    valintatapajono.setOid(valintatapajonoOid.toString)
    //valintatapajono.setHyvaksytty(hyvaksytty)
    //valintatapajono.setVaralla(varalla)
    valintatapajono.setHakemukset(hakemukset.asJava)
    valintatapajono.setHakemustenMaara(hakemukset.size)
    valintatapajono
  }
}

case class HakemusRecord(hakijaOid:Option[String], hakemusOid: HakemusOid, pisteet:Option[BigDecimal],
                         prioriteetti:Int, jonosija:Int, tasasijaJonosija:Int, tila:Valinnantila, tilankuvausHash:Int,
                         tarkenteenLisatieto:Option[String], hyvaksyttyHarkinnanvaraisesti:Boolean, varasijaNumero:Option[Int],
                         onkoMuuttunutviimesijoittelusta:Boolean, siirtynytToisestaValintatapaJonosta:Boolean, valintatapajonoOid: ValintatapajonoOid) {

  def dto(hakijaryhmaOids:Set[String],
          tilankuvaus: Option[TilankuvausRecord],
          tilahistoria:List[TilaHistoriaDTO]): HakemusDTO = {

    val hakemusDTO = new HakemusDTO
    hakijaOid.foreach(hakemusDTO.setHakijaOid)
    hakemusDTO.setHakemusOid(hakemusOid.toString)
    pisteet.foreach(p => hakemusDTO.setPisteet(p.bigDecimal))
    hakemusDTO.setPrioriteetti(prioriteetti)
    hakemusDTO.setJonosija(jonosija)
    hakemusDTO.setTasasijaJonosija(tasasijaJonosija)
    hakemusDTO.setTila(HakemuksenTila.valueOf(tila.valinnantila.name))
    hakemusDTO.setTilanKuvaukset(tilankuvaus.map(_.tilankuvaukset(tarkenteenLisatieto)).getOrElse(TilanKuvaukset.tyhja))
    hakemusDTO.setHyvaksyttyHarkinnanvaraisesti(hyvaksyttyHarkinnanvaraisesti)
    varasijaNumero.foreach(hakemusDTO.setVarasijanNumero(_))
    hakemusDTO.setOnkoMuuttunutViimeSijoittelussa(onkoMuuttunutviimesijoittelusta)
    hakemusDTO.setHyvaksyttyHakijaryhmista(hakijaryhmaOids.asJava)
    hakemusDTO.setSiirtynytToisestaValintatapajonosta(siirtynytToisestaValintatapaJonosta)
    hakemusDTO.setValintatapajonoOid(valintatapajonoOid.toString)
    hakemusDTO.setTilaHistoria(tilahistoria.asJava)
    hakemusDTO
  }

  def entity(hakijaryhmaOids:Set[String],
             tilankuvaus: Option[TilankuvausRecord],
             tilahistoria:List[TilaHistoria],
             vastaanottoDeadline: Option[DateTime]): Hakemus = {

    val isLate: Boolean = vastaanottoDeadline.exists(new DateTime().isAfter)
    val hakemus = new Hakemus
    hakijaOid.foreach(hakemus.setHakijaOid)
    hakemus.setHakemusOid(hakemusOid.toString)
    pisteet.foreach(p => hakemus.setPisteet(p.bigDecimal))
    hakemus.setPrioriteetti(prioriteetti)
    hakemus.setJonosija(jonosija)
    hakemus.setTasasijaJonosija(tasasijaJonosija)
    hakemus.setTila(fi.vm.sade.sijoittelu.domain.HakemuksenTila.valueOf(tila.valinnantila.name))
    hakemus.setTilankuvauksenTarkenne(
      tilankuvaus.map(_.tilankuvauksenTarkenne.tilankuvauksenTarkenne).getOrElse(TilankuvauksenTarkenne.EI_TILANKUVAUKSEN_TARKENNETTA),
      tilankuvaus.map(_.tilankuvaukset(tarkenteenLisatieto)).getOrElse(TilanKuvaukset.tyhja)
    )
    hakemus.setHyvaksyttyHarkinnanvaraisesti(hyvaksyttyHarkinnanvaraisesti)
    varasijaNumero.foreach(hakemus.setVarasijanNumero(_))
    hakemus.setOnkoMuuttunutViimeSijoittelussa(onkoMuuttunutviimesijoittelusta)
    hakemus.setHyvaksyttyHakijaryhmista(hakijaryhmaOids.asJava)
    hakemus.setSiirtynytToisestaValintatapajonosta(siirtynytToisestaValintatapaJonosta)
    //hakemus.setValintatapajonoOid(valintatapajonoOid)
    hakemus.setTilaHistoria(tilahistoria.asJava)
    hakemus.setVastaanottoMyohassa(isLate)
    hakemus.setVastaanottoDeadline(vastaanottoDeadline.map(_.toDate).orNull)
    hakemus
  }
}

object HakemusRecord {
  def entity(valinnantulos: Valinnantulos): Hakemus = {
    val hakemus = new Hakemus
    hakemus.setHakijaOid(valinnantulos.henkiloOid)
    hakemus.setHakemusOid(valinnantulos.hakemusOid.toString)
    hakemus.setTila(fi.vm.sade.sijoittelu.domain.HakemuksenTila.valueOf(valinnantulos.valinnantila.valinnantila.name))
    hakemus.setJonosija(1) //TODO
    hakemus.setTasasijaJonosija(1) //TODO
    //hakemus.setTilanKuvaukset(tilankuvaukset.asJava) Erillishaulla ei tilankuvauksia
    //hakemus.setTilaHistoria(tilahistoria.asJava)
    hakemus
  }
}

case class TilaHistoriaRecord(valintatapajonoOid: ValintatapajonoOid, hakemusOid: HakemusOid, tila:Valinnantila, luotu:Date) {
  def dto: TilaHistoriaDTO = {
    val tilaDTO = new TilaHistoriaDTO
    tilaDTO.setLuotu(luotu)
    tilaDTO.setTila(tila.valinnantila.toString)
    tilaDTO
  }

  def entity: TilaHistoria = {
    val tilahistoria = new TilaHistoria
    tilahistoria.setLuotu(luotu)
    tilahistoria.setTila(tila.valinnantila)
    tilahistoria
  }
}

case class HakijaryhmaRecord(prioriteetti:Int, oid:String, nimi:String, hakukohdeOid: Option[HakukohdeOid], kiintio:Int,
                             kaytaKaikki:Boolean, sijoitteluajoId:Long, tarkkaKiintio:Boolean, kaytetaanRyhmaanKuuluvia:Boolean,
                             valintatapajonoOid: Option[ValintatapajonoOid], hakijaryhmatyyppikoodiUri:String) {

  def dto(hakemusOid: List[HakemusOid]): HakijaryhmaDTO = {
    val hakijaryhmaDTO = new HakijaryhmaDTO
    hakijaryhmaDTO.setPrioriteetti(prioriteetti)
    hakijaryhmaDTO.setOid(oid)
    hakijaryhmaDTO.setNimi(nimi)
    hakukohdeOid.map(_.toString).foreach(hakijaryhmaDTO.setHakukohdeOid)
    hakijaryhmaDTO.setKiintio(kiintio)
    hakijaryhmaDTO.setKaytaKaikki(kaytaKaikki)
    hakijaryhmaDTO.setTarkkaKiintio(tarkkaKiintio)
    hakijaryhmaDTO.setKaytetaanRyhmaanKuuluvia(kaytetaanRyhmaanKuuluvia)
    valintatapajonoOid.map(_.toString).foreach(hakijaryhmaDTO.setValintatapajonoOid)
    hakijaryhmaDTO.setHakijaryhmatyyppikoodiUri(hakijaryhmatyyppikoodiUri)
    hakijaryhmaDTO.setHakemusOid(hakemusOid.map(_.toString).asJava)
    hakijaryhmaDTO
  }

  def entity(hakemusOid: List[HakemusOid]): Hakijaryhma = {
    val hakijaryhma = new Hakijaryhma
    hakijaryhma.setPrioriteetti(prioriteetti)
    hakijaryhma.setOid(oid)
    hakijaryhma.setNimi(nimi)
    hakukohdeOid.map(_.toString).foreach(hakijaryhma.setHakukohdeOid)
    hakijaryhma.setKiintio(kiintio)
    hakijaryhma.setKaytaKaikki(kaytaKaikki)
    hakijaryhma.setTarkkaKiintio(tarkkaKiintio)
    hakijaryhma.setKaytetaanRyhmaanKuuluvia(kaytetaanRyhmaanKuuluvia)
    valintatapajonoOid.map(_.toString).foreach(hakijaryhma.setValintatapajonoOid)
    hakijaryhma.setHakijaryhmatyyppikoodiUri(hakijaryhmatyyppikoodiUri)
    hakijaryhma.getHakemusOid.addAll(hakemusOid.map(_.toString).asJava)
    hakijaryhma
  }
}

case class TilankuvausRecord(hash:Int, tilankuvauksenTarkenne:ValinnantilanTarkenne, textFi:Option[String],
                             textSv:Option[String], textEn:Option[String]) {
  private def replaceLisatieto(text: String, tarkenteenLisatieto: Option[String]): String =
    tarkenteenLisatieto.map(text.replace("<lisatieto>", _)).getOrElse(text)

  def tilankuvaukset(tarkenteenLisatieto: Option[String]): util.Map[String,String] = {
    if (textFi.isEmpty && textSv.isEmpty && textEn.isEmpty) {
      TilanKuvaukset.tyhja
    } else {
      val m = new util.HashMap[String, String]()
      textFi.map(replaceLisatieto(_, tarkenteenLisatieto)).foreach(m.put("FI", _))
      textSv.map(replaceLisatieto(_, tarkenteenLisatieto)).foreach(m.put("SV", _))
      textEn.map(replaceLisatieto(_, tarkenteenLisatieto)).foreach(m.put("EN", _))
      m
    }
  }
}
