package fi.vm.sade.valintatulosemailer.util

import java.util.Date

import org.joda.time.DateTime

import scala.util.Random

object RandomDataGenerator {
  val firstNames = List("Teppo", "Teemu", "Mikko", "Jussi", "Janne", "Anna", "Marleena", "Maija", "Johanna", "Leena")
  val lastNames = List("Korhonen", "Virtanen", "Mäkinen", "Nieminen", "Mäkelä", "Hämäläinen", "Laine", "Koskinen", "Järvinen")

  def randomFirstName: String = {
    randomListItem(firstNames)
  }

  def randomLastName: String = {
    randomListItem(lastNames)
  }

  def randomEmailAddress: String = {
    s"${randomFirstName.toLowerCase}.${randomLastName.toLowerCase()}@example.com"
  }

  def randomLang: String = {
    randomListItem(List("FI", "SV", "EN"))
  }

  def randomDateAfterNow: Date = {
    new DateTime().plusDays(Random.nextInt(30) + 1).plusMinutes(Random.nextInt(30)).plusHours(Random.nextInt(12)).toDate
  }

  def randomListItem[A](l: List[A]): A = {
    l(Random.nextInt(l.length))
  }

  def randomOid: String = {
    def nextRandomInt(i: Int) = Random.nextInt(Math.pow(2, i).toInt)
    (1 to 10).map(nextRandomInt).mkString(".")
  }
}
