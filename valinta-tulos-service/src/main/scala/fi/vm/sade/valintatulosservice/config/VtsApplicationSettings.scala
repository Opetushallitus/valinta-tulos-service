package fi.vm.sade.valintatulosservice.config

import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.HOURS

import com.typesafe.config.Config
import fi.vm.sade.utils.config.MongoConfig
import fi.vm.sade.valintatulosservice.SecuritySettings
import org.apache.commons.lang3.BooleanUtils

import scala.concurrent.duration.Duration

case class VtsApplicationSettings(config: Config) extends ApplicationSettings(config) {
  val omatsivutUrlEn = withConfig(_.getString("omatsivut.en"))
  val omatsivutUrlFi = withConfig(_.getString("omatsivut.fi"))
  val omatsivutUrlSv = withConfig(_.getString("omatsivut.sv"))
  val oppijanTunnistusUrl = withConfig(_.getString("oppijan-tunnistus-service.url"))
  val hakemusMongoConfig: MongoConfig = getMongoConfig(config.getConfig("hakemus.mongodb"))
  val securitySettings = new SecuritySettings(config)
  val valintaRekisteriEnsikertalaisuusMaxPersonOids = withConfig(_.getInt("valinta-tulos-service.valintarekisteri.ensikertalaisuus.max.henkilo.oids"))
  val lenientSijoitteluntuloksetParsing: Boolean = BooleanUtils.isTrue(withConfig(_.getBoolean("valinta-tulos-service.parseleniently.sijoitteluajontulos")))
  val kelaURL = withConfig(_.getString("valinta-tulos-service.kela.url"))
  val organisaatioServiceUrl = withConfig(_.getString("cas.service.organisaatio-service"))
  val rootOrganisaatioOid = withConfig(_.getString("root.organisaatio.oid"))
  val scheduledMigrationStart = withConfig(_.getInt("valinta-tulos-service.scheduled-migration.start-hour"))
  val scheduledDeleteSijoitteluAjoStart = withConfig(_.getInt("valinta-tulos-service.scheduled-delete-sijoitteluajo.start-hour"))
  val scheduledDeleteSijoitteluAjoLimit = withConfig(_.getInt("valinta-tulos-service.scheduled-delete-sijoitteluajo.limit"))
  val scheduledMigrationEnd = withConfig(_.getInt("valinta-tulos-service.scheduled-migration.end-hour"))
  val oiliHetutonUrl = withConfig(_.getString("omatsivut.oili.hetutonUrl"))
  val readFromValintarekisteri = BooleanUtils.isTrue(withConfig(_.getBoolean("valinta-tulos-service.read-from-valintarekisteri")))
  val ilmoittautuminenEnabled = {
    val value = config.getString("valinta-tulos-service.ilmoittautuminen.enabled")
    if(value.trim.length > 0) {
      value.toBoolean
    }
    else {
      false
    }
  }
  val mailPollerConcurrency: Int = withConfig(_.getInt("valinta-tulos-service.mail-poller.concurrency"))
  val mailPollerResultlessHakukohdeRecheckInterval: Duration = Duration(withConfig(
    _.getInt("valinta-tulos-service.mail-poller.resultless.hakukohde.hours")), HOURS)
  val hakukohdeStreamingConcurrency: Int = withConfig(_.getInt("valinta-tulos-service.streaming.hakukohde.concurrency"))
  val hakuResultsLoadingLockSeconds: Int = withConfig(_.getInt("valinta-tulos-service.streaming.lock.timeout.seconds"))
  val hakuResultsLoadingLockQueueLimit: Int = withConfig(_.getInt("valinta-tulos-service.streaming.lock.queue.limit"))
  val ataruHakemusEnricherHakukohdeCacheTtl: Duration = Duration(withConfig(_.getInt("valinta-tulos-service.ataru-hakemus-enricher-hakukohde-cache.ttl.seconds")), TimeUnit.SECONDS)
  val ataruHakemusEnricherHakukohdeCacheMaxSize: Long = 3000

  val emailerCronString: String = withConfig(_.getString("valinta-tulos-service.emailer.cron.string"))
}

object VtsApplicationSettingsParser extends fi.vm.sade.utils.config.ApplicationSettingsParser[VtsApplicationSettings] {
  override def parse(config: Config) = VtsApplicationSettings(config)
}
