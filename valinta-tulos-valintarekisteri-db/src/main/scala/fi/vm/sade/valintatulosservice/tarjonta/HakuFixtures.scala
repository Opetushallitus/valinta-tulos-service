package fi.vm.sade.valintatulosservice.tarjonta

import java.io.InputStream

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakuOid, HakukohdeOid}

import fi.vm.sade.valintatulosservice.MonadHelper
import org.json4s.jackson.JsonMethods._

object HakuFixtures extends HakuService with JsonHakuService {
  val defaultHakuOid = HakuOid("1.2.246.562.5.2013080813081926341928")
  val korkeakouluYhteishaku = HakuOid("korkeakoulu-yhteishaku")
  val korkeakouluLisahaku1 = HakuOid("korkeakoulu-lisahaku1")
  val korkeakouluErillishaku = HakuOid("korkeakoulu-erillishaku")
  val toinenAsteYhteishaku = HakuOid("toinen-aste-yhteishaku")
  val toinenAsteErillishakuEiSijoittelua = HakuOid("toinen-aste-erillishaku-ei-sijoittelua")
  val korkeakouluErillishakuEiYhdenPaikanSaantoa = HakuOid("korkeakoulu-erillishaku-ei-yhden-paikan-saantoa")

  private var hakuOids = List(defaultHakuOid)
  private var activeFixture = korkeakouluYhteishaku

  def useFixture(fixtureName: HakuOid, hakuOids: List[HakuOid] = List(defaultHakuOid)) {
    this.hakuOids = hakuOids
    this.activeFixture = fixtureName
  }
  override def getHakukohdesForHaku(hakuOid: HakuOid): Either[Throwable, Seq[Hakukohde]] = {
    getHakukohdeOids(hakuOid).right.flatMap(getHakukohdes)
  }
  override def getHaku(oid: HakuOid): Either[Throwable, Haku] = {
    getHakuFixture(oid).map(toHaku(_).copy(oid = oid)).toRight(new IllegalArgumentException(s"No haku $oid found"))
  }

  private def getHakuFixture(oid: HakuOid): Option[HakuTarjonnassa] = {
    getHakuFixtureAsStream(oid)
      .map(scala.io.Source.fromInputStream(_).mkString)
      .map { response =>
        (parse(response) \ "result").extract[HakuTarjonnassa]
    }
  }

  private def getHakuFixtureAsStream(oid: HakuOid): Option[InputStream] = {
    val default = getFixtureAsStream(oid)
    if(default.isDefined) {
      default
    }
    else {
      getFixtureAsStream(activeFixture)
    }
  }
  def getKomos(kOids: Seq[String]): Either[Throwable, Seq[Komo]] = {
    Left(new RuntimeException("Not implemented!"))
  }
  def getKoulutuses(koulutusOids: Seq[String]): Either[Throwable, Seq[Koulutus]] = {
    Left(new RuntimeException("Not implemented!"))
  }
  private def getFixtureAsStream(hakuOid: HakuOid) = {
    Option(getClass.getResourceAsStream("/fixtures/tarjonta/haku/" + hakuOid.toString + ".json"))
  }

  override def kaikkiJulkaistutHaut: Either[Throwable, List[Haku]] = {
    Right(hakuOids.flatMap { hakuOid =>
      getHakuFixture(hakuOid).toList.filter {_.julkaistu}.map(toHaku(_).copy(oid = hakuOid))
    })
  }

  override def getHakukohdeKela(oid: HakukohdeOid): Either[Throwable, HakukohdeKela] = {
    Left(new UnsupportedOperationException("Not implemented"))
  }
  override def getHakukohdes(oids: Seq[HakukohdeOid]): Either[Throwable, Seq[Hakukohde]] ={
    MonadHelper.sequence(for {oid <- oids.toStream} yield getHakukohde(oid))
  }
  override def getHakukohde(oid: HakukohdeOid): Either[Throwable, Hakukohde] ={
    val hakuOid = hakuOids.head
    // TODO: Saner / more working test data
    if (activeFixture == toinenAsteYhteishaku || activeFixture == toinenAsteErillishakuEiSijoittelua) {
      Right(Hakukohde(oid, hakuOid, Set("123.123.123.123"), List("koulu.tus.oid"), "AMMATILLINEN_PERUSKOULUTUS", "TUTKINTO_OHJELMA",
        Map("kieli_fi" -> "Lukion ilmaisutaitolinja"), Map("fi" -> "Kallion lukio"), YhdenPaikanSaanto(false, "testihakukohde"), false, "kausi_k#1", 2016))
    } else {
      Right(Hakukohde(oid, hakuOid, Set("123.123.123.123"), List("koulu.tus.oid"), "KORKEAKOULUTUS", "TUTKINTO",
        Map("kieli_fi" -> "Lukion ilmaisutaitolinja"), Map("fi" -> "Kallion lukio"), YhdenPaikanSaanto(
          activeFixture != korkeakouluErillishakuEiYhdenPaikanSaantoa, "testihakukohde"), true, "kausi_k#1", 2016))
    }
  }

  override def getHakukohdeOids(hakuOid: HakuOid): Either[Throwable, Seq[HakukohdeOid]] = Right(List(
    "1.2.246.562.14.2013120515524070995659",
    "1.2.246.562.14.2014022408541751568934",
    "1.2.246.562.20.42476855715",
    "1.2.246.562.20.93395603447",
    "1.2.246.562.20.99933864235",
    "1.2.246.562.5.16303028779",
    "1.2.246.562.5.72607738902",
    "1.2.246.562.5.72607738903",
    "1.2.246.562.5.72607738904"
  ).map(HakukohdeOid))

  override def getArbitraryPublishedHakukohdeOid(hakuOid: HakuOid): Either[Throwable, HakukohdeOid] =
    getHakukohdeOids(hakuOid).right.map(_.head)

  private def sequence[A, B](xs: Stream[Either[B, A]]): Either[B, List[A]] = xs match {
    case Stream.Empty => Right(Nil)
    case Left(e)#::_ => Left(e)
    case Right(x)#::rest => sequence(rest).right.map(x +: _)
  }
}
