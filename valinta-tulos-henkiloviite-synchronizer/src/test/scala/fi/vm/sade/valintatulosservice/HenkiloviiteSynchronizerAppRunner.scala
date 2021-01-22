package fi.vm.sade.valintatulosservice

object HenkiloviiteSynchronizerAppRunner {
  def main(args: Array[String]): Unit = {
    if (!scala.sys.props.contains("henkiloviite.properties")) {
      scala.sys.props.put("henkiloviite.properties", "src/test/resources/luokka.properties")
    }
    println(s"""Using henkiloviite.properties from ${scala.sys.props.get("henkiloviite.properties").get}""")
    System.setProperty("logback.access", "")
    HenkiloviiteSynchronizerApp.main(args)
  }
}
