package fi.vm.sade.valintatulosservice.valintarekisteri.domain

import java.util
import java.util.{Comparator, Date}

import fi.vm.sade.sijoittelu.tulos.dto._
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.{HakijaDTO, HakutoiveDTO, HakutoiveenValintatapajonoDTO}

import scala.collection.JavaConverters._

case class SijoitteluajoRecord(sijoitteluajoId:Long, hakuOid:String, startMils:Long, endMils:Long) {
  def dto(hakukohteet:List[HakukohdeDTO]) = {
    val sijoitteluajoDTO = new SijoitteluajoDTO
    sijoitteluajoDTO.setSijoitteluajoId(sijoitteluajoId)
    sijoitteluajoDTO.setHakuOid(hakuOid)
    sijoitteluajoDTO.setStartMils(startMils)
    sijoitteluajoDTO.setEndMils(endMils)
    sijoitteluajoDTO.setHakukohteet(hakukohteet.asJava)
    sijoitteluajoDTO
  }
}

case class HakijaRecord(etunimi:String, sukunimi:String, hakemusOid:String, hakijaOid:String) {
  def dto(hakutoiveet:List[HakutoiveDTO]) = {
    val hakijaDTO = new HakijaDTO
    hakijaDTO.setHakijaOid(hakijaOid)
    hakijaDTO.setHakemusOid(hakemusOid)
    hakijaDTO.setEtunimi(etunimi)
    hakijaDTO.setSukunimi(sukunimi)
    hakijaDTO.setHakutoiveet(sortHakutoiveet(hakutoiveet))
    hakijaDTO
  }

  def sortHakutoiveet(hakutoiveet:List[HakutoiveDTO]) = {
    val sortedJavaHakutoiveSet = new util.TreeSet[HakutoiveDTO](new Comparator[HakutoiveDTO] {
      override def compare(o1: HakutoiveDTO, o2: HakutoiveDTO): Int = o1.getHakutoive.compareTo(o2.getHakutoive)
    })
    sortedJavaHakutoiveSet.addAll(hakutoiveet.asJava)
    sortedJavaHakutoiveSet
  }
}

case class HakutoiveRecord(hakemusOid: String, hakutoive: Int, hakukohdeOid: String, valintatuloksenTila: String, kaikkiJonotsijoiteltu: Boolean) {

  def dto(valintatapajonot:List[HakutoiveenValintatapajonoDTO], pistetiedot:List[PistetietoDTO]) = {
    val hakutoiveDTO = new HakutoiveDTO
    hakutoiveDTO.setHakutoive(hakutoive)
    hakutoiveDTO.setHakukohdeOid(hakukohdeOid)
    //  TODO hakutoiveDTO.setVastaanottotieto(valintatuloksenTila) ?
    hakutoiveDTO.setPistetiedot(pistetiedot.asJava)
    hakutoiveDTO.setHakutoiveenValintatapajonot(valintatapajonot.asJava)
    hakutoiveDTO
  }
}

case class PistetietoRecord(valintatapajonoOid:String, hakemusOid:String, tunniste:String,
                            arvo:String, laskennallinenArvo:String, osallistuminen:String) {
  def dto = {
    val pistetietoDTO = new PistetietoDTO
    pistetietoDTO.setArvo(arvo)
    pistetietoDTO.setLaskennallinenArvo(laskennallinenArvo)
    pistetietoDTO.setOsallistuminen(osallistuminen)
    pistetietoDTO.setTunniste(tunniste)
    pistetietoDTO
  }
}

case class SijoittelunHakukohdeRecord(sijoitteluajoId: Long, oid: String, kaikkiJonotsijoiteltu: Boolean) {

  def dto(valintatapajonot:List[ValintatapajonoDTO], hakijaryhmat:List[HakijaryhmaDTO]) = {

    val hakukohdeDTO = new HakukohdeDTO
    hakukohdeDTO.setSijoitteluajoId(sijoitteluajoId)
    hakukohdeDTO.setOid(oid)
    hakukohdeDTO.setKaikkiJonotSijoiteltu(kaikkiJonotsijoiteltu)
    hakukohdeDTO.setValintatapajonot(valintatapajonot.asJava)
    hakukohdeDTO.setHakijaryhmat(hakijaryhmat.asJava)
    hakukohdeDTO
  }
}

