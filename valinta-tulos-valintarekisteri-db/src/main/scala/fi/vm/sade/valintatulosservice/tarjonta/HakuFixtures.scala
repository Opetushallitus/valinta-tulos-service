package fi.vm.sade.valintatulosservice.tarjonta

import java.io.InputStream

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakuOid, HakukohdeOid}
import fi.vm.sade.valintatulosservice.MonadHelper
import fi.vm.sade.valintatulosservice.config.AppConfig
import org.json4s.jackson.JsonMethods._

object HakuFixtures extends HakuService with JsonHakuService {
  val defaultHakuOid = HakuOid("1.2.246.562.5.2013080813081926341928")
  val korkeakouluYhteishaku = HakuOid("korkeakoulu-yhteishaku")
  val korkeakouluLisahaku1 = HakuOid("korkeakoulu-lisahaku1")
  val korkeakouluErillishaku = HakuOid("korkeakoulu-erillishaku")
  val korkeakouluErillishakuEiSijoittelua = HakuOid("korkeakoulu-erillishaku-ei-sijoittelua")
  val toinenAsteYhteishaku = HakuOid("toinen-aste-yhteishaku")
  val toinenAsteErillishakuEiSijoittelua = HakuOid("toinen-aste-erillishaku-ei-sijoittelua")
  val korkeakouluErillishakuEiYhdenPaikanSaantoa = HakuOid("korkeakoulu-erillishaku-ei-yhden-paikan-saantoa")
  val ataruHaku = HakuOid("ataru-haku")

  private var hakuOids = List(defaultHakuOid)
  private var activeFixture = korkeakouluYhteishaku
  var config: AppConfig = _

  def useFixture(fixtureName: HakuOid, hakuOids: List[HakuOid] = List(defaultHakuOid)) {
    this.hakuOids = hakuOids
    this.activeFixture = fixtureName
  }

  override def getHaku(oid: HakuOid): Either[Throwable, Haku] = {
    getHakuFixture(oid).map(toHaku(_, config).copy(oid = oid)).toRight(new IllegalArgumentException(s"No haku $oid found"))
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

  private def getFixtureAsStream(hakuOid: HakuOid) = {
    Option(getClass.getResourceAsStream("/fixtures/tarjonta/haku/" + hakuOid.toString + ".json"))
  }

  override def kaikkiJulkaistutHaut: Either[Throwable, List[Haku]] = {
    Right(hakuOids.flatMap { hakuOid =>
      getHakuFixture(hakuOid).toList.filter {_.julkaistu}.map(toHaku(_, config).copy(oid = hakuOid))
    })
  }

  override def getHakukohdeKela(oid: HakukohdeOid): Either[Throwable, Option[HakukohdeKela]] = {
    Left(new UnsupportedOperationException("Not implemented"))
  }
  override def getHakukohdes(oids: Seq[HakukohdeOid]): Either[Throwable, Seq[Hakukohde]] ={
    MonadHelper.sequence(for {oid <- oids.toStream} yield getHakukohde(oid))
  }
  override def getHakukohde(oid: HakukohdeOid): Either[Throwable, Hakukohde] ={
    val hakuOid = hakuOids.head
    // TODO: Saner / more working test data
    if (activeFixture == HakuFixtures.toinenAsteYhteishaku || activeFixture == HakuFixtures.toinenAsteErillishakuEiSijoittelua) {
      Right(Hakukohde(
        oid,
        hakuOid,
        Set("123.123.123.123"),
        "AMMATILLINEN_PERUSKOULUTUS",
        Map("kieli_fi" -> "Lukion ilmaisutaitolinja"),
        Map("fi" -> "Kallion lukio"),
        YhdenPaikanSaanto(false, "testihakukohde"),
        false,
        Some("kausi_k#1"),
        Some(2016),
        organisaatioRyhmaOids = Set()))
    } else if (activeFixture == HakuFixtures.ataruHaku) {
      Right(Hakukohde(
        oid,
        hakuOid,
        Set("1.2.246.562.10.72985435253"),
        "KORKEAKOULUTUS",
        Map("kieli_fi" -> "Ataru testihakukohde"),
        Map("fi" -> "Aalto-yliopisto, Insinööritieteiden korkeakoulu"),
        YhdenPaikanSaanto(true, "Haun kohdejoukon tarkenne on 'haunkohdejoukontarkenne_3#1'"),
        true,
        Some("kausi_s#1"),
        Some(2017),
        organisaatioRyhmaOids = Set()))
    } else {
      Right(Hakukohde(
        oid,
        hakuOid,
        Set("123.123.123.123"),
        "KORKEAKOULUTUS",
        Map("kieli_fi" -> "Lukion ilmaisutaitolinja"),
        Map("fi" -> "Kallion lukio"),
        YhdenPaikanSaanto(activeFixture != HakuFixtures.korkeakouluErillishakuEiYhdenPaikanSaantoa, "testihakukohde"),
        true,
        Some("kausi_k#1"),
        Some(2016),
        organisaatioRyhmaOids = Set()))
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
}
