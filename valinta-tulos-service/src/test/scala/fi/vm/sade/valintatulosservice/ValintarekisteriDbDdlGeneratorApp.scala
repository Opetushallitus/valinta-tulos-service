package fi.vm.sade.valintatulosservice

import scala.sys.process.stringToProcess

object ValintarekisteriDbDdlGeneratorApp extends ITSetup {
  def main(args: Array[String]) = {
    appConfig.start
    singleConnectionValintarekisteriDb // force lazy val
    val r = "jdbc:postgresql://(.*):([0-9]+).*".r
    dbConfig.url match {
      case r(host, port) =>
        val r = s"./scripts/generate_schema_diagram.sh $host $port ./target/schema ${args(0)}".!
        System.exit(r)
    }
    System.exit(0)
  }
}
