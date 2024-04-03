package fi.vm.sade.valintatulosservice.testenvironment

import java.io.File
import fi.vm.sade.utils.cas.CasClient
import fi.vm.sade.valintatulosservice.config.VtsAppConfig
import fi.vm.sade.valintatulosservice.domain.Hakemuksentulos
import fi.vm.sade.valintatulosservice.koodisto.{KoodistoService, RemoteKoodistoService}
import fi.vm.sade.valintatulosservice.ohjausparametrit.RemoteOhjausparametritService
import fi.vm.sade.valintatulosservice.organisaatio.OrganisaatioService
import fi.vm.sade.valintatulosservice.sijoittelu.SijoittelutulosService
//import fi.vm.sade.valintatulosservice.sijoittelu.legacymongo.{SijoitteluSpringContext, SijoittelunTulosRestClient, StreamingHakijaDtoClient}
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakemusOid
import fi.vm.sade.valintatulosservice.{ITSpecification, ValintatulosService}
import org.junit.runner.RunWith
import org.specs2.execute._
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ValintatulosIntegrationTest extends ITSpecification {
  "in luokka environment" should {
    "return valintatulos for " in {
      val varsFile = "ENVIRONMENT OPHITEST PATH HERE/deploy/ophitest_vars.yml"
      if (new File(varsFile).exists()) {
        implicit val appConfig = new VtsAppConfig.LocalTestingWithTemplatedVars(varsFile)
        val hakuService = HakuService(appConfig, new RemoteOhjausparametritService(appConfig), OrganisaatioService(appConfig), new RemoteKoodistoService(appConfig))
        //lazy val sijoitteluContext = new SijoitteluSpringContext(appConfig, SijoitteluSpringContext.createApplicationContext(appConfig))
        /*val sijoittelutulosService = new SijoittelutulosService(sijoitteluContext.raportointiService, appConfig.ohjausparametritService, null,
          SijoittelunTulosRestClient(sijoitteluContext, appConfig))
        val valintatulosService = new ValintatulosService(null, sijoittelutulosService, null, hakuService, null, null, null, new StreamingHakijaDtoClient(appConfig))*/

        val tulos: Hakemuksentulos = null//valintatulosService.hakemuksentulos(HakemusOid("1.2.246.562.11.00000000330")).get

        tulos.hakutoiveet.length must_== 2
      } else {
        throw SkipException(Skipped("Variables file not found at " + varsFile))
      }
    }
  }
}
