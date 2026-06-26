package fi.vm.sade.valintatulosservice.lokalisointi

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.TranslatedName

object TranslationUtil {

  def getMatchingTranslation(translatedName: TranslatedName, lang: String): String = {
    val translation = lang match {
      case "fi" =>
        translatedName.fi
      case "sv" =>
        translatedName.sv
      case "en" =>
        translatedName.en
    }
    (translation == null || translation.isBlank, lang) match {
      case (false, _) =>
        translation
      case (_, "fi") if translatedName.en != null && translatedName.en.nonEmpty =>
        translatedName.en
      case (_, "fi") if translatedName.sv != null && translatedName.sv.nonEmpty =>
        translatedName.sv
      case (_, "en") if translatedName.fi != null && translatedName.fi.nonEmpty =>
        translatedName.fi
      case (_, "en") if translatedName.sv != null && translatedName.sv.nonEmpty =>
        translatedName.sv
      case (_, "sv") if translatedName.fi != null && translatedName.fi.nonEmpty =>
        translatedName.fi
      case (_, "sv") if translatedName.en != null && translatedName.en.nonEmpty =>
        translatedName.en
      case _ => ""
    }
  }
}
