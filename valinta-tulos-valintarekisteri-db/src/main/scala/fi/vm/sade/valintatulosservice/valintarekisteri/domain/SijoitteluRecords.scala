package fi.vm.sade.valintatulosservice.valintarekisteri.domain

import java.util.Date

import fi.vm.sade.sijoittelu.tulos.dto._
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.{HakijaDTO, HakutoiveDTO}

import scala.collection.JavaConverters._

case class SijoitteluajoRecord(sijoitteluajoId:Long, hakuOid:String, startMils:Long, endMils:Long)

case class HakijaRecord(etunimi:String, sukunimi:String, hakemusOid:String, hakijaOid:String)

case class HakutoiveRecord(hakemusOid: String, hakutoive: Int, hakukohdeOid: String, valintatuloksenTila: String, kaikkiJonotsijoiteltu: Boolean)

case class PistetietoRecord(valintatapajonoOid:String, hakemusOid:String, tunniste:String,
                            arvo:String, laskennallinenArvo:String, osallistuminen:String)

case class SijoittelunHakukohdeRecord(sijoitteluajoId: Long, oid: String, kaikkiJonotsijoiteltu: Boolean)

case class ValintatapajonoRecord(tasasijasaanto:String, oid:String, nimi:String, prioriteetti:Int, aloituspaikat:Option[Int],
                                 alkuperaisetAloituspaikat:Option[Int], alinHyvaksyttyPistemaara:BigDecimal,
                                 eiVarasijatayttoa:Boolean, kaikkiEhdonTayttavatHyvaksytaan:Boolean,
                                 poissaOlevaTaytto:Boolean, valintaesitysHyvaksytty:Option[Boolean], hakeneet:Int,
                                 hyvaksytty:Int, varalla:Int, varasijat:Option[Int], varasijanTayttoPaivat:Option[Int],
                                 varasijojaKaytetaanAlkaen:Option[java.sql.Date], varasijojaKaytetaanAsti:Option[java.sql.Date],
                                 tayttoJono:Option[String], hakukohdeOid:String)

case class HakemusRecord(hakijaOid:Option[String], hakemusOid:String, pisteet:Option[BigDecimal], etunimi:Option[String], sukunimi:Option[String],
                         prioriteetti:Int, jonosija:Int, tasasijaJonosija:Int, tila:Valinnantila, tilankuvausHash:Int,
                         tarkenteenLisatieto:Option[String], hyvaksyttyHarkinnanvaraisesti:Boolean, varasijaNumero:Option[Int],
                         onkoMuuttunutviimesijoittelusta:Boolean,
                         siirtynytToisestaValintatapaJonosta:Boolean, valintatapajonoOid:String)

case class TilaHistoriaRecord(valintatapajonoOid:String, hakemusOid:String, tila:Valinnantila, luotu:Date)

case class HakijaryhmaRecord(prioriteetti:Int, oid:String, nimi:String, hakukohdeOid:Option[String], kiintio:Int,
                             kaytaKaikki:Boolean, sijoitteluajoId:Long, tarkkaKiintio:Boolean, kaytetaanRyhmaanKuuluvia:Boolean,
                             valintatapajonoOid:Option[String], hakijaryhmatyyppikoodiUri:String)

case class TilankuvausRecord(hash:Int, tilankuvauksenTarkenne:ValinnantilanTarkenne, textFi:Option[String],
                             textSv:Option[String], textEn:Option[String]) {
  val tilankuvaukset:Map[String,String] = {
    Map("FI" -> textFi, "SV" -> textSv, "EN" -> textEn).filter(_._2.isDefined).mapValues(_.get)
  }
}

abstract class SijoitteluRecordToDTO {
  def hakijaRecordToDTO(hakija: HakijaRecord): HakijaDTO = {
    val hakijaDTO = new HakijaDTO
    hakijaDTO.setHakijaOid(hakija.hakijaOid)
    hakijaDTO.setHakemusOid(hakija.hakemusOid)
    hakijaDTO.setEtunimi(hakija.etunimi)
    hakijaDTO.setSukunimi(hakija.sukunimi)
    return hakijaDTO
  }

