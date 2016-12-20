package fi.vm.sade.valintatulosservice.valintarekisteri.domain

import java.lang.{Boolean => javaBoolean, Integer => javaInt, String => javaString}
import java.math.{BigDecimal => javaBigDecimal}
import java.util.Date

import fi.vm.sade.sijoittelu.domain.{Hakemus => SijoitteluHakemus, Tasasijasaanto => SijoitteluTasasijasaanto, _}

import scala.collection.JavaConverters._

case class SijoitteluWrapper(sijoitteluajo: SijoitteluAjo, hakukohteet: List[Hakukohde], valintatulokset: List[Valintatulos])

object SijoitteluWrapper {
  def apply(sijoitteluajo: SijoitteluAjo, hakukohteet: java.util.List[Hakukohde], valintatulokset: java.util.List[Valintatulos]): SijoitteluWrapper = {
    SijoitteluWrapper(sijoitteluajo, hakukohteet.asScala.toList, valintatulokset.asScala.toList)
  }
}

case class SijoitteluajoWrapper(
                                 sijoitteluajoId: Long,
                                 hakuOid: String,
                                 startMils: Long,
                                 endMils: Long) {

  val sijoitteluajo: SijoitteluAjo = {
    val sijoitteluajo = new SijoitteluAjo
    sijoitteluajo.setSijoitteluajoId(sijoitteluajoId)
    sijoitteluajo.setHakuOid(hakuOid)
    sijoitteluajo.setStartMils(startMils)
    sijoitteluajo.setEndMils(endMils)
    sijoitteluajo
  }
}

object SijoitteluajoWrapper {
  def apply(sijoitteluAjo: SijoitteluAjo): SijoitteluajoWrapper = {
    SijoitteluajoWrapper(
      sijoitteluAjo.getSijoitteluajoId,
      sijoitteluAjo.getHakuOid,
      sijoitteluAjo.getStartMils,
      sijoitteluAjo.getEndMils
    )
  }
}

case class SijoitteluajonHakukohdeWrapper(
                                           sijoitteluajoId: Long,
                                           oid: String,
                                           tarjoajaOid: String,
                                           kaikkiJonotSijoiteltu: Boolean) {

  val hakukohde: Hakukohde = {
    val hakukohde = new Hakukohde
    hakukohde.setSijoitteluajoId(sijoitteluajoId)
    hakukohde.setOid(oid)
    hakukohde.setTarjoajaOid(tarjoajaOid)
    hakukohde.setKaikkiJonotSijoiteltu(kaikkiJonotSijoiteltu)
    hakukohde
  }
}

object SijoitteluajonHakukohdeWrapper {
  def apply(hakukohde: Hakukohde): SijoitteluajonHakukohdeWrapper = {
    SijoitteluajonHakukohdeWrapper(
      hakukohde.getSijoitteluajoId,
      hakukohde.getOid,
      hakukohde.getTarjoajaOid,
      hakukohde.isKaikkiJonotSijoiteltu
    )
  }
}

sealed trait Tasasijasaanto {
  def tasasijasaanto: SijoitteluTasasijasaanto
}

case object Arvonta extends Tasasijasaanto {
  val tasasijasaanto = SijoitteluTasasijasaanto.ARVONTA
}

case object Ylitaytto extends Tasasijasaanto {
  val tasasijasaanto = SijoitteluTasasijasaanto.YLITAYTTO
}

case object Alitaytto extends Tasasijasaanto {
  val tasasijasaanto = SijoitteluTasasijasaanto.ALITAYTTO
}

object Tasasijasaanto {
  private val valueMapping = Map(
    "Arvonta" -> Arvonta,
    "Ylitaytto" -> Ylitaytto,
    "Alitaytto" -> Alitaytto)
  val values: List[String] = valueMapping.keysIterator.toList

  def apply(value: String): Tasasijasaanto = valueMapping.getOrElse(value, {
    throw new IllegalArgumentException(s"Unknown tasasijasaanto '$value', expected one of $values")
  })

  def getTasasijasaanto(tasasijasaanto: SijoitteluTasasijasaanto) = tasasijasaanto match {
    case SijoitteluTasasijasaanto.ARVONTA => Arvonta
    case SijoitteluTasasijasaanto.ALITAYTTO => Alitaytto
    case SijoitteluTasasijasaanto.YLITAYTTO => Ylitaytto
    case null => Arvonta
  }
}

