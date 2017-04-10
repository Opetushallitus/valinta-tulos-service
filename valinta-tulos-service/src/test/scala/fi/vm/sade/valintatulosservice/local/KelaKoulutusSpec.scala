package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.utils.tarjonta.{KoulutusBuilder, TarjontaBuilder}
import fi.vm.sade.valintatulosservice.tarjonta.{KelaKoulutus, Koulutus, Koodi}
import org.specs2.mutable.Specification


class KelaKoulutusSpec extends Specification with TarjontaBuilder{

  "Koulutus from Tarjonta" should {
/*
    "map 'Alempi'" in {
      val k = koulutus.withKoulutuskoodi("623701").withOpintojenLaajuusarvo("180").koulutus


      KelaKoulutus(Seq(k)) must_== Some(KelaKoulutus(Some("050"),"180",None))

    }

    "map 'Ylempi'" in {
        val k = koulutus.withKoulutuskoodi("799999").withOpintojenLaajuusarvo("120").koulutus

        KelaKoulutus(Seq(k)) must_== Some(KelaKoulutus(Some("061"),"120",None))

    }

    "map 'Ylempi' and 'Alempi'" in {
      val k0 = koulutus.withKoulutuskoodi("799999").withOpintojenLaajuusarvo("180").koulutus
      val k1 = koulutus.withKoulutuskoodi("623701").withOpintojenLaajuusarvo("120").koulutus

      KelaKoulutus(Seq(k0,k1)) must_== Some(KelaKoulutus(Some("060"),"120",Some("180")))

    }

    "map 'Lääkäri'" in {
      val k0 = koulutus.withKoulutuskoodi("772101").withOpintojenLaajuusarvo("180").koulutus
      val k1 = koulutus.withKoulutuskoodi("772201").withOpintojenLaajuusarvo("120").koulutus

      KelaKoulutus(Seq(k0)) must_== Some(KelaKoulutus(Some("070"),"180",None))
      KelaKoulutus(Seq(k1)) must_== Some(KelaKoulutus(Some("071"),"120",None))

    }

    "map 'Ylempi' and 'Alempi' with hidden sisältyväkoulutuskoodi" in {
      val k0 = koulutus.withKoulutuskoodi("623701").withOpintojenLaajuusarvo("180+120")
          .withSisaltyvatKoulutuskoodit(Seq("726701")).koulutus

      val ko0 = komo.withKoulutuskoodi("799999").withOpintojenLaajuusarvo("120").komo

      KelaKoulutus(Seq(k0), Seq(ko0)) must_== Some(KelaKoulutus(Some("060"),"180",Some("120")))

    }
*/

    "map 'Ylempi' and 'Alempi' with hidden sisältyväkoulutuskoodi" in {
      val k0 = koulutus.withKoulutuskoodi("751301").withOpintojenLaajuusarvo("120").koulutus


      KelaKoulutus(Seq(k0)) must_== Some(KelaKoulutus(Some("061"),"120", None))

    }
  }

}