  def hakutoiveRecordToDTO(hakutoive: HakutoiveRecord, pistetiedot: List[PistetietoRecord]): HakutoiveDTO = {
    val hakutoiveDTO = new HakutoiveDTO
    hakutoiveDTO.setHakutoive(hakutoive.hakutoive)
    hakutoiveDTO.setHakukohdeOid(hakutoive.hakukohdeOid)
    //  TODO mites tämä? hakutoiveDTO.setVastaanottotieto(hakutoive.valintatuloksenTila)

    val pistetietoDTOs = pistetiedot.map(p => pistetietoRecordToTDO(p))
    hakutoiveDTO.setPistetiedot(pistetietoDTOs.asJava)
    return hakutoiveDTO
  }

  def pistetietoRecordToTDO(pistetieto: PistetietoRecord): PistetietoDTO = {
    val pistetietoDTO = new PistetietoDTO
    pistetietoDTO.setArvo(pistetieto.arvo)
    pistetietoDTO.setLaskennallinenArvo(pistetieto.laskennallinenArvo)
    pistetietoDTO.setOsallistuminen(pistetieto.osallistuminen)
    pistetietoDTO.setTunniste(pistetieto.tunniste)
    return pistetietoDTO
  }

  def sijoitteluajoRecordToDto(sijoitteluajo: SijoitteluajoRecord): SijoitteluajoDTO = {
    val sijoitteluajoDTO = new SijoitteluajoDTO
    sijoitteluajoDTO.setSijoitteluajoId(sijoitteluajo.sijoitteluajoId)
    sijoitteluajoDTO.setHakuOid(sijoitteluajo.hakuOid)
    sijoitteluajoDTO.setStartMils(sijoitteluajo.startMils)
    sijoitteluajoDTO.setEndMils(sijoitteluajo.endMils)
    sijoitteluajoDTO
  }

  def sijoitteluajoRecordToDto(sijoitteluajo: SijoitteluajoRecord, hakukohteet:List[HakukohdeDTO]): SijoitteluajoDTO = {
    val sijoitteluajoDTO = sijoitteluajoRecordToDto(sijoitteluajo)
    sijoitteluajoDTO.setHakukohteet(hakukohteet.asJava)
    sijoitteluajoDTO
  }

  import scala.collection.JavaConverters._

  def sijoittelunHakukohdeRecordToDTO(hakukohde: SijoittelunHakukohdeRecord): HakukohdeDTO = {
    val hakukohdeDTO = new HakukohdeDTO
    hakukohdeDTO.setSijoitteluajoId(hakukohde.sijoitteluajoId)
    hakukohdeDTO.setOid(hakukohde.oid)
    hakukohdeDTO.setKaikkiJonotSijoiteltu(hakukohde.kaikkiJonotsijoiteltu)
    hakukohdeDTO
  }

  def valintatapajonoRecordToDTO(jono: ValintatapajonoRecord, hakemukset: List[HakemusDTO]): ValintatapajonoDTO = {
    val jonoDTO = new ValintatapajonoDTO
    jonoDTO.setTasasijasaanto(fi.vm.sade.sijoittelu.tulos.dto.Tasasijasaanto.valueOf(jono.tasasijasaanto.toUpperCase()))
    jonoDTO.setOid(jono.oid)
    jonoDTO.setNimi(jono.nimi)
    jonoDTO.setPrioriteetti(jono.prioriteetti)
    jonoDTO.setAloituspaikat(jono.aloituspaikat.get)
    jono.alkuperaisetAloituspaikat.foreach(jonoDTO.setAlkuperaisetAloituspaikat(_))
    jonoDTO.setAlinHyvaksyttyPistemaara(bigDecimal(jono.alinHyvaksyttyPistemaara))
    jonoDTO.setEiVarasijatayttoa(jono.eiVarasijatayttoa)
    jonoDTO.setKaikkiEhdonTayttavatHyvaksytaan(jono.kaikkiEhdonTayttavatHyvaksytaan)
    jonoDTO.setPoissaOlevaTaytto(jono.poissaOlevaTaytto)
    jono.valintaesitysHyvaksytty.foreach(jonoDTO.setValintaesitysHyvaksytty(_))
    jonoDTO.setHyvaksytty(jono.hyvaksytty)
    jonoDTO.setVaralla(jono.varalla)
    jono.varasijat.foreach(jonoDTO.setVarasijat(_))
    jono.varasijanTayttoPaivat.foreach(jonoDTO.setVarasijaTayttoPaivat(_))
    jono.varasijojaKaytetaanAlkaen.foreach(jonoDTO.setVarasijojaKaytetaanAlkaen(_))
    jono.varasijojaKaytetaanAsti.foreach(jonoDTO.setVarasijojaTaytetaanAsti(_))
    jono.tayttoJono.foreach(jonoDTO.setTayttojono(_))
    jonoDTO.setHakemukset(hakemukset.asJava)
    jonoDTO.setHakeneet(hakemukset.size)
    jonoDTO
  }