case class SijoitteluajonValintatapajonoWrapper(
                                                 oid: String,
                                                 nimi: String,
                                                 prioriteetti: Int,
                                                 tasasijasaanto: Tasasijasaanto,
                                                 aloituspaikat: Option[Int],
                                                 alkuperaisetAloituspaikat: Option[Int],
                                                 eiVarasijatayttoa: Boolean = false,
                                                 kaikkiEhdonTayttavatHyvaksytaan: Boolean = false,
                                                 poissaOlevaTaytto: Boolean = false,
                                                 varasijat: Option[Int],
                                                 varasijaTayttoPaivat: Option[Int],
                                                 varasijojaKaytetaanAlkaen: Option[Date],
                                                 varasijojaTaytetaanAsti: Option[Date],
                                                 tayttojono: Option[String],
                                                 hyvaksytty: Int,
                                                 varalla: Int,
                                                 alinHyvaksyttyPistemaara: Option[BigDecimal],
                                                 valintaesitysHyvaksytty: Option[Boolean] = Some(false)) {

  val valintatapajono: Valintatapajono = {
    val valintatapajono = new Valintatapajono
    valintatapajono.setOid(oid)
    valintatapajono.setNimi(nimi)
    valintatapajono.setPrioriteetti(prioriteetti)
    valintatapajono.setTasasijasaanto(tasasijasaanto.tasasijasaanto)
    aloituspaikat.foreach(valintatapajono.setAloituspaikat(_))
    alkuperaisetAloituspaikat.foreach(valintatapajono.setAlkuperaisetAloituspaikat(_))
    valintatapajono.setEiVarasijatayttoa(eiVarasijatayttoa)
    valintatapajono.setKaikkiEhdonTayttavatHyvaksytaan(kaikkiEhdonTayttavatHyvaksytaan)
    valintatapajono.setPoissaOlevaTaytto(poissaOlevaTaytto)
    varasijat.foreach(valintatapajono.setVarasijat(_))
    varasijaTayttoPaivat.foreach(valintatapajono.setVarasijaTayttoPaivat(_))
    varasijojaKaytetaanAlkaen.foreach(valintatapajono.setVarasijojaKaytetaanAlkaen(_))
    varasijojaTaytetaanAsti.foreach(valintatapajono.setVarasijojaTaytetaanAsti(_))
    tayttojono.foreach(valintatapajono.setTayttojono(_))
    valintatapajono.setHyvaksytty(hyvaksytty)
    valintatapajono.setVaralla(varalla)
    alinHyvaksyttyPistemaara.foreach(pm => valintatapajono.setAlinHyvaksyttyPistemaara(pm.bigDecimal))
    valintaesitysHyvaksytty.foreach(valintatapajono.setValintaesitysHyvaksytty(_))
    valintatapajono
  }
}

object SijoitteluajonValintatapajonoWrapper extends OptionConverter {
  def apply(valintatapajono: Valintatapajono): SijoitteluajonValintatapajonoWrapper = {
    SijoitteluajonValintatapajonoWrapper(
      valintatapajono.getOid(),
      valintatapajono.getNimi(),
      valintatapajono.getPrioriteetti(),
      Tasasijasaanto.getTasasijasaanto(valintatapajono.getTasasijasaanto),
      convert[javaInt, Int](valintatapajono.getAloituspaikat(), int),
      convert[javaInt, Int](valintatapajono.getAlkuperaisetAloituspaikat(), int),
      valintatapajono.getEiVarasijatayttoa(),
      valintatapajono.getKaikkiEhdonTayttavatHyvaksytaan(),
      valintatapajono.getPoissaOlevaTaytto(),
      convert[javaInt, Int](valintatapajono.getVarasijat(), int),
      convert[javaInt, Int](valintatapajono.getVarasijaTayttoPaivat(), int),
      convert[Date, Date](valintatapajono.getVarasijojaKaytetaanAlkaen(), date),
      convert[Date, Date](valintatapajono.getVarasijojaTaytetaanAsti(), date),
      convert[javaString, String](valintatapajono.getTayttojono, string),
      valintatapajono.getHyvaksytty(),
      valintatapajono.getVaralla(),
      convert[javaBigDecimal, BigDecimal](valintatapajono.getAlinHyvaksyttyPistemaara(), bigDecimal),
      convert[javaBoolean, Boolean](valintatapajono.getValintaesitysHyvaksytty(), boolean)
    )
  }
}

sealed trait Valinnantila {
  def valinnantila: HakemuksenTila
}

case object Hylatty extends Valinnantila {
  val valinnantila = HakemuksenTila.HYLATTY
}

