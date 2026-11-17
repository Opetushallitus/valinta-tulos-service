package fi.vm.sade.valintatulosservice.suorituspalvelu

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.{Authenticated, CasAuthenticatedServlet, PaatettavienOpiskeluoikeuksienUudelleenpaattely, VtsServletBase}
import org.json4s.jackson.Serialization
import org.scalatra.Ok
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder

class PaatettavatOpiskeluoikeudetServlet(suorituspalveluService: SuorituspalveluService,
                                         val sessionRepository: SessionRepository,
                                         audit: Audit)
                                        (implicit val swagger: Swagger) extends VtsServletBase with CasAuthenticatedServlet {

  override val applicationDescription = "REST API päätettävien opiskeluoikeuksien uudelleenpäättelyyn"

  val uudelleenpaattelySwagger: OperationBuilder = (apiOperation[List[UudelleenpaattelynTulos]]("paatettavienOpiskeluoikeuksienUudelleenpaattely")
    summary "Päättele hakutoiveiden päätettävät opiskeluoikeudet uudelleen suorituspalvelusta ja tallenna ne"
    description "Käsittelee annetut hakemus- ja hakukohdeparit, joilla on sitova tai ehdollinen vastaanotto YOS:n piiriin kuuluvaan hakukohteeseen. Ylikirjoittaa aiemmin tallennetut tiedot."
    parameter queryParam[Boolean]("dryRun").defaultValue(false).description("Palauttaa päättelyn tuloksen tallentamatta sitä")
    parameter bodyParam[List[UudelleenpaattelynKohde]]("kohteet").description("Hakemus- ja hakukohdeparit")
    tags "paatettavat-opiskeluoikeudet")
  post("/uudelleenpaattely", operation(uudelleenpaattelySwagger)) {
    implicit val authenticated: Authenticated = authenticate
    authorize(Role.SIJOITTELU_CRUD_OPH)
    val dryRun = params.getOrElse("dryRun", "false").toBoolean
    val kohteet = parsedBody.extract[List[UudelleenpaattelynKohde]]
    if (kohteet.isEmpty) {
      throw new IllegalArgumentException("Uudelleenpäätettäviä hakutoiveita ei annettu")
    }
    val user = auditInfo.user
    audit.log(user, PaatettavienOpiskeluoikeuksienUudelleenpaattely,
      new Target.Builder()
        .setField("kohteet", Serialization.write(kohteet))
        .setField("dryRun", dryRun.toString)
        .build(),
      new Changes.Builder().build())

    val tulokset = suorituspalveluService.paatteleUudelleen(kohteet, dryRun)
    tulokset.filter(_.tila == UudelleenpaattelynTila.Tallennettu).foreach(tulos =>
      audit.log(user, PaatettavienOpiskeluoikeuksienUudelleenpaattely,
        new Target.Builder()
          .setField("hakemusOid", tulos.hakemusOid.toString)
          .setField("hakukohdeOid", tulos.hakukohdeOid.toString)
          .build(),
        new Changes.Builder()
          .updated("paatettavat_oikeudet", tulos.aiemmat.map(Serialization.write(_)).orNull, Serialization.write(tulos.paatellyt.getOrElse(List.empty)))
          .build()))
    Ok(tulokset)
  }
}
