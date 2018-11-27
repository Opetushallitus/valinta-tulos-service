package fi.vm.sade.valintatulosservice


import fi.vm.sade.auditlog.{Audit, Changes, Target, Operation}
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.streamingresults.StreamingValintatulosService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import org.scalatra.swagger._

class PublicValintatulosServlet(audit: Audit, valintatulosService: ValintatulosService, streamingValintatulosService: StreamingValintatulosService, vastaanottoService: VastaanottoService, ilmoittautumisService: IlmoittautumisService, valintarekisteriDb: ValintarekisteriDb, val sessionRepository: SessionRepository)(override implicit val swagger: Swagger, appConfig: VtsAppConfig) extends ValintatulosServlet(valintatulosService, streamingValintatulosService, vastaanottoService, ilmoittautumisService, valintarekisteriDb)(swagger, appConfig) with CasAuthenticatedServlet {

  override val applicationName = Some("cas/haku")

  protected val applicationDescription = "Julkinen valintatulosten REST API"

  override def auditLog(auditParams: List[(String, String)], auditOperation: Operation) {
    implicit val authenticated = authenticate
    val credentials: AuditInfo = auditInfo
    val builder= new Target.Builder()
    auditParams.foreach(p => builder.setField(p._1,p._2))
    audit.log(auditInfo.user, auditOperation, builder.build(), new Changes.Builder().build())
  }

  override def auditLogChanged(auditParams: List[(String, String)], auditOperation: Operation, changedParams: List[(String, String)], changeOperation: String) {
    implicit val authenticated = authenticate
    val credentials: AuditInfo = auditInfo
    val builder = new Target.Builder()
    auditParams.foreach(p => builder.setField(p._1,p._2))
    val changesBuilder = new Changes.Builder()

    if (changeOperation.equals("added")) {
      changedParams.foreach(p => changesBuilder.added(p._1, p._2))
    } else if (changeOperation.equals("removed")) {
      changedParams.foreach(p => changesBuilder.removed(p._1, p._2))
    } else {
      changedParams.foreach(p => changesBuilder.updated(p._1, None.toString, p._2))
    }
    changedParams.foreach(p => changesBuilder.added(p._1, p._2))
    audit.log(auditInfo.user, auditOperation, builder.build(), changesBuilder.build())
  }
}