case object Varalla extends Valinnantila {
  val valinnantila = HakemuksenTila.VARALLA
}

case object Peruuntunut extends Valinnantila {
  val valinnantila = HakemuksenTila.PERUUNTUNUT
}

case object Perunut extends Valinnantila {
  val valinnantila = HakemuksenTila.PERUNUT
}

case object Peruutettu extends Valinnantila {
  val valinnantila = HakemuksenTila.PERUUTETTU
}

case object Hyvaksytty extends Valinnantila {
  val valinnantila = HakemuksenTila.HYVAKSYTTY
}

case object VarasijaltaHyvaksytty extends Valinnantila {
  val valinnantila = HakemuksenTila.VARASIJALTA_HYVAKSYTTY
}

object Valinnantila {
  private val valueMapping = Map(
    "Hylatty" -> Hylatty,
    "Varalla" -> Varalla,
    "Peruuntunut" -> Peruuntunut,
    "VarasijaltaHyvaksytty" -> VarasijaltaHyvaksytty,
    "Hyvaksytty" -> Hyvaksytty,
    "Perunut" -> Perunut,
    "Peruutettu" -> Peruutettu)
  val values: List[String] = valueMapping.keysIterator.toList

  def apply(value: String): Valinnantila = valueMapping.getOrElse(value, {
    throw new IllegalArgumentException(s"Unknown valinnantila '$value', expected one of $values")
  })

  def getValinnantila(valinnantila: HakemuksenTila) = valinnantila match {
    case HakemuksenTila.HYLATTY => Hylatty
    case HakemuksenTila.HYVAKSYTTY => Hyvaksytty
    case HakemuksenTila.PERUNUT => Perunut
    case HakemuksenTila.PERUUNTUNUT => Peruuntunut
    case HakemuksenTila.PERUUTETTU => Peruutettu
    case HakemuksenTila.VARALLA => Varalla
    case HakemuksenTila.VARASIJALTA_HYVAKSYTTY => VarasijaltaHyvaksytty
    case null => throw new IllegalArgumentException(s"Valinnantila null ei ole sallittu")
  }
}

sealed trait ValinnantilanTarkenne {
  def tilankuvauksenTarkenne: TilankuvauksenTarkenne
}

case object PeruuntunutHyvaksyttyYlemmalleHakutoiveelle extends ValinnantilanTarkenne {
  val tilankuvauksenTarkenne = TilankuvauksenTarkenne.PERUUNTUNUT_HYVAKSYTTY_YLEMMALLE_HAKUTOIVEELLE
}

case object PeruuntunutAloituspaikatTaynna extends ValinnantilanTarkenne {
  val tilankuvauksenTarkenne = TilankuvauksenTarkenne.PERUUNTUNUT_ALOITUSPAIKAT_TAYNNA
}

case object PeruuntunutHyvaksyttyToisessaJonossa extends ValinnantilanTarkenne {
  val tilankuvauksenTarkenne = TilankuvauksenTarkenne.PERUUNTUNUT_HYVAKSYTTY_TOISESSA_JONOSSA
}

case object HyvaksyttyVarasijalta extends ValinnantilanTarkenne {
  val tilankuvauksenTarkenne = TilankuvauksenTarkenne.HYVAKSYTTY_VARASIJALTA
}

case object PeruuntunutEiVastaanottanutMaaraaikana extends ValinnantilanTarkenne {
  val tilankuvauksenTarkenne = TilankuvauksenTarkenne.PERUUNTUNUT_EI_VASTAANOTTANUT_MAARAAIKANA
}

case object PeruuntunutVastaanottanutToisenPaikan extends ValinnantilanTarkenne {
  val tilankuvauksenTarkenne = TilankuvauksenTarkenne.PERUUNTUNUT_VASTAANOTTANUT_TOISEN_PAIKAN
}

case object PeruuntunutEiMahduVarasijojenMaaraan extends ValinnantilanTarkenne {
  val tilankuvauksenTarkenne = TilankuvauksenTarkenne.PERUUNTUNUT_EI_MAHDU_VARASIJOJEN_MAARAAN
}

case object PeruuntunutHakukierrosPaattynyt extends ValinnantilanTarkenne {
  val tilankuvauksenTarkenne = TilankuvauksenTarkenne.PERUUNTUNUT_HAKUKIERROS_PAATTYNYT
}

case object PeruuntunutEiVarasijatayttoa extends ValinnantilanTarkenne {
  val tilankuvauksenTarkenne = TilankuvauksenTarkenne.PERUUNTUNUT_EI_VARASIJATAYTTOA
}

