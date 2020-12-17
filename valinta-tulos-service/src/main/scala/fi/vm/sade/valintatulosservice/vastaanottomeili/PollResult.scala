package fi.vm.sade.valintatulosservice.vastaanottomeili

import java.util.Date

import scala.concurrent.duration.Duration

case class PollResult(
  isPollingComplete: Boolean = false,
  candidatesProcessed: Int = 0,
  started: Date = new Date(),
  mailables: List[Ilmoitus] = List.empty
) {
  def exceeds(timeLimit: Duration): Boolean =
    System.currentTimeMillis() > (started.getTime + timeLimit.toMillis)

  def size: Int = mailables.size
}
