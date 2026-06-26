package fi.vm.sade.valintatulosservice.lokalisointi

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.TranslatedName
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class TranslationUtilSpec extends Specification {

  private def ruhtinas: TranslatedName = TranslatedName("Ruhtinas Nukettaja", "Prins Dockor", "Duke Nukem")

  "getMatchingTranslation" should {
    "translate name in finnish" in {
      TranslationUtil.getMatchingTranslation(ruhtinas, "fi") must_== "Ruhtinas Nukettaja"
    }

    "translate name in swedish" in {
      TranslationUtil.getMatchingTranslation(ruhtinas, "sv") must_== "Prins Dockor"
    }

    "translate name in english" in {
      TranslationUtil.getMatchingTranslation(ruhtinas, "en") must_== "Duke Nukem"
    }

    "translate name uses english when finnish missing" in {
      TranslationUtil.getMatchingTranslation(ruhtinas.copy(fi = ""), "fi") must_== "Duke Nukem"
      TranslationUtil.getMatchingTranslation(ruhtinas.copy(fi = null), "fi") must_== "Duke Nukem"
    }

    "translate name uses finnish when swedish missing" in {
      TranslationUtil.getMatchingTranslation(ruhtinas.copy(sv = ""), "sv") must_== "Ruhtinas Nukettaja"
      TranslationUtil.getMatchingTranslation(ruhtinas.copy(sv = null), "sv") must_== "Ruhtinas Nukettaja"
    }

    "translate name uses finnish when english missing" in {
      TranslationUtil.getMatchingTranslation(ruhtinas.copy(en = ""), "en") must_== "Ruhtinas Nukettaja"
      TranslationUtil.getMatchingTranslation(ruhtinas.copy(en = null), "en") must_== "Ruhtinas Nukettaja"
    }

    "translate name uses swedish when finnish and english missing" in {
      TranslationUtil.getMatchingTranslation(ruhtinas.copy(fi = "", en = ""), "fi") must_== "Prins Dockor"
      TranslationUtil.getMatchingTranslation(ruhtinas.copy(fi = null, en = null), "fi") must_== "Prins Dockor"
    }

    "translate name uses english when swedish and finnish missing" in {
      TranslationUtil.getMatchingTranslation(ruhtinas.copy(sv = "", fi = ""), "sv") must_== "Duke Nukem"
      TranslationUtil.getMatchingTranslation(ruhtinas.copy(sv = null, fi = null), "sv") must_== "Duke Nukem"
    }

    "translate name uses swedish when english and finnish missing" in {
      TranslationUtil.getMatchingTranslation(ruhtinas.copy(en = "", fi = ""), "en") must_== "Prins Dockor"
      TranslationUtil.getMatchingTranslation(ruhtinas.copy(en = null, fi = null), "en") must_== "Prins Dockor"
    }

    "returns empty string when all missing" in {
      TranslationUtil.getMatchingTranslation(TranslatedName(null, null, null), "fi") must_== ""
      TranslationUtil.getMatchingTranslation(TranslatedName(null, null, null), "sv") must_== ""
      TranslationUtil.getMatchingTranslation(TranslatedName(null, null, null), "en") must_== ""
    }
  }

}