  def hakemusRecordToDTO(hakemus:HakemusRecord,
                         hakijaryhmaOids:Set[String],
                         tilanKuvaukset:Map[String,String],
                         tilahistoria:List[TilaHistoriaDTO],
                         pistetiedot:List[PistetietoDTO]): HakemusDTO = {
    val hakemusDTO = new HakemusDTO
    hakemus.hakijaOid.foreach(hakemusDTO.setHakijaOid(_))
    hakemusDTO.setHakemusOid(hakemus.hakemusOid)
    hakemus.pisteet.foreach(p => hakemusDTO.setPisteet(p.bigDecimal))
    hakemus.etunimi.foreach(hakemusDTO.setEtunimi(_))
    hakemus.sukunimi.foreach(hakemusDTO.setSukunimi(_))
    hakemusDTO.setPrioriteetti(hakemus.prioriteetti)
    hakemusDTO.setJonosija(hakemus.jonosija)
    hakemusDTO.setTasasijaJonosija(hakemus.tasasijaJonosija)
    hakemusDTO.setTila(HakemuksenTila.valueOf(hakemus.tila.valinnantila.name()))
    hakemusDTO.setTilanKuvaukset(tilanKuvaukset.asJava)
    hakemusDTO.setHyvaksyttyHarkinnanvaraisesti(hakemus.hyvaksyttyHarkinnanvaraisesti)
    hakemus.varasijaNumero.foreach(hakemusDTO.setVarasijanNumero(_))
    hakemusDTO.setOnkoMuuttunutViimeSijoittelussa(hakemus.onkoMuuttunutviimesijoittelusta)
    hakemusDTO.setHyvaksyttyHakijaryhmista(hakijaryhmaOids.asJava)
    hakemusDTO.setSiirtynytToisestaValintatapajonosta(hakemus.siirtynytToisestaValintatapaJonosta)
    hakemusDTO.setValintatapajonoOid(hakemus.valintatapajonoOid)
    hakemusDTO.setTilaHistoria(tilahistoria.asJava)
    hakemusDTO.getPistetiedot.addAll(pistetiedot.asJava)
    hakemusDTO
  }

  def tilaHistoriaRecordToDTO(tila: TilaHistoriaRecord): TilaHistoriaDTO = {
    val tilaDTO = new TilaHistoriaDTO
    tilaDTO.setLuotu(tila.luotu)
    tilaDTO.setTila(tila.tila.valinnantila.toString)
    tilaDTO
  }

  def hakijaryhmaRecordToDTO(hakijaRyhma: HakijaryhmaRecord): HakijaryhmaDTO = {
    val ryhmaDTO = new HakijaryhmaDTO
    ryhmaDTO.setPrioriteetti(hakijaRyhma.prioriteetti)
    ryhmaDTO.setOid(hakijaRyhma.oid)
    ryhmaDTO.setNimi(hakijaRyhma.nimi)
    hakijaRyhma.hakukohdeOid.foreach(ryhmaDTO.setHakukohdeOid(_))
    ryhmaDTO.setKiintio(hakijaRyhma.kiintio)
    ryhmaDTO.setKaytaKaikki(hakijaRyhma.kaytaKaikki)
    ryhmaDTO.setTarkkaKiintio(hakijaRyhma.tarkkaKiintio)
    ryhmaDTO.setKaytetaanRyhmaanKuuluvia(hakijaRyhma.kaytetaanRyhmaanKuuluvia)
    hakijaRyhma.valintatapajonoOid.foreach(ryhmaDTO.setValintatapajonoOid(_))
    ryhmaDTO.setHakijaryhmatyyppikoodiUri(hakijaRyhma.hakijaryhmatyyppikoodiUri)
    ryhmaDTO
  }

  def hakijaryhmaRecordToDTO(hakijaRyhma: HakijaryhmaRecord, hakemusOidit:List[String]): HakijaryhmaDTO = {
    val ryhmaDTO = hakijaryhmaRecordToDTO(hakijaRyhma)
    ryhmaDTO.setHakemusOid(hakemusOidit.asJava)
    ryhmaDTO
  }

  def bigDecimal(bigDecimal:BigDecimal): java.math.BigDecimal = bigDecimal match {
    case i: BigDecimal => i.bigDecimal
    case _ => null
  }
}
