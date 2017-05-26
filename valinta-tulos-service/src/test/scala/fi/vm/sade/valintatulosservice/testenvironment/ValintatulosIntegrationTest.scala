package fi.vm.sade.valintatulosservice.testenvironment

import java.io.File

import fi.vm.sade.valintatulosservice.{ITSpecification, ValintatulosService}
import fi.vm.sade.valintatulosservice.config.{VtsAppConfig, VtsDynamicAppConfig}
import fi.vm.sade.valintatulosservice.domain.Hakemuksentulos
import fi.vm.sade.valintatulosservice.sijoittelu.legacymongo.{SijoitteluSpringContext, SijoittelunTulosRestClient, StreamingHakijaDtoClient}
import fi.vm.sade.valintatulosservice.sijoittelu.{SijoittelutulosService, ValintarekisteriRaportointiServiceImpl, ValintarekisteriSijoittelunTulosClientImpl, ValintarekisteriValintatulosDaoImpl}
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakemusOid
import org.junit.runner.RunWith
import org.specs2.execute._
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ValintatulosIntegrationTest extends ITSpecification {
  "in luokka environment" should {
    "return valintatulos for " in {
      val varsFile = "ENVIRONMENT OPHITEST PATH HERE/deploy/ophitest_vars.yml"
      if (new File(varsFile).exists()) {
        implicit val appConfig = new VtsAppConfig.LocalTestingWithTemplatedVars(varsFile)
        implicit val dynamicAppConfig: VtsDynamicAppConfig = VtsAppConfig.MockDynamicAppConfig()
        val hakuService = HakuService(appConfig.hakuServiceConfig)
        //lazy val sijoitteluContext = new SijoitteluSpringContext(appConfig, SijoitteluSpringContext.createApplicationContext(appConfig))
        lazy val raportointiService = new ValintarekisteriRaportointiServiceImpl(singleConnectionValintarekisteriDb, new ValintarekisteriValintatulosDaoImpl(singleConnectionValintarekisteriDb))
        lazy val client = new ValintarekisteriSijoittelunTulosClientImpl(singleConnectionValintarekisteriDb, singleConnectionValintarekisteriDb)

        val sijoittelutulosService = new SijoittelutulosService(raportointiService, appConfig.ohjausparametritService, null,
          client)
        val valintatulosService = new ValintatulosService(null, sijoittelutulosService, null, hakuService, null, null, null, new StreamingHakijaDtoClient(appConfig))

        val tulos: Hakemuksentulos = valintatulosService.hakemuksentulos(HakemusOid("1.2.246.562.11.00000000330")).get

        tulos.hakutoiveet.length must_== 2
      } else {
        throw SkipException(Skipped("Variables file not found at " + varsFile))
      }
    }
  }
}
