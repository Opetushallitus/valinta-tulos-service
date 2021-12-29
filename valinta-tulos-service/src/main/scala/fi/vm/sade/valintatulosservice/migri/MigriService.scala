package fi.vm.sade.valintatulosservice.migri

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.oppijanumerorekisteri.{Henkilo, OppijanumerorekisteriService}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakijaOid

class MigriService(oppijanumerorekisteriService: OppijanumerorekisteriService) extends Logging {

  private def fetchHenkilotFromONR(hakijaOids: Set[HakijaOid]): Set[Henkilo] = {
    oppijanumerorekisteriService.henkilot(hakijaOids) match {
      case Right(h) => h.values.toSet
      case Left(_) => throw new IllegalArgumentException(s"No hakijas found for oid: $hakijaOids found.")
    }
  }

  def fetchHakemuksetByHakijaOid(hakijaOids: Set[HakijaOid]): Set[Hakija] = {
    val hakijat: Set[Hakija] = fetchHenkilotFromONR(hakijaOids).map(henkilo =>
      fi.vm.sade.valintatulosservice.migri.Hakija(
        henkilotunnus = henkilo.hetu.getOrElse("").toString,
        henkiloOid = henkilo.oid.toString,
        sukunimi = henkilo.sukunimi.getOrElse(""),
        etunimet = henkilo.etunimet.getOrElse(""),
        kansalaisuudet = henkilo.kansalaisuudet.getOrElse(List()),
        syntymaaika = henkilo.syntymaaika.getOrElse("")
        , Seq()
      )
    ).filterNot(hakija => hakija.kansalaisuudet.contains("246"))
    //Suomi: 246
    //      [
    //        "1.2.246.562.24.53551096594",
    //        "1.2.246.562.24.88173955981",
    //        "1.2.246.562.24.57953701739",
    //        "1.2.246.562.24.79062047896"
    //      ]
    logger.info("HAKIJAT: " + hakijat.toString)

    hakijat
  }
}
