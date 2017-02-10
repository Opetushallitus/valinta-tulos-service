package fi.vm.sade.valintatulosservice.tarjonta

import java.io.InputStream
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Kausi
import org.json4s.jackson.JsonMethods._

object HakuFixtures extends HakuService with JsonHakuService {
  val defaultHakuOid = "1.2.246.562.5.2013080813081926341928"
  val korkeakouluYhteishaku = "korkeakoulu-yhteishaku"
  val korkeakouluLisahaku1 = "korkeakoulu-lisahaku1"
  val korkeakouluErillishaku = "korkeakoulu-erillishaku"
  val toinenAsteYhteishaku = "toinen-aste-yhteishaku"
  val toinenAsteErillishakuEiSijoittelua = "toinen-aste-erillishaku-ei-sijoittelua"
  val korkeakouluErillishakuEiYhdenPaikanSaantoa = "korkeakoulu-erillishaku-ei-yhden-paikan-saantoa"

  private var hakuOids = List(defaultHakuOid)
  private var activeFixture = korkeakouluYhteishaku

  def useFixture(fixtureName: String, hakuOids: List[String] = List(defaultHakuOid)) {
    this.hakuOids = hakuOids
    this.activeFixture = fixtureName
  }
  override def getHakukohdesForHaku(hakuOid: String): Either[Throwable, Seq[Hakukohde]] = {
    getHakukohdeOids(hakuOid).right.flatMap(getHakukohdes)
  }
  override def getHaku(oid: String): Either[Throwable, Haku] = {
    getHakuFixture(oid).map(toHaku(_).copy(oid = oid)).toRight(new IllegalArgumentException(s"No haku $oid found"))
  }

  private def getHakuFixture(oid: String): Option[HakuTarjonnassa] = {
    getHakuFixtureAsStream(oid)
      .map(scala.io.Source.fromInputStream(_).mkString)
      .map { response =>
        (parse(response) \ "result").extract[HakuTarjonnassa]
    }
  }

  private def getHakuFixtureAsStream(oid: String): Option[InputStream] = {
    val default = getFixtureAsStream(oid)
    if(default.isDefined) {
      default
    }
    else {
      getFixtureAsStream(activeFixture)
    }
  }

  private def getFixtureAsStream(baseFilename: String) = {
    Option(getClass.getResourceAsStream("/fixtures/tarjonta/haku/" + baseFilename + ".json"))
  }

  override def kaikkiJulkaistutHaut: Either[Throwable, List[Haku]] = {
    Right(hakuOids.flatMap { hakuOid =>
      getHakuFixture(hakuOid).toList.filter {_.julkaistu}.map(toHaku(_).copy(oid = hakuOid))
    })
  }
  override def getHakukohdes(oids: Seq[String]): Either[Throwable, Seq[Hakukohde]] ={
    sequence(for{oid <- oids.toStream} yield getHakukohde(oid))
  }
  override def getHakukohde(oid: String): Either[Throwable, Hakukohde] ={
    val hakuOid = hakuOids.head
    // TODO: Saner / more working test data
    if (activeFixture == toinenAsteYhteishaku || activeFixture == toinenAsteErillishakuEiSijoittelua) {
      Right(Hakukohde(oid, hakuOid, Seq(), List("koulu.tus.oid"), "AMMATILLINEN_PERUSKOULUTUS", "TUTKINTO_OHJELMA",
        Map("kieli_fi" -> "Lukion ilmaisutaitolinja"), Map("fi" -> "Kallion lukio"), YhdenPaikanSaanto(false, "testihakukohde"), false, "kausi_k#1", 2016))
    } else {
      Right(Hakukohde(oid, hakuOid, Seq(), List("koulu.tus.oid"), "KORKEAKOULUTUS", "TUTKINTO",
        Map("kieli_fi" -> "Lukion ilmaisutaitolinja"), Map("fi" -> "Kallion lukio"), YhdenPaikanSaanto(
          activeFixture != korkeakouluErillishakuEiYhdenPaikanSaantoa, "testihakukohde"), true, "kausi_k#1", 2016))
    }
  }

  override def getHakukohdeOids(hakuOid: String): Either[Throwable, Seq[String]] = Right(List(
    "1.2.246.562.14.2013120515524070995659",
    "1.2.246.562.14.2014022408541751568934",
    "1.2.246.562.20.42476855715",
    "1.2.246.562.20.93395603447",
    "1.2.246.562.20.99933864235",
    "1.2.246.562.5.16303028779",
    "1.2.246.562.5.72607738902",
    "1.2.246.562.5.72607738903",
    "1.2.246.562.5.72607738904"
  ))

  override def getArbitraryPublishedHakukohdeOid(hakuOid: String): Either[Throwable, String] =
    getHakukohdeOids(hakuOid).right.map(_.head)

  private def sequence[A, B](xs: Stream[Either[B, A]]): Either[B, List[A]] = xs match {
    case Stream.Empty => Right(Nil)
    case Left(e)#::_ => Left(e)
    case Right(x)#::rest => sequence(rest).right.map(x +: _)
  }
}
