package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.valintarekisteri.db.{SijoitteluRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakuOid, HakukohdeTiedot, HaunHakukohdeTiedot}

class ValintojenToteuttaminenService (val repository: ValinnantulosRepository
    with SijoitteluRepository) {

    def getJulkaisemattomatTaiSijoittelemattotHakukohteetHaulle(hakuOid: HakuOid): HaunHakukohdeTiedot = {
      val julkaisemattomat = repository.getHaunJulkaisemattomatHakukohteet(hakuOid)
      val sijoittelemattomat = repository.getHaunSijoittelemattomatHakukohteet(hakuOid)
      val hakukohdeTiedot = (julkaisemattomat ++ sijoittelemattomat).map(hk =>
        HakukohdeTiedot(hk, sijoittelemattomat.contains(hk), julkaisemattomat.contains(hk))
      )
      HaunHakukohdeTiedot(hakuOid, hakukohdeTiedot)
    }
}