case object HyvaksyttyTayttojonoSaannolla extends ValinnantilanTarkenne {
  val tilankuvauksenTarkenne = TilankuvauksenTarkenne.HYVAKSYTTY_TAYTTOJONO_SAANNOLLA
}

case object HylattyHakijaryhmaanKuulumattomana extends ValinnantilanTarkenne {
  val tilankuvauksenTarkenne = TilankuvauksenTarkenne.HYLATTY_HAKIJARYHMAAN_KUULUMATTOMANA
}

case object PeruuntunutVastaanottanutToisenPaikanYhdenSaannonPaikanPiirissa extends ValinnantilanTarkenne {
  val tilankuvauksenTarkenne = TilankuvauksenTarkenne.PERUUNTUNUT_VASTAANOTTANUT_TOISEN_PAIKAN_YHDEN_SAANNON_PAIKAN_PIIRISSA
}

case object EiTilankuvauksenTarkennetta extends ValinnantilanTarkenne {
  val tilankuvauksenTarkenne = TilankuvauksenTarkenne.EI_TILANKUVAUKSEN_TARKENNETTA
}

object ValinnantilanTarkenne {
  private val valueMapping = Map(
    "PeruuntunutHyvaksyttyYlemmalleHakutoiveelle" -> PeruuntunutHyvaksyttyYlemmalleHakutoiveelle,
    "PeruuntunutAloituspaikatTaynna" -> PeruuntunutAloituspaikatTaynna,
    "PeruuntunutHyvaksyttyToisessaJonossa" -> PeruuntunutHyvaksyttyToisessaJonossa,
    "HyvaksyttyVarasijalta" -> HyvaksyttyVarasijalta,
    "PeruuntunutEiVastaanottanutMaaraaikana" -> PeruuntunutEiVastaanottanutMaaraaikana,
    "PeruuntunutVastaanottanutToisenPaikan" -> PeruuntunutVastaanottanutToisenPaikan,
    "PeruuntunutEiMahduVarasijojenMaaraan" -> PeruuntunutEiMahduVarasijojenMaaraan,
    "PeruuntunutHakukierrosPaattynyt" -> PeruuntunutHakukierrosPaattynyt,
    "PeruuntunutEiVarasijatayttoa" -> PeruuntunutEiVarasijatayttoa,
    "HyvaksyttyTayttojonoSaannolla" -> HyvaksyttyTayttojonoSaannolla,
    "HylattyHakijaryhmaanKuulumattomana" -> HylattyHakijaryhmaanKuulumattomana,
    "PeruuntunutVastaanottanutToisenPaikanYhdenSaannonPaikanPiirissa" -> PeruuntunutVastaanottanutToisenPaikanYhdenSaannonPaikanPiirissa,
    "EiTilankuvauksenTarkennetta" -> EiTilankuvauksenTarkennetta
  )
  val values: List[String] = valueMapping.keysIterator.toList

  def apply(value: String): ValinnantilanTarkenne = valueMapping.getOrElse(value, {
    throw new IllegalArgumentException(s"Unknown valinnantilantarkenne '$value', expected one of $values")
  })

  def getValinnantilanTarkenne(tilankuvauksenTarkenne: TilankuvauksenTarkenne) = tilankuvauksenTarkenne match {
    case TilankuvauksenTarkenne.PERUUNTUNUT_HYVAKSYTTY_YLEMMALLE_HAKUTOIVEELLE => PeruuntunutHyvaksyttyYlemmalleHakutoiveelle
    case TilankuvauksenTarkenne.PERUUNTUNUT_ALOITUSPAIKAT_TAYNNA => PeruuntunutAloituspaikatTaynna
    case TilankuvauksenTarkenne.PERUUNTUNUT_HYVAKSYTTY_TOISESSA_JONOSSA => PeruuntunutHyvaksyttyToisessaJonossa
    case TilankuvauksenTarkenne.HYVAKSYTTY_VARASIJALTA => HyvaksyttyVarasijalta
    case TilankuvauksenTarkenne.PERUUNTUNUT_EI_VASTAANOTTANUT_MAARAAIKANA => PeruuntunutEiVastaanottanutMaaraaikana
    case TilankuvauksenTarkenne.PERUUNTUNUT_VASTAANOTTANUT_TOISEN_PAIKAN => PeruuntunutVastaanottanutToisenPaikan
    case TilankuvauksenTarkenne.PERUUNTUNUT_EI_MAHDU_VARASIJOJEN_MAARAAN => PeruuntunutEiMahduVarasijojenMaaraan
    case TilankuvauksenTarkenne.PERUUNTUNUT_HAKUKIERROS_PAATTYNYT => PeruuntunutHakukierrosPaattynyt
    case TilankuvauksenTarkenne.PERUUNTUNUT_EI_VARASIJATAYTTOA => PeruuntunutEiVarasijatayttoa
    case TilankuvauksenTarkenne.HYVAKSYTTY_TAYTTOJONO_SAANNOLLA => HyvaksyttyTayttojonoSaannolla
    case TilankuvauksenTarkenne.HYLATTY_HAKIJARYHMAAN_KUULUMATTOMANA => HylattyHakijaryhmaanKuulumattomana
    case TilankuvauksenTarkenne.PERUUNTUNUT_VASTAANOTTANUT_TOISEN_PAIKAN_YHDEN_SAANNON_PAIKAN_PIIRISSA => PeruuntunutVastaanottanutToisenPaikanYhdenSaannonPaikanPiirissa
    case TilankuvauksenTarkenne.EI_TILANKUVAUKSEN_TARKENNETTA => EiTilankuvauksenTarkennetta
  }
}


