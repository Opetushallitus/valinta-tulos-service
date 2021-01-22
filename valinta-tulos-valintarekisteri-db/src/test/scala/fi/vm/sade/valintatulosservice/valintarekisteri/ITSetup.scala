package fi.vm.sade.valintatulosservice.valintarekisteri

import com.typesafe.config.ConfigValueFactory
import fi.vm.sade.valintatulosservice.config.ValintarekisteriAppConfig
import fi.vm.sade.valintatulosservice.ohjausparametrit.StubbedOhjausparametritService
import fi.vm.sade.valintatulosservice.organisaatio.OrganisaatioService
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, ValintatapajonoOid}
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import slick.jdbc.{GetResult, PositionedParameters, SetParameter}

trait ITSetup {
  implicit val appConfig = new ValintarekisteriAppConfig.IT
  val dbConfig = appConfig.settings.valintaRekisteriDbConfig

  lazy val singleConnectionValintarekisteriDb = new ValintarekisteriDb(
    dbConfig.copy(maxConnections = Some(1), minConnections = Some(1)))

  lazy val valintarekisteriDbWithPool = new ValintarekisteriDb(dbConfig, true)

  lazy private val hakuService = HakuService(appConfig, null, new StubbedOhjausparametritService(), OrganisaatioService(appConfig), null)
  lazy val hakukohdeRecordService = new HakukohdeRecordService(hakuService, singleConnectionValintarekisteriDb, appConfig.settings.lenientTarjontaDataParsing)

  implicit val getHakukohdeOid: GetResult[HakukohdeOid] = GetResult(r => {
    HakukohdeOid(r.nextString())
  })

  implicit val getHakemusOid: GetResult[HakemusOid] = GetResult(r => {
    HakemusOid(r.nextString())
  })

  implicit object SetHakuOid extends SetParameter[HakuOid] {
    def apply(o: HakuOid, pp: PositionedParameters) {
      pp.setString(o.toString)
    }
  }

  implicit object SetHakukohdeOid extends SetParameter[HakukohdeOid] {
    def apply(o: HakukohdeOid, pp: PositionedParameters) {
      pp.setString(o.toString)
    }
  }

  implicit object SetValintatapajonoOid extends SetParameter[ValintatapajonoOid] {
    def apply(o: ValintatapajonoOid, pp: PositionedParameters) {
      pp.setString(o.toString)
    }
  }

  implicit object SetHakemusOid extends SetParameter[HakemusOid] {
    def apply(o: HakemusOid, pp: PositionedParameters) {
      pp.setString(o.toString)
    }
  }
}