case class ValintatapajonoRecord(tasasijasaanto:String, oid:String, nimi:String, prioriteetti:Int, aloituspaikat:Option[Int],
                                 alkuperaisetAloituspaikat:Option[Int], alinHyvaksyttyPistemaara:BigDecimal,
                                 eiVarasijatayttoa:Boolean, kaikkiEhdonTayttavatHyvaksytaan:Boolean,
                                 poissaOlevaTaytto:Boolean, valintaesitysHyvaksytty:Option[Boolean], hakeneet:Int,
                                 hyvaksytty:Int, varalla:Int, varasijat:Option[Int], varasijanTayttoPaivat:Option[Int],
                                 varasijojaKaytetaanAlkaen:Option[java.sql.Date], varasijojaKaytetaanAsti:Option[java.sql.Date],
                                 tayttoJono:Option[String], hakukohdeOid:String) {

  def bigDecimal(bigDecimal:BigDecimal): java.math.BigDecimal = bigDecimal match {
    case i: BigDecimal => i.bigDecimal
    case _ => null
  }

  def dto(hakemukset: List[HakemusDTO]) = {
    val valintatapajonoDTO = new ValintatapajonoDTO
    valintatapajonoDTO.setTasasijasaanto(fi.vm.sade.sijoittelu.tulos.dto.Tasasijasaanto.valueOf(tasasijasaanto.toUpperCase()))
    valintatapajonoDTO.setOid(oid)
    valintatapajonoDTO.setNimi(nimi)
    valintatapajonoDTO.setPrioriteetti(prioriteetti)
    valintatapajonoDTO.setAloituspaikat(aloituspaikat.get)
    alkuperaisetAloituspaikat.foreach(valintatapajonoDTO.setAlkuperaisetAloituspaikat(_))
    valintatapajonoDTO.setAlinHyvaksyttyPistemaara(bigDecimal(alinHyvaksyttyPistemaara))
    valintatapajonoDTO.setEiVarasijatayttoa(eiVarasijatayttoa)
    valintatapajonoDTO.setKaikkiEhdonTayttavatHyvaksytaan(kaikkiEhdonTayttavatHyvaksytaan)
    valintatapajonoDTO.setPoissaOlevaTaytto(poissaOlevaTaytto)
    valintaesitysHyvaksytty.foreach(valintatapajonoDTO.setValintaesitysHyvaksytty(_))
    valintatapajonoDTO.setHyvaksytty(hyvaksytty)
    valintatapajonoDTO.setVaralla(varalla)
    varasijat.foreach(valintatapajonoDTO.setVarasijat(_))
    varasijanTayttoPaivat.foreach(valintatapajonoDTO.setVarasijaTayttoPaivat(_))
    varasijojaKaytetaanAlkaen.foreach(valintatapajonoDTO.setVarasijojaKaytetaanAlkaen)
    varasijojaKaytetaanAsti.foreach(valintatapajonoDTO.setVarasijojaTaytetaanAsti)
    tayttoJono.foreach(valintatapajonoDTO.setTayttojono)
    valintatapajonoDTO.setHakemukset(hakemukset.asJava)
    valintatapajonoDTO.setHakeneet(hakemukset.size)
    valintatapajonoDTO
  }

  //TODO: Does not populate hakemus specific fields
  def hakutoiveenDto() = {
    val hakutoiveenValintatapajonoDto = new HakutoiveenValintatapajonoDTO()
    if (alinHyvaksyttyPistemaara != null) {
      hakutoiveenValintatapajonoDto.setAlinHyvaksyttyPistemaara(alinHyvaksyttyPistemaara.bigDecimal)
    }
    hakutoiveenValintatapajonoDto.setEiVarasijatayttoa(eiVarasijatayttoa)
    hakutoiveenValintatapajonoDto.setHakeneet(hakeneet)
    hakutoiveenValintatapajonoDto.setHyvaksytty(hyvaksytty)
    tayttoJono.foreach(hakutoiveenValintatapajonoDto.setTayttojono)
    hakutoiveenValintatapajonoDto.setValintatapajonoNimi(nimi)
    hakutoiveenValintatapajonoDto.setValintatapajonoOid(oid)
    hakutoiveenValintatapajonoDto.setValintatapajonoPrioriteetti(prioriteetti)
    hakutoiveenValintatapajonoDto.setVaralla(varalla)
    varasijat.foreach(hakutoiveenValintatapajonoDto.setVarasijat(_))
    varasijanTayttoPaivat.foreach(hakutoiveenValintatapajonoDto.setVarasijaTayttoPaivat(_))
    varasijojaKaytetaanAlkaen.foreach(hakutoiveenValintatapajonoDto.setVarasijojaKaytetaanAlkaen)
    varasijojaKaytetaanAsti.foreach(hakutoiveenValintatapajonoDto.setVarasijojaTaytetaanAsti)
    hakutoiveenValintatapajonoDto
  }
}