case class SijoitteluajonHakemusWrapper(
                                         hakemusOid: String,
                                         hakijaOid: Option[String],
                                         etunimi: Option[String],
                                         sukunimi: Option[String],
                                         prioriteetti: Int,
                                         jonosija: Int,
                                         varasijanNumero: Option[Int],
                                         onkoMuuttunutViimeSijoittelussa: Boolean = false,
                                         pisteet: Option[BigDecimal],
                                         tasasijaJonosija: Int,
                                         hyvaksyttyHarkinnanvaraisesti: Boolean = false,
                                         siirtynytToisestaValintatapajonosta: Boolean = false,
                                         tila: Valinnantila,
                                         tilanKuvaukset: Option[Map[String, String]],
                                         tilankuvauksenTarkenne: ValinnantilanTarkenne,
                                         tarkenteenLisatieto: Option[String],
                                         hyvaksyttyHakijaryhmista: Set[String],
                                         tilaHistoria: List[TilahistoriaWrapper]) {
  val tilankuvauksetWithTarkenne =
    tilanKuvaukset.getOrElse(Map()).mapValues(v => tarkenteenLisatieto.map(v.replace(_, "<lisatieto>")).getOrElse(v)) +
      ("tilankuvauksenTarkenne" -> tilankuvauksenTarkenne.toString)

  val tilankuvauksenHash = tilankuvauksetWithTarkenne.hashCode

  import scala.collection.JavaConverters._

  val hakemus: SijoitteluHakemus = {
    val hakemus = new SijoitteluHakemus
    hakemus.setHakemusOid(hakemusOid)
    hakijaOid.foreach(hakemus.setHakijaOid(_))
    etunimi.foreach(hakemus.setEtunimi(_))
    sukunimi.foreach(hakemus.setSukunimi(_))
    hakemus.setPrioriteetti(prioriteetti)
    hakemus.setJonosija(jonosija)
    varasijanNumero.foreach(hakemus.setVarasijanNumero(_))
    hakemus.setOnkoMuuttunutViimeSijoittelussa(onkoMuuttunutViimeSijoittelussa)
    pisteet.foreach(p => hakemus.setPisteet(p.bigDecimal))
    hakemus.setTasasijaJonosija(tasasijaJonosija)
    hakemus.setHyvaksyttyHarkinnanvaraisesti(hyvaksyttyHarkinnanvaraisesti)
    hakemus.setSiirtynytToisestaValintatapajonosta(siirtynytToisestaValintatapajonosta)
    hakemus.setTila(tila.valinnantila)
    hakemus.setTilanKuvaukset(tilanKuvaukset.getOrElse(Map()).asJava)
    hakemus.setTilankuvauksenTarkenne(tilankuvauksenTarkenne.tilankuvauksenTarkenne)
    tarkenteenLisatieto.foreach(hakemus.setTarkenteenLisatieto(_))
    hakemus.setHyvaksyttyHakijaryhmista(hyvaksyttyHakijaryhmista.asJava)
    hakemus.setTilaHistoria(tilaHistoria.map(_.tilahistoria).asJava)
    hakemus
  }
}

object SijoitteluajonHakemusWrapper extends OptionConverter {

  import scala.collection.JavaConverters._

