package fi.vm.sade.valintatulosservice.lokalisointi

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.TranslatedName

object TranslationUtil {

  def getMatchingTranslation(translatedName: TranslatedName, lang: String): String = {
    val translationLookup = lang match {
      case "fi" =>
        List(translatedName.fi, translatedName.en, translatedName.sv)
      case "sv" =>
        List(translatedName.sv, translatedName.fi, translatedName.en)
      case "en" =>
        List(translatedName.en, translatedName.fi, translatedName.sv)
    }
    translationLookup.find(t => t != null && !t.isBlank).getOrElse("")
  }
}
