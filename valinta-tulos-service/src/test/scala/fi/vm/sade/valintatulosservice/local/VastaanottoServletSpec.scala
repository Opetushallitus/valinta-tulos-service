package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.valintatulosservice.ServletSpecification
import fi.vm.sade.valintatulosservice.domain.{Hakemuksentulos, Valintatila}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila
import org.json4s.jackson.Serialization
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class VastaanottoServletSpec extends ServletSpecification {
  "POST /vastaanotto" should {
    "vastaanottaa opiskelupaikan" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json")

      vastaanota("VastaanotaSitovasti") {
        status must_== 200

        get("haku/1.2.246.562.5.2013080813081926341928/hakemus/1.2.246.562.11.00000441369") {
          val tulos: Hakemuksentulos = Serialization.read[Hakemuksentulos](body)
          tulos.hakutoiveet.head.vastaanottotila must_== Vastaanottotila.vastaanottanut
          tulos.hakutoiveet.head.viimeisinValintatuloksenMuutos.isDefined must beTrue
          tulos.hakutoiveet.head.viimeisinValintatuloksenMuutos.get.getTime must be ~ (System
            .currentTimeMillis() +/- 4000)
        }
      }
    }

    "peruu opiskelupaikan" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json")

      vastaanota("Peru") {
        status must_== 200

        get("haku/1.2.246.562.5.2013080813081926341928/hakemus/1.2.246.562.11.00000441369") {
          val tulos: Hakemuksentulos = Serialization.read[Hakemuksentulos](body)
          tulos.hakutoiveet.head.vastaanottotila.toString must_== "PERUNUT"
          tulos.hakutoiveet.head.viimeisinValintatuloksenMuutos.isDefined must beTrue
          tulos.hakutoiveet.head.viimeisinValintatuloksenMuutos.get.getTime must be ~ (System
            .currentTimeMillis() +/- 4000)
        }
      }
    }

    "vastaanottaa ehdollisesti" in {
      useFixture("hyvaksytty-ylempi-varalla.json")

      vastaanota("VastaanotaEhdollisesti", hakukohde = "1.2.246.562.5.16303028779") {
        status must_== 200

        get("haku/1.2.246.562.5.2013080813081926341928/hakemus/1.2.246.562.11.00000441369") {
          val tulos: Hakemuksentulos = Serialization.read[Hakemuksentulos](body)
          tulos.hakutoiveet.head.valintatila must_== Valintatila.varalla
          tulos.hakutoiveet.head.vastaanottotila.toString must_== "KESKEN"
          tulos.hakutoiveet.last.vastaanottotila.toString must_== "EHDOLLISESTI_VASTAANOTTANUT"
          val muutosAika = tulos.hakutoiveet.last.viimeisinValintatuloksenMuutos.get
          tulos.hakutoiveet.head.viimeisinValintatuloksenMuutos must beNone
          muutosAika.getTime must be ~ (System.currentTimeMillis() +/- 4000)
        }
      }
    }
  }

  def vastaanota[T](
    action: String,
    hakukohde: String = "1.2.246.562.5.72607738902",
    personOid: String = "1.2.246.562.24.14229104472",
    hakemusOid: String = "1.2.246.562.11.00000441369"
  )(block: => T) = {
    postJSON(
      s"""vastaanotto/henkilo/$personOid/hakemus/$hakemusOid/hakukohde/$hakukohde""",
      s"""{"action":"$action"}"""
    ) {
      block
    }
  }
}