  def apply(hakemus: SijoitteluHakemus): SijoitteluajonHakemusWrapper = {
    SijoitteluajonHakemusWrapper(
      hakemus.getHakemusOid,
      convert[javaString, String](hakemus.getHakijaOid, string),
      convert[javaString, String](hakemus.getEtunimi, string),
      convert[javaString, String](hakemus.getSukunimi, string),
      hakemus.getPrioriteetti,
      hakemus.getJonosija,
      convert[javaInt, Int](hakemus.getVarasijanNumero, int),
      hakemus.isOnkoMuuttunutViimeSijoittelussa,
      convert[javaBigDecimal, BigDecimal](hakemus.getPisteet, bigDecimal),
      hakemus.getTasasijaJonosija,
      hakemus.isHyvaksyttyHarkinnanvaraisesti,
      hakemus.getSiirtynytToisestaValintatapajonosta,
      Valinnantila.getValinnantila(hakemus.getTila),
      Option(hakemus.getTilanKuvaukset.asScala.toMap),
      ValinnantilanTarkenne.getValinnantilanTarkenne(hakemus.getTilankuvauksenTarkenne),
      convert[javaString, String](hakemus.getTarkenteenLisatieto, string),
      hakemus.getHyvaksyttyHakijaryhmista.asScala.toSet,
      hakemus.getTilaHistoria.asScala.map(TilahistoriaWrapper(_)).toList
    )
  }
}

case class TilahistoriaWrapper(tila: Valinnantila, luotu: Date) {
  val tilahistoria: TilaHistoria = {
    val tilahistoria = new TilaHistoria()
    tilahistoria.setLuotu(luotu)
    tilahistoria.setTila(tila.valinnantila)
    tilahistoria
  }
}

object TilahistoriaWrapper {
  def apply(tilahistoria: TilaHistoria): TilahistoriaWrapper = {
    TilahistoriaWrapper(
      Valinnantila.getValinnantila(tilahistoria.getTila),
      tilahistoria.getLuotu
    )
  }
}

sealed trait SijoitteluajonIlmoittautumistila {
  def ilmoittautumistila: IlmoittautumisTila
}

case object EiTehty extends SijoitteluajonIlmoittautumistila {
  val ilmoittautumistila = IlmoittautumisTila.EI_TEHTY
}

case object LasnaKokoLukuvuosi extends SijoitteluajonIlmoittautumistila {
  val ilmoittautumistila = IlmoittautumisTila.LASNA_KOKO_LUKUVUOSI
}

case object PoissaKokoLukuvuosi extends SijoitteluajonIlmoittautumistila {
  val ilmoittautumistila = IlmoittautumisTila.POISSA_KOKO_LUKUVUOSI
}

case object EiIlmoittautunut extends SijoitteluajonIlmoittautumistila {
  val ilmoittautumistila = IlmoittautumisTila.EI_ILMOITTAUTUNUT
}

case object LasnaSyksy extends SijoitteluajonIlmoittautumistila {
  val ilmoittautumistila = IlmoittautumisTila.LASNA_SYKSY
}

case object PoissaSyksy extends SijoitteluajonIlmoittautumistila {
  val ilmoittautumistila = IlmoittautumisTila.POISSA_SYKSY
}

case object Lasna extends SijoitteluajonIlmoittautumistila {
  val ilmoittautumistila = IlmoittautumisTila.LASNA
}

case object Poissa extends SijoitteluajonIlmoittautumistila {
  val ilmoittautumistila = IlmoittautumisTila.POISSA
}

object SijoitteluajonIlmoittautumistila {
  private val valueMapping = Map(
    "EiTehty" -> EiTehty,
    "LasnaKokoLukuvuosi" -> LasnaKokoLukuvuosi,
    "PoissaKokoLukuvuosi" -> PoissaKokoLukuvuosi,
    "EiIlmoittautunut" -> EiIlmoittautunut,
    "LasnaSyksy" -> LasnaSyksy,
    "PoissaSyksy" -> PoissaSyksy,
    "Lasna" -> Lasna,
    "Poissa" -> Poissa)
  val values: List[String] = valueMapping.keysIterator.toList

  def apply(value: String): SijoitteluajonIlmoittautumistila = valueMapping.getOrElse(value, {
    throw new IllegalArgumentException(s"Unknown ilmoittautumistila '$value', expected one of $values")
  })

