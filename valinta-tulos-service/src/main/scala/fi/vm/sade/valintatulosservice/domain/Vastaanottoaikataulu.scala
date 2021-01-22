package fi.vm.sade.valintatulosservice.domain

import org.joda.time.DateTime

case class Vastaanottoaikataulu(vastaanottoEnd: Option[DateTime], vastaanottoBufferDays: Option[Int])
