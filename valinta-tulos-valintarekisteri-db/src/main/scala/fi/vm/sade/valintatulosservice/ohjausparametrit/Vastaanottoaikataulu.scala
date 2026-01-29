package fi.vm.sade.valintatulosservice.ohjausparametrit

import java.time.ZonedDateTime

case class Vastaanottoaikataulu(vastaanottoEnd: Option[ZonedDateTime], vastaanottoBufferDays: Option[Int])
