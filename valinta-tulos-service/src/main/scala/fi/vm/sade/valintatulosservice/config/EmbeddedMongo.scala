package fi.vm.sade.valintatulosservice.config

import de.flapdoodle.embed.mongo.config.{IMongodConfig, MongoCmdOptionsBuilder, MongodConfigBuilder, Net, RuntimeConfigBuilder}
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.{Command, MongodStarter}
import de.flapdoodle.embed.process.config.io.ProcessOutput
import de.flapdoodle.embed.process.runtime.Network
import fi.vm.sade.valintatulosservice.logging.Logging

object EmbeddedMongo extends Logging {

  def start(port: Int): Option[MongoServer] = {
    if (PortChecker.isFreeLocalPort(port)) {
      logger.info("Starting embedded mongo on port " + port)
      Some(new MongoServer(port))
    } else {
      logger.info("Not starting embedded mongo, seems to be running on port " + port)
      None
    }
  }
}

class MongoServer(val port: Int) {
  private val mongodConfig: IMongodConfig = new MongodConfigBuilder()
    .version(Version.Main.PRODUCTION)
    .cmdOptions(new MongoCmdOptionsBuilder()
      .useStorageEngine("ephemeralForTest")
      .build())
    .net(new Net(port, Network.localhostIsIPv6))
    .setParameter("maxBSONDepth", "1000")
    .build
  private val runtimeConfig = new RuntimeConfigBuilder()
    .defaults(Command.MongoD)
    .processOutput(ProcessOutput.getDefaultInstanceSilent)
    .build();
  private val runtime: MongodStarter = MongodStarter.getInstance(runtimeConfig)
  private val mongodExecutable = runtime.prepare(mongodConfig)
  private val mongod = mongodExecutable.start

  def stop(): Unit = {
    mongod.stop()
    mongodExecutable.stop()
  }
}
