package fi.vm.sade.valintatulosservice.siirtotiedosto

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.{SiirtotiedostoIlmoittautuminen, SiirtotiedostoPagingParams, SiirtotiedostoVastaanotto}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{SiirtotiedostoRepository, SijoitteluRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila.Vastaanottotila
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, Valinnantulos, ValintatapajonoRecord}

import scala.annotation.tailrec
class SiirtotiedostoService(siirtotiedostoRepository: SiirtotiedostoRepository, valinnantulosRepository: ValinnantulosRepository) extends Logging {

  //Todo, actually save.
  def saveSiirtotiedosto(params: SiirtotiedostoPagingParams) = {
    logger.info(s"Saving siirtotiedosto... $params")
  }

  private def ilmoittautumisetSize = 50000

  private def vastaanototSize = 50000

  private def valintatapajonotSize = 50000


  @tailrec
  private def saveInSiirtotiedostoPaged[T](params: SiirtotiedostoPagingParams, pageFun: SiirtotiedostoPagingParams => List[T]): Option[Exception] = {
    val pageResults = pageFun(params)
    if (pageResults.isEmpty) {
      None
    } else {
      saveSiirtotiedosto(params)
      saveInSiirtotiedostoPaged(params.copy(offset = params.offset + pageResults.size), pageFun)
    }
  }

  def muodostaJaTallennaSiirtotiedostot(start: String, end: String): Map[HakukohdeOid, Set[Valinnantulos]] = {
    val hakukohteet = siirtotiedostoRepository.getChangedHakukohdeoidsForValinnantulokses(start, end)
    logger.info(s"Saatiin ${hakukohteet.size} muuttunutta hakukohdetta välille $start - $end")

    val tulokset = hakukohteet.take(10).map(hakukohde => hakukohde ->
      valinnantulosRepository.runBlocking(valinnantulosRepository.getValinnantuloksetForHakukohde(hakukohde))).toMap
    //val jonotResult = saveValintatapajonotPaged(start, end, 0, 10000)
    logger.info(s"Saatiin tuloksia hakukohteille, eka: ${tulokset.take(1)}")

    val vastaanototResult = saveInSiirtotiedostoPaged[SiirtotiedostoVastaanotto](SiirtotiedostoPagingParams("vastaanotto", start, end, 0, vastaanototSize),
      params => siirtotiedostoRepository.getVastaanototPage(params))
    val ilmoittautumisetResult = saveInSiirtotiedostoPaged[SiirtotiedostoIlmoittautuminen](SiirtotiedostoPagingParams("ilmoittautuminen", start, end, 0, ilmoittautumisetSize),
      params => siirtotiedostoRepository.getIlmoittautumisetPage(params))
    val valintatapajonotResult = saveInSiirtotiedostoPaged[ValintatapajonoRecord](SiirtotiedostoPagingParams("valintatapajono", start, end, 0, valintatapajonotSize),
      params => siirtotiedostoRepository.getValintatapajonotPage(params))

    //Todo, ei tarvitse palauttaa muuta kuin mahdollinen virhe, muuten riittää että tarvittavat tiedot on tallennettu s3:seen.
    tulokset
  }
}
