package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.valintatulosservice.ITSpecification
import fi.vm.sade.valintatulosservice.domain.{Hakemus, Hakutoive, Henkilotiedot}
import fi.vm.sade.valintatulosservice.hakemus.{HakemusFixtures, HakemusRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid}
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class HakemusRepositorySpec extends ITSpecification {
  val repo = new HakemusRepository()

  "HakemusRepository" should {
    "palauttaa yksittäisen Hakemuksen" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakemusFixtures = HakemusFixtures.defaultFixtures)

      val hakutoiveet = repo.findHakemus(HakemusOid("1.2.246.562.11.00000878229"))
      hakutoiveet must beRight(Hakemus(HakemusOid("1.2.246.562.11.00000878229"), HakuOid("1.2.246.562.29.92478804245"), "1.2.246.562.24.14229104472", "FI",
              List(Hakutoive(HakukohdeOid("1.2.246.562.20.83060182827"), "1.2.246.562.10.83122281013", "stevari amk hakukohde", "Saimaan ammattikorkeakoulu, Skinnarilan kampus, Lappeenranta"),
                Hakutoive(HakukohdeOid("1.2.246.562.10.83122281012"), "1.2.246.562.10.83122281012", "", "")),
              Henkilotiedot(Some("Teppo"), None, true)
            ))
    }

    "palauttaa kaikki Hakuun liittyvät Hakemukset" in {
      val hakemukset = repo.findHakemukset(HakuOid("1.2.246.562.5.2013080813081926341928")).toList
      hakemukset must_== Seq(
        Hakemus(HakemusOid("1.2.246.562.11.00000441369"), HakuOid("1.2.246.562.5.2013080813081926341928"), "1.2.246.562.24.14229104472", "FI",
          List(
            Hakutoive(HakukohdeOid("1.2.246.562.5.72607738902"), "1.2.246.562.10.591352080610", "stevari amk hakukohde", "Saimaan ammattikorkeakoulu, Skinnarilan kampus, Lappeenranta"),
            Hakutoive(HakukohdeOid("1.2.246.562.5.16303028779"), "1.2.246.562.10.455978782510", "", "")
          ),
          Henkilotiedot(Some("Teppo"), Some("teppo@testaaja.fi"), true)),
        Hakemus(HakemusOid("1.2.246.562.11.00000441370"), HakuOid("1.2.246.562.5.2013080813081926341928"), "1.2.246.562.24.14229104472", "FI",
          List(
            Hakutoive(HakukohdeOid("1.2.246.562.5.72607738902"), "1.2.246.562.10.591352080610", "stevari amk hakukohde", "Saimaan ammattikorkeakoulu, Skinnarilan kampus, Lappeenranta"),
            Hakutoive(HakukohdeOid("1.2.246.562.20.83060182827"), "1.2.246.562.10.83122281013", "", "")
          ),
          Henkilotiedot(Some("Teppo"),None,true)),
        Hakemus(HakemusOid("1.2.246.562.11.00000441371"), HakuOid("1.2.246.562.5.2013080813081926341928"), "1.2.246.562.24.14229104472", "FI",
          List(
            Hakutoive(HakukohdeOid("1.2.246.562.5.72607738902"), "1.2.246.562.10.591352080610", "stevari amk hakukohde", "Saimaan ammattikorkeakoulu, Skinnarilan kampus, Lappeenranta"),
            Hakutoive(HakukohdeOid("1.2.246.562.20.83060182827"), "1.2.246.562.10.83122281013", "", "")
          ),
          Henkilotiedot(Some("Teppo"),None,true))
      )
    }

    "palauttaa kaikki henkilön tiettyyn hakuun liittyvät Hakemukset" in {
      val hakemukset = repo.findHakemukset(HakuOid("korkeakoulu-lisahaku1"), "1.2.246.562.24.14229104472").toList
      hakemukset must_== List(Hakemus(HakemusOid("1.2.246.562.11.00000878230"), HakuOid("korkeakoulu-lisahaku1"), "1.2.246.562.24.14229104472", "FI",
          List(Hakutoive(HakukohdeOid("1.2.246.562.14.2013120515524070995659"), "1.2.246.562.10.83122281013", "stevari amk hakukohde", "Saimaan ammattikorkeakoulu, Skinnarilan kampus, Lappeenranta"),
            Hakutoive(HakukohdeOid("1.2.246.562.14.2014022408541751568934"), "1.2.246.562.10.83122281012", "", "")),
        Henkilotiedot(Some("Teppo"), None, true))
      )
    }

    "palauttaa yksittäisen Hakemuksen jolla on eri asiointikieli" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakemusFixtures = HakemusFixtures.defaultFixtures)

      val hakutoiveet = repo.findHakemus(HakemusOid("1.2.246.562.11.00000878229-SE"))
      hakutoiveet must beRight(Hakemus(HakemusOid("1.2.246.562.11.00000878229-SE"), HakuOid("1.2.246.562.29.92478804245"), "1.2.246.562.24.14229104472", "SV",
              List(Hakutoive(HakukohdeOid("1.2.246.562.20.83060182827"), "1.2.246.562.10.83122281013", "stevari amk hakukohde", "Saimaan ammattikorkeakoulu, Skinnarilan kampus, Lappeenranta"),
                Hakutoive(HakukohdeOid("1.2.246.562.10.83122281012"), "1.2.246.562.10.83122281012", "", "")),
              Henkilotiedot(Some("Teppo"), None, true)
            ))
    }
  }
}
