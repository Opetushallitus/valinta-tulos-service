package fi.vm.sade.valintatulosservice.ovara

import java.sql.Date
import java.text.SimpleDateFormat

class SiirtotiedostoUtil {

  val sdFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS XXX")

  def nowFormatted() = {
    sdFormat.format(new Date(System.currentTimeMillis()))
  }
}

object SiirtotiedostoUtil extends SiirtotiedostoUtil
