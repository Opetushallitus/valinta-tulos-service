package fi.vm.sade.valintatulosservice


import fi.vm.sade.auditlog.{Audit, Changes, Operation, Target}
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.streamingresults.{HakemustenTulosHakuLock, StreamingValintatulosService}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import org.scalatra.swagger._

class PublicValintatulosServlet(audit: Audit,
                                valintatulosService: ValintatulosService,
                                streamingValintatulosService: StreamingValintatulosService,
                                vastaanottoService: VastaanottoService,
                                ilmoittautumisService: IlmoittautumisService,
                                valintarekisteriDb: ValintarekisteriDb,
                                val sessionRepository: SessionRepository,
                                hakemustenTulosHakuLock: HakemustenTulosHakuLock) (override implicit val swagger: Swagger, appConfig: VtsAppConfig)
  extends ValintatulosServlet(valintatulosService,
    streamingValintatulosService,
    vastaanottoService,
    ilmoittautumisService,
    valintarekisteriDb,
    hakemustenTulosHakuLock,
  "valintatulos-public")(swagger, appConfig) with CasAuthenticatedServlet {

  protected val applicationDescription = "Julkinen valintatulosten REST API"

  override def auditLog(auditParams: Map[String, String], auditOperation: Operation): Unit = {
    implicit val authenticated = authenticate
    val credentials: AuditInfo = auditInfo
    val builder= new Target.Builder()
    auditParams.foreach(p => builder.setField(p._1,p._2))
    audit.log(auditInfo.user, auditOperation, builder.build(), new Changes.Builder().build())
  }

  override def auditLogChanged(auditParams: Map[String, String], auditOperation: Operation, changedParams: Map[String, String], changeOperation: String): Unit = {
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