  def apply(ilmoittautumistila: IlmoittautumisTila): SijoitteluajonIlmoittautumistila = ilmoittautumistila match {
    case IlmoittautumisTila.EI_TEHTY => EiTehty
    case IlmoittautumisTila.LASNA_KOKO_LUKUVUOSI => LasnaKokoLukuvuosi
    case IlmoittautumisTila.POISSA_KOKO_LUKUVUOSI => PoissaKokoLukuvuosi
    case IlmoittautumisTila.EI_ILMOITTAUTUNUT => EiIlmoittautunut
    case IlmoittautumisTila.LASNA_SYKSY => LasnaSyksy
    case IlmoittautumisTila.POISSA_SYKSY => PoissaSyksy
    case IlmoittautumisTila.LASNA => Lasna
    case IlmoittautumisTila.POISSA => Poissa
  }

  def apply(ilmoittautumistila: fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila): SijoitteluajonIlmoittautumistila =
    SijoitteluajonIlmoittautumistila(IlmoittautumisTila.valueOf(ilmoittautumistila.toString))
}

case class SijoitteluajonValinnantulosWrapper(
                                               valintatapajonoOid: String,
                                               hakemusOid: String,
                                               hakukohdeOid: String,
                                               ehdollisestiHyvaksyttavissa: Boolean = false,
                                               julkaistavissa: Boolean = false,
                                               hyvaksyttyVarasijalta: Boolean = false,
                                               hyvaksyPeruuntunut: Boolean = false,
                                               ilmoittautumistila: Option[SijoitteluajonIlmoittautumistila],
                                               logEntries: Option[List[LogEntry]],
                                               mailStatus: ValintatulosMailStatus
                                             ) {
  val valintatulos: Valintatulos = {
    val valintatulos = new Valintatulos()
    valintatulos.setValintatapajonoOid(valintatapajonoOid, "")
    valintatulos.setHakemusOid(hakemusOid, "")
    valintatulos.setHakukohdeOid(hakukohdeOid, "")
    valintatulos.setEhdollisestiHyvaksyttavissa(ehdollisestiHyvaksyttavissa, "", "");
    valintatulos.setJulkaistavissa(julkaistavissa, "")
    valintatulos.setHyvaksyttyVarasijalta(hyvaksyttyVarasijalta, "")
    valintatulos.setHyvaksyPeruuntunut(hyvaksyPeruuntunut, "")
    ilmoittautumistila.foreach(ilmoittautumistila => valintatulos.setIlmoittautumisTila(ilmoittautumistila.ilmoittautumistila, ""))
    valintatulos.setOriginalLogEntries(logEntries.getOrElse(List()).asJava)
    valintatulos.setMailStatus(mailStatus)
    valintatulos
  }
}

object SijoitteluajonValinnantulosWrapper extends OptionConverter {
  def apply(valintatulos: Valintatulos): SijoitteluajonValinnantulosWrapper = SijoitteluajonValinnantulosWrapper(
    valintatulos.getValintatapajonoOid,
    valintatulos.getHakemusOid,
    valintatulos.getHakukohdeOid,
    valintatulos.getEhdollisestiHyvaksyttavissa,
    valintatulos.getJulkaistavissa,
    valintatulos.getHyvaksyttyVarasijalta,
    valintatulos.getHyvaksyPeruuntunut,
    convert[IlmoittautumisTila, SijoitteluajonIlmoittautumistila](valintatulos.getIlmoittautumisTila,
      SijoitteluajonIlmoittautumistila.apply),
    Option(valintatulos.getOriginalLogEntries.asScala.toList),
    valintatulos.getMailStatus)
}

case class LogEntryWrapper(luotu: Date, muokkaaja: String, muutos: String, selite: String) {
  val entry: LogEntry = {
    val entry = new LogEntry
    entry.setLuotu(luotu)
    entry.setMuokkaaja(muokkaaja)
    entry.setMuutos(muutos)
    entry.setSelite(selite)
    entry
  }
}

object LogEntryWrapper extends OptionConverter {
  def apply(entry: LogEntry): LogEntryWrapper = LogEntryWrapper(
    entry.getLuotu,
    entry.getMuokkaaja,
    entry.getMuutos,
    entry.getSelite
  )
}

case class MailStatusWrapper(previousCheck: Option[Date], sent: Option[Date], done: Option[Date], message: Option[String]) {
  val status: ValintatulosMailStatus = {
    val status = new ValintatulosMailStatus
    status.previousCheck = previousCheck.getOrElse(null)
    status.sent = sent.getOrElse(null)
    status.done = done.getOrElse(null)
    status.message = message.getOrElse(null)
    status
  }
}