case class HakemusRecord(hakijaOid:Option[String], hakemusOid:String, pisteet:Option[BigDecimal], etunimi:Option[String], sukunimi:Option[String],
                         prioriteetti:Int, jonosija:Int, tasasijaJonosija:Int, tila:Valinnantila, tilankuvausHash:Int,
                         tarkenteenLisatieto:Option[String], hyvaksyttyHarkinnanvaraisesti:Boolean, varasijaNumero:Option[Int],
                         onkoMuuttunutviimesijoittelusta:Boolean, siirtynytToisestaValintatapaJonosta:Boolean, valintatapajonoOid:String) {

  def dto(hakijaryhmaOids:Set[String],
          tilankuvaukset:Map[String,String],
          tilahistoria:List[TilaHistoriaDTO],
          pistetiedot:List[PistetietoDTO]) = {

    val hakemusDTO = new HakemusDTO
    hakijaOid.foreach(hakemusDTO.setHakijaOid)
    hakemusDTO.setHakemusOid(hakemusOid)
    pisteet.foreach(p => hakemusDTO.setPisteet(p.bigDecimal))
    etunimi.foreach(hakemusDTO.setEtunimi)
    sukunimi.foreach(hakemusDTO.setSukunimi)
    hakemusDTO.setPrioriteetti(prioriteetti)
    hakemusDTO.setJonosija(jonosija)
    hakemusDTO.setTasasijaJonosija(tasasijaJonosija)
    hakemusDTO.setTila(HakemuksenTila.valueOf(tila.valinnantila.name))
    hakemusDTO.setTilanKuvaukset(tilankuvaukset.asJava)
    hakemusDTO.setHyvaksyttyHarkinnanvaraisesti(hyvaksyttyHarkinnanvaraisesti)
    varasijaNumero.foreach(hakemusDTO.setVarasijanNumero(_))
    hakemusDTO.setOnkoMuuttunutViimeSijoittelussa(onkoMuuttunutviimesijoittelusta)
    hakemusDTO.setHyvaksyttyHakijaryhmista(hakijaryhmaOids.asJava)
    hakemusDTO.setSiirtynytToisestaValintatapajonosta(siirtynytToisestaValintatapaJonosta)
    hakemusDTO.setValintatapajonoOid(valintatapajonoOid)
    hakemusDTO.setTilaHistoria(tilahistoria.asJava)
    hakemusDTO.getPistetiedot.addAll(pistetiedot.asJava)
    hakemusDTO
  }

  def tilankuvaukset(tilankuvaus:Option[TilankuvausRecord]):Map[String,String] = tilankuvaus match {
      case Some(x) if tarkenteenLisatieto.isDefined => x.tilankuvaukset.mapValues(_.replace("<lisatieto>", tarkenteenLisatieto.get))
      case Some(x) => x.tilankuvaukset
      case _ => Map()
  }
}

case class TilaHistoriaRecord(valintatapajonoOid:String, hakemusOid:String, tila:Valinnantila, luotu:Date) {
  def dto = {
    val tilaDTO = new TilaHistoriaDTO
    tilaDTO.setLuotu(luotu)
    tilaDTO.setTila(tila.valinnantila.toString)
    tilaDTO
  }
}

case class HakijaryhmaRecord(prioriteetti:Int, oid:String, nimi:String, hakukohdeOid:Option[String], kiintio:Int,
                             kaytaKaikki:Boolean, sijoitteluajoId:Long, tarkkaKiintio:Boolean, kaytetaanRyhmaanKuuluvia:Boolean,
                             valintatapajonoOid:Option[String], hakijaryhmatyyppikoodiUri:String) {

  def dto(hakemusOid:List[String]) = {
    val hakijaryhmaDTO = new HakijaryhmaDTO
    hakijaryhmaDTO.setPrioriteetti(prioriteetti)
    hakijaryhmaDTO.setOid(oid)
    hakijaryhmaDTO.setNimi(nimi)
    hakukohdeOid.foreach(hakijaryhmaDTO.setHakukohdeOid)
    hakijaryhmaDTO.setKiintio(kiintio)
    hakijaryhmaDTO.setKaytaKaikki(kaytaKaikki)
    hakijaryhmaDTO.setTarkkaKiintio(tarkkaKiintio)
    hakijaryhmaDTO.setKaytetaanRyhmaanKuuluvia(kaytetaanRyhmaanKuuluvia)
    valintatapajonoOid.foreach(hakijaryhmaDTO.setValintatapajonoOid)
    hakijaryhmaDTO.setHakijaryhmatyyppikoodiUri(hakijaryhmatyyppikoodiUri)
    hakijaryhmaDTO.setHakemusOid(hakemusOid.asJava)
    hakijaryhmaDTO
  }
}

case class TilankuvausRecord(hash:Int, tilankuvauksenTarkenne:ValinnantilanTarkenne, textFi:Option[String],
                             textSv:Option[String], textEn:Option[String]) {
  val tilankuvaukset:Map[String,String] = {
    Map("FI" -> textFi, "SV" -> textSv, "EN" -> textEn).filter(_._2.isDefined).mapValues(_.get)
  }
}
