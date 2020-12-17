package fi.vm.sade.valintatulosservice.performance

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.ValintarekisteriAppConfig
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Random, Try}

object VastaanottoStoreTester extends App with Logging {
  val appConfig = new ValintarekisteriAppConfig.IT
  appConfig.start
  val valintarekisteriDb = new ValintarekisteriDb(appConfig.settings.valintaRekisteriDbConfig)
  private val henkiloOid = "1.2.246.562.24.0000000000"
  private val hakemusOid = HakemusOid("1.2.246.562.99.00000000001")
  private val hakukohdeOid = HakukohdeOid("1.2.246.561.20.00000000001")
  private val hakuOid = HakuOid("1.2.246.561.29.00000000001")

  valintarekisteriDb.storeHakukohde(YPSHakukohde(hakukohdeOid, hakuOid, Kevat(2015)))

  val r = Random
  val concurrency = 60
  val errors = new AtomicInteger(0)
  val runtimes = (1 to 20).map(_ => {
    val start = System.currentTimeMillis()
    Await.result(
      Future.sequence(
        (1 to concurrency).map(i =>
          Future {
            Thread.sleep(r.nextInt(10))
            if (
              Try(
                valintarekisteriDb.store(
                  HakijanVastaanotto(henkiloOid + i, hakemusOid, hakukohdeOid, VastaanotaSitovasti)
                )
              ).isFailure
            ) {
              errors.incrementAndGet()
            }
          }
        )
      ),
      Duration(60, TimeUnit.SECONDS)
    )
    System.currentTimeMillis() - start
  })
  Thread.sleep(2000)
  logger.info(
    s"Average runtime ${runtimes.sum / runtimes.length / concurrency} ms, errors ${errors.get()}"
  )
}
