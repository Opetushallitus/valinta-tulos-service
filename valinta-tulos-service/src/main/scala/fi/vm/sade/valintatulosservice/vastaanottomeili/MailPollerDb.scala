package fi.vm.sade.valintatulosservice.vastaanottomeili

import java.sql.Timestamp
import java.util.Date

import com.typesafe.config.{Config, ConfigValueFactory}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.db._
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriRepository
import org.flywaydb.core.Flyway
import slick.driver.PostgresDriver.api.{Database, _}
import slick.jdbc.GetResult

/**
  * Created by heikki.honkanen on 08/11/16.
  */
class MailPollerDb(dbConfig: Config, isItProfile:Boolean = false) extends ValintarekisteriRepository with Logging {
  val user = if (dbConfig.hasPath("user")) dbConfig.getString("user") else null
  val password = if (dbConfig.hasPath("password")) dbConfig.getString("password") else null
  logger.info(s"Database configuration: ${dbConfig.withValue("password", ConfigValueFactory.fromAnyRef("***"))}")
  val flyway = new Flyway()
  flyway.setDataSource(dbConfig.getString("url"), user, password)
  flyway.migrate()
  override val db = Database.forConfig("", dbConfig)
  if(isItProfile) {
    logger.warn("alter table public.schema_version owner to oph")
    runBlocking(sqlu"""alter table public.schema_version owner to oph""")
  }

  private implicit val pollForCandidatesResult = GetResult(r => HakemusIdentifier(r.nextString, r.nextString,
    Option(r.nextTimestamp())))

  def pollForCandidates(hakuOids: List[String], limit: Int, recheckIntervalHours: Int = (24 * 3),  excludeHakemusOids: Set[String] = Set.empty): Set[HakemusIdentifier] = {
    val hakuOidsIn = hakuOids.map(oid => s"'$oid'").mkString(",")
    val hakemusOidsNotIn = excludeHakemusOids.map(oid => s"'$oid'").mkString(",")
    val res = runBlocking(
      sql"""select h.haku_oid, v.hakemus_oid, v.sent
            from valinnantulokset as v
            inner join hakukohteet as h on h.hakukohde_oid = v.hakukohde_oid
            where h.haku_oid in (#$hakuOidsIn) and v.julkaistavissa is true and v.done is null
            and (v.previous_check is null or v.previous_check < current_timestamp - interval '${recheckIntervalHours.toString} hour'
            and v.hakemus_oid not in (#$hakemusOidsNotIn)
            limit ${limit}
         """.as[HakemusIdentifier]).toSet

    updateLastChecked(res.map(r => r.hakemusOid))
    res
  }

  def updateLastChecked(hakemusOids: Set[String]) = {
    val hakemusOidsIn = hakemusOids.map(oid => s"'$oid'").mkString(",")
    val timestamp = new Timestamp(new Date().getTime)
    runBlocking(
      sqlu"""update valinnantulokset
             set previous_check=${timestamp}
             where hakemus_oid in #$hakemusOidsIn""")
  }

  def alreadyMailed(hakemusOid: String, hakukohdeOid: String): Option[java.util.Date] = {
    runBlocking(
      sql"""select sent
            from valinnantulokset
            where hakemus_oid = ${hakemusOid} and hakukohde_oid = ${hakukohdeOid} and sent is not null""".as[Timestamp]).headOption
  }

  def markAsSent(mailContents: LahetysKuittaus): Unit = {
    mailContents.hakukohteet.foreach(hakukohde => {
      markAsSent(mailContents.hakemusOid, hakukohde, "Lähetetty email")
    })
  }

  def addMessage(hakemus: HakemusMailStatus, hakukohde: HakukohdeMailStatus, message: String): Unit = {
    updateValintatulos(hakemus.hakemusOid, hakukohde.hakukohdeOid, null, null, message)
  }

  def markAsNonMailable(hakemusOid: String, hakuKohdeOid: String, message: String) {
    updateValintatulos(hakemusOid, hakuKohdeOid, new Timestamp(new Date().getTime), null, message)
  }

  private def markAsSent(hakemusOid: String, hakuKohdeOid: String, message: String) {
    updateValintatulos(hakemusOid, hakuKohdeOid, null, new Timestamp(new Date().getTime), message)
  }

  private def updateValintatulos(hakemusOid: String, hakuKohdeOid: String, done: Timestamp, sent: Timestamp, message: String): Unit = {
    runBlocking(
      sqlu"""update valinnantulokset
             set done = ${done}, sent = ${sent}, message = ${message}
             where hakemus_oid = ${hakemusOid}""")
  }

}

