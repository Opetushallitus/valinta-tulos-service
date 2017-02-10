package fi.vm.sade.valintatulosservice.kela

import java.util.Date

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.migraatio.vastaanotot.MissingHakijaOidResolver
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{VastaanottoRecord, ValintarekisteriService}
import org.scalatra.swagger.Swagger

import scala.concurrent.{Future, Await}
import scala.util.Try
import scala.concurrent.duration._

class KelaService(valintarekisteriService: ValintarekisteriService)(implicit val appConfig: VtsAppConfig) {
  private val missingHakijaOidResolver = new MissingHakijaOidResolver(appConfig)

  def fetchVastaanototForPersonWithHetu(hetu: String, alkaen: Option[Date]): Option[Henkilo] = {
    missingHakijaOidResolver.findPersonByHetu(hetu, 5 seconds) match {
      case Some(henkilo) =>
        val vastaanotot = valintarekisteriService.findHenkilonVastaanotot(henkilo.oidHenkilo, alkaen)

        vastaanotot.map(vastaanotto => {

          Vastaanotto(
            tutkintotyyppi = "",
            organisaatio = "",
            oppilaitos = "",
            hakukohde = vastaanotto.hakukohdeOid,
            tutkinnonlaajuus1 = "",
            tutkinnonlaajuus2 = None,
            tutkinnontaso = None,
            vastaaottoaika = "",
            alkamiskausipvm = ""
          )
        })

        None
      case _ =>
        None
    }


  }

}