object MailStatusWrapper extends OptionConverter {
  def apply(status: ValintatulosMailStatus): MailStatusWrapper = MailStatusWrapper(
    Option(status.previousCheck),
    Option(status.sent),
    Option(status.done),
    Option(status.message)
  )
}

case class SijoitteluajonPistetietoWrapper(
                                            tunniste: String,
                                            arvo: Option[String],
                                            laskennallinenArvo: Option[String],
                                            osallistuminen: Option[String]
                                          ) {
  val pistetieto: Pistetieto = {
    val pistetieto = new Pistetieto()
    pistetieto.setTunniste(tunniste)
    arvo.foreach(pistetieto.setArvo(_))
    laskennallinenArvo.foreach(pistetieto.setLaskennallinenArvo(_))
    osallistuminen.foreach(pistetieto.setOsallistuminen(_))
    pistetieto
  }
}

object SijoitteluajonPistetietoWrapper extends OptionConverter {
  def apply(pistetieto: Pistetieto): SijoitteluajonPistetietoWrapper = {
    SijoitteluajonPistetietoWrapper(
      pistetieto.getTunniste,
      convert[javaString, String](pistetieto.getArvo, string),
      convert[javaString, String](pistetieto.getLaskennallinenArvo, string),
      convert[javaString, String](pistetieto.getOsallistuminen, string)
    )
  }
}

case class SijoitteluajonHakijaryhmaWrapper(
                                             oid: String,
                                             nimi: String,
                                             prioriteetti: Int,
                                             kiintio: Int,
                                             kaytaKaikki: Boolean,
                                             tarkkaKiintio: Boolean,
                                             kaytetaanRyhmaanKuuluvia: Boolean,
                                             hakemusOid: List[String],
                                             valintatapajonoOid: Option[String],
                                             hakukohdeOid: Option[String],
                                             hakijaryhmatyyppikoodiUri: Option[String]
                                           ) {
  val hakijaryhma: Hakijaryhma = {
    import scala.collection.JavaConverters._
    val hakijaryhma = new Hakijaryhma()
    hakijaryhma.setOid(oid)
    hakijaryhma.setNimi(nimi)
    hakijaryhma.setPrioriteetti(prioriteetti)
    hakijaryhma.setKiintio(kiintio)
    hakijaryhma.setKaytaKaikki(kaytaKaikki)
    hakijaryhma.setTarkkaKiintio(tarkkaKiintio)
    hakijaryhma.setKaytetaanRyhmaanKuuluvia(kaytetaanRyhmaanKuuluvia)
    hakijaryhma.getHakemusOid.addAll(hakemusOid.asJava)
    valintatapajonoOid.foreach(hakijaryhma.setValintatapajonoOid(_))
    hakukohdeOid.foreach(hakijaryhma.setHakukohdeOid(_))
    hakijaryhmatyyppikoodiUri.foreach(hakijaryhma.setHakijaryhmatyyppikoodiUri(_))
    hakijaryhma
  }
}

object SijoitteluajonHakijaryhmaWrapper extends OptionConverter {

  import scala.collection.JavaConverters._

  def apply(hakijaryhma: Hakijaryhma): SijoitteluajonHakijaryhmaWrapper = {
    SijoitteluajonHakijaryhmaWrapper(
      hakijaryhma.getOid,
      hakijaryhma.getNimi,
      hakijaryhma.getPrioriteetti,
      hakijaryhma.getKiintio,
      hakijaryhma.isKaytaKaikki,
      hakijaryhma.isTarkkaKiintio,
      hakijaryhma.isKaytetaanRyhmaanKuuluvia,
      hakijaryhma.getHakemusOid.asScala.toList,
      convert[javaString, String](hakijaryhma.getValintatapajonoOid, string),
      convert[javaString, String](hakijaryhma.getHakukohdeOid, string),
      convert[javaString, String](hakijaryhma.getHakijaryhmatyyppikoodiUri, string)
    )
  }
}

trait OptionConverter {
  def int(x: javaInt) = x.toInt

  def boolean(x: javaBoolean) = x.booleanValue

  def bigDecimal(x: javaBigDecimal) = BigDecimal(x)

  def string(x: javaString) = x

  def date(x: Date) = x

  def convert[javaType, scalaType](javaObject: javaType, f: javaType => scalaType): Option[scalaType] = javaObject match {
    case null => None //Avoid NullPointerException raised by type conversion when creating scala option with java object
    case x => Some(f(x))
  }
}
