package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.valintatulosservice.tarjonta.{KelaKoulutus, KoulutusLaajuusarvo}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class KelaKoulutusSpec extends Specification {

  "Koulutus from Tarjonta" should {

    "map 'Alempi'" in {

      KelaKoulutus(Seq(KoulutusLaajuusarvo(None, Some("623701"), None, Some("180")))) must_== Some(KelaKoulutus(Some("050"),Some("180"),None))

    }

    "map 'Ylempi'" in {

        KelaKoulutus(Seq(KoulutusLaajuusarvo(None, Some("799999"), None, Some("120")))) must_== Some(KelaKoulutus(Some("061"),Some("120"),None))

    }

    "map 'Ylempi' and 'Alempi'" in {

      KelaKoulutus(Seq(KoulutusLaajuusarvo(None, Some("799999"), None, Some("180")),
        KoulutusLaajuusarvo(None, Some("623701"), None, Some("120")))) must_== Some(KelaKoulutus(Some("060"),Some("120"),Some("180")))

    }

    "map 'Lääkäri'" in {

      KelaKoulutus(Seq(KoulutusLaajuusarvo(None, Some("772101"), None, Some("180")))) must_== Some(KelaKoulutus(Some("070"),Some("180"),None))
      KelaKoulutus(Seq(KoulutusLaajuusarvo(None, Some("772201"), None, Some("120")))) must_== Some(KelaKoulutus(Some("071"),Some("120"),None))

    }

    "map 'Ylempi' and 'Alempi' with hidden sisältyväkoulutuskoodi" in {

      KelaKoulutus(Seq(
        KoulutusLaajuusarvo(None, Some("623701"), None, Some("180+120")), KoulutusLaajuusarvo(None, Some("726701"), None, None),
        KoulutusLaajuusarvo(None, Some("799999"), None, Some("120")))) must_== Some(KelaKoulutus(Some("060"),Some("180"),Some("120")))

    }

    "map 'Ylempi' and 'Alempi' with hidden sisältyväkoulutuskoodi" in {

      KelaKoulutus(Seq(KoulutusLaajuusarvo(None, Some("751301"), None, Some("120")))) must_== Some(KelaKoulutus(Some("061"),Some("120"), None))

    }

    "map 'Muut' with empty taso&laajuus" in {

      KelaKoulutus(Seq(KoulutusLaajuusarvo(None, Some("0000"), None, Some("120")))) must_== Some(KelaKoulutus(None,None, None))

    }

    "map 'Valma' with empty taso&laajuus" in {

      KelaKoulutus(Seq(KoulutusLaajuusarvo(None, Some("999901"), Some("18"), Some("120")))) must_== Some(KelaKoulutus(Some("005"),None, None))

    }

    "map 'Telma' with empty taso&laajuus" in {

      KelaKoulutus(Seq(KoulutusLaajuusarvo(None, Some("999903"), Some("5"), Some("120")))) must_== Some(KelaKoulutus(Some("006"),None, None))

    }


    "map 'Lukio'" in {

      KelaKoulutus(Seq(KoulutusLaajuusarvo(None, Some("1"), Some("2"), Some("180")))) must_== Some(KelaKoulutus(Some("001"),None,None))
      KelaKoulutus(Seq(KoulutusLaajuusarvo(None, Some("1"), Some("21"), Some("120")))) must_== Some(KelaKoulutus(Some("001"),None,None))

    }

  }

}
