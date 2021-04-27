package fi.vm.sade.valintatulosservice.ohjausparametrit

import org.joda.time.DateTime

case class Vastaanottoaikataulu(vastaanottoEnd: Option[DateTime], vastaanottoBufferDays: Option[Int])
