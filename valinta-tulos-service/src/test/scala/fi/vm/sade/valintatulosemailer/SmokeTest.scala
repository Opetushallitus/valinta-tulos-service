package fi.vm.sade.valintatulosemailer

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosemailer.config.EmailerRegistry
import fi.vm.sade.valintatulosemailer.config.EmailerRegistry.{LocalVT, EmailerRegistry}
import org.apache.log4j._
import org.apache.log4j.spi.LoggingEvent
import org.junit.runner.RunWith
import org.scalatra.test.HttpComponentsClient
import org.specs2.mutable._
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SmokeTest extends Specification with HttpComponentsClient with Logging {
  lazy val registry: EmailerRegistry = EmailerRegistry.fromString(Option(System.getProperty("valintatulos.profile")).getOrElse("localvt"))

  override def baseUrl: String = "http://localhost:" + ValintaTulosServiceWarRunner.valintatulosPort + "/valinta-tulos-service"

  "Fetch, send and confirm batch" in {
    put("util/fixtures/generate?hakemuksia=3&hakukohteita=2") {
      val appender: TestAppender = new TestAppender
      Logger.getRootLogger.addAppender(appender)
      registry.mailer.sendMail
      registry.asInstanceOf[LocalVT].lastEmailSize mustEqual 1
      appender.errors mustEqual List()
    }
  }
}

class TestAppender extends AppenderSkeleton {
  private var events: List[LoggingEvent] = Nil

  def errors = events.filter { event => List(Level.ERROR, Level.FATAL).contains(event.getLevel)}.map(_.getMessage)

  override def append(event: LoggingEvent): Unit = {
    events = events ++ List(event)
  }

  override def requiresLayout() = false

  override def close() {}
}
