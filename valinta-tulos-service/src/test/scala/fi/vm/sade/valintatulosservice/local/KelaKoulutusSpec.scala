package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.valintatulosservice.tarjonta.{KelaKoulutus, Koodi, Koulutus, KoulutusLaajuusarvo}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class KelaKoulutusSpec extends Specification {

  "Koulutus from Tarjonta" should {

    "map 'Alempi'" in {

      KelaKoulutus(Seq(KoulutusLaajuusarvo(Some("623701"), Some("180")))) must_== Some(KelaKoulutus(Some("050"),"180",None))

    }

    "map 'Ylempi'" in {

        KelaKoulutus(Seq(KoulutusLaajuusarvo(Some("799999"), Some("120")))) must_== Some(KelaKoulutus(Some("061"),"120",None))

    }

    "map 'Ylempi' and 'Alempi'" in {

      KelaKoulutus(Seq(KoulutusLaajuusarvo(Some("799999"), Some("180")),
        KoulutusLaajuusarvo(Some("623701"), Some("120")))) must_== Some(KelaKoulutus(Some("060"),"120",Some("180")))

    }

    "map 'Lääkäri'" in {

      KelaKoulutus(Seq(KoulutusLaajuusarvo(Some("772101"), Some("180")))) must_== Some(KelaKoulutus(Some("070"),"180",None))
      KelaKoulutus(Seq(KoulutusLaajuusarvo(Some("772201"), Some("120")))) must_== Some(KelaKoulutus(Some("071"),"120",None))

    }

    "map 'Ylempi' and 'Alempi' with hidden sisältyväkoulutuskoodi" in {

      KelaKoulutus(Seq(
        KoulutusLaajuusarvo(Some("623701"), Some("180+120")), KoulutusLaajuusarvo(Some("726701"), None),
        KoulutusLaajuusarvo(Some("799999"), Some("120")))) must_== Some(KelaKoulutus(Some("060"),"180",Some("120")))

    }

    "map 'Ylempi' and 'Alempi' with hidden sisältyväkoulutuskoodi" in {

      KelaKoulutus(Seq(KoulutusLaajuusarvo(Some("751301"), Some("120")))) must_== Some(KelaKoulutus(Some("061"),"120", None))

    }
  }

}
