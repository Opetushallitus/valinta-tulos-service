package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.{HakijaDTO, HakijaPaginationObject, HakutoiveDTO, HakutoiveenValintatapajonoDTO}
import fi.vm.sade.valintatulosservice.SijoittelunTulostenOrganisaatiorajaus
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakukohdeOid
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import java.util
import scala.collection.JavaConverters._

@RunWith(classOf[JUnitRunner])
class SijoittelunTulostenOrganisaatiorajausSpec extends Specification {

  val omaHakukohde = HakukohdeOid("1.2.246.562.20.1")
  val vierasHakukohde = HakukohdeOid("1.2.246.562.20.2")
  val sallittu: HakukohdeOid => Boolean = _ == omaHakukohde

  "SijoittelunTulostenOrganisaatiorajaus" should {
    "poistaa hakijalta hakutoiveet, joihin ei ole oikeutta" in {
      val tulos = SijoittelunTulostenOrganisaatiorajaus.rajaa(
        pagination(hakija("h1", toive(1, omaHakukohde, HakemuksenTila.HYVAKSYTTY), toive(2, vierasHakukohde, HakemuksenTila.HYVAKSYTTY))),
        sallittu, vainHyvaksytyt = true)
      tulos.getTotalCount must_== 1
      hakukohteet(tulos, "h1") must_== List(omaHakukohde.toString)
    }

    "pudottaa hakijan, jolla ei ole hakutoiveita sallituissa hakukohteissa" in {
      val tulos = SijoittelunTulostenOrganisaatiorajaus.rajaa(
        pagination(
          hakija("h1", toive(1, vierasHakukohde, HakemuksenTila.HYVAKSYTTY)),
          hakija("h2", toive(1, omaHakukohde, HakemuksenTila.HYVAKSYTTY))),
        sallittu, vainHyvaksytyt = true)
      tulos.getTotalCount must_== 1
      hakemusOidit(tulos) must_== List("h2")
    }

    "pudottaa hyväksyttyjen listauksesta hakijan, jonka hyväksyntä on vain vieraassa hakukohteessa" in {
      val tulos = SijoittelunTulostenOrganisaatiorajaus.rajaa(
        pagination(hakija("h1", toive(1, omaHakukohde, HakemuksenTila.HYLATTY), toive(2, vierasHakukohde, HakemuksenTila.HYVAKSYTTY))),
        sallittu, vainHyvaksytyt = true)
      tulos.getTotalCount must_== 0
      tulos.getResults.isEmpty must beTrue
    }

    "säilyttää hakijan, jonka hyväksyntä on varasijalta omassa hakukohteessa" in {
      val tulos = SijoittelunTulostenOrganisaatiorajaus.rajaa(
        pagination(hakija("h1", toive(1, omaHakukohde, HakemuksenTila.VARASIJALTA_HYVAKSYTTY), toive(2, vierasHakukohde, HakemuksenTila.HYVAKSYTTY))),
        sallittu, vainHyvaksytyt = true)
      hakemusOidit(tulos) must_== List("h1")
      hakukohteet(tulos, "h1") must_== List(omaHakukohde.toString)
    }

    "säilyttää ilman hyväksyntää olevan hakijan, kun ei listata vain hyväksyttyjä" in {
      val tulos = SijoittelunTulostenOrganisaatiorajaus.rajaa(
        pagination(hakija("h1", toive(1, omaHakukohde, HakemuksenTila.HYLATTY), toive(2, vierasHakukohde, HakemuksenTila.HYVAKSYTTY))),
        sallittu, vainHyvaksytyt = false)
      hakemusOidit(tulos) must_== List("h1")
      hakukohteet(tulos, "h1") must_== List(omaHakukohde.toString)
    }

    "palauttaa tyhjän listauksen tyhjästä syötteestä" in {
      val tulos = SijoittelunTulostenOrganisaatiorajaus.rajaa(pagination(), sallittu, vainHyvaksytyt = true)
      tulos.getTotalCount must_== 0
      tulos.getResults.isEmpty must beTrue
    }
  }

  private def pagination(hakijat: HakijaDTO*): HakijaPaginationObject = {
    val p = new HakijaPaginationObject
    p.setResults(new util.ArrayList[HakijaDTO](hakijat.asJava))
    p.setTotalCount(hakijat.size)
    p
  }

  private def hakija(hakemusOid: String, toiveet: HakutoiveDTO*): HakijaDTO = {
    val h = new HakijaDTO
    h.setHakemusOid(hakemusOid)
    val s = new util.TreeSet[HakutoiveDTO]()
    s.addAll(toiveet.asJava)
    h.setHakutoiveet(s)
    h
  }

  private def toive(prioriteetti: Int, hakukohdeOid: HakukohdeOid, tila: HakemuksenTila): HakutoiveDTO = {
    val t = new HakutoiveDTO
    t.setHakutoive(prioriteetti)
    t.setHakukohdeOid(hakukohdeOid.toString)
    val jono = new HakutoiveenValintatapajonoDTO
    jono.setValintatapajonoOid(s"jono-$prioriteetti")
    jono.setTila(tila)
    t.setHakutoiveenValintatapajonot(List(jono).asJava)
    t
  }

  private def hakemusOidit(p: HakijaPaginationObject): List[String] = p.getResults.asScala.map(_.getHakemusOid).toList

  private def hakukohteet(p: HakijaPaginationObject, hakemusOid: String): List[String] =
    p.getResults.asScala.find(_.getHakemusOid == hakemusOid).get.getHakutoiveet.asScala.map(_.getHakukohdeOid).toList
}
