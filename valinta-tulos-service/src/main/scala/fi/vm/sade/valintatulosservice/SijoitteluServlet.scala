package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.config.AppConfig.AppConfig
import fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu.ValintarekisteriForSijoittelu
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger._
import org.scalatra.{NotImplemented, Ok}

class SijoitteluServlet(sijoitteluService: ValintarekisteriForSijoittelu)(implicit val swagger: Swagger, appConfig: AppConfig) extends VtsServletBase {

  override val applicationName = Some("sijoittelu")

  override protected def applicationDescription: String = "Sijoittelun REST API"

  /*lazy val postSijoitteluajoSwagger: OperationBuilder = (apiOperation[Unit]("postSijoitteluajoSwagger")
    summary "Tallentaa sijoitteluajon"
    parameter bodyParam[SijoitteluAjo]("sijoitteluajo").description("Sijoitteluajon data"))
  post("/sijoitteluajo", operation(postSijoitteluajoSwagger)) {
    val sijoitteluajo = read[SijoitteluajoWrapper](request.body)
    Ok(sijoitteluService.luoSijoitteluajo(sijoitteluajo.sijoitteluajo))
  }*/

  lazy val getSijoitteluajoMaxIntervalSwagger: OperationBuilder = (apiOperation[Unit]("getSijoitteluajoMaxIntervalSwagger")
    summary "Hakee sijoittelun tiedot haulle. Pääasiallinen kaytto sijoitteluajojen tunnisteiden hakuun.")
  get("/session/maxinterval", operation(getSijoitteluajoMaxIntervalSwagger)) {
    //TODO Ok(sijoitteluService.getMaxInterval(hakuOid))
    NotImplemented()
  }

  // Sijoittelu-service
  lazy val getSijoitteluajoByHakuOidSwagger: OperationBuilder = (apiOperation[Unit]("getSijoitteluajoByHakuOidSwagger")
    summary "Hakee sijoittelun tiedot haulle. Pääasiallinen kaytto sijoitteluajojen tunnisteiden hakuun."
    parameter pathParam[String]("hakuOid").description("Haun yksilöllinen tunniste"))
  get("/:hakuOid", operation(getSijoitteluajoByHakuOidSwagger)) {
    val hakuOid = params("hakuOid")
    //TODO Ok(sijoitteluService.getSijoitteluajoByHakuOid(hakuOid))
    NotImplemented()
  }

  lazy val getSijoitteluajoSwagger: OperationBuilder = (apiOperation[Unit]("getSijoitteluajoSwagger")
    summary "Hakee sijoitteluajon tiedot. Pääsiallinen kaytto sijoitteluun osallistuvien hakukohteiden hakemiseen."
    parameter pathParam[String]("hakuOid").description("Haun yksilöllinen tunniste") //TODO tarpeeton?
    parameter pathParam[String]("sijoitteluajoId").description("Sijoitteluajon yksilöllinen tunniste, tai 'latest' avainsana."))
  get("/:hakuOid/sijoitteluajo/:sijoitteluajoId", operation(getSijoitteluajoSwagger)) {
    val hakuOid = params("hakuOid")
    val sijoitteluajoId = params("sijoitteluajoId")
    Ok(sijoitteluService.getSijoitteluajo(hakuOid, sijoitteluajoId))
  }

  lazy val getHakemusetBySijoitteluajoSwagger: OperationBuilder = (apiOperation[Unit]("getHakemusetBySijoitteluajoSwagger")
    summary "Sivutettu listaus hakemuksien/hakijoiden listaukseen. Yksityiskohtainen listaus kaikista hakutoiveista ja niiden valintatapajonoista."
    parameter pathParam[String]("hakuOid").description("Haun yksilöllinen tunniste")
    parameter pathParam[String]("sijoitteluajoId").description("Sijoitteluajon yksilöllinen tunniste, tai 'latest' avainsana."))
  get("/:hakuOid/sijoitteluajo/:sijoitteluajoId/hakemukset", operation(getHakemusetBySijoitteluajoSwagger)) {
    val hakuOid = params("hakuOid")
    val sijoitteluajoId = params("sijoitteluajoId")
    //TODO Ok(sijoitteluService.getHakemuksetBySijoitteluajo(hakuOid, sijoitteluajoId))
    NotImplemented()
  }

  lazy val getHakemusBySijoitteluajoSwagger: OperationBuilder = (apiOperation[Unit]("getHakemusBySijoitteluajoSwagger")
    summary "Nayttaa yksittaisen hakemuksen kaikki hakutoiveet ja tiedot kaikista valintatapajonoista."
    parameter pathParam[String]("hakuOid").description("Haun yksilöllinen tunniste")
    parameter pathParam[String]("sijoitteluajoId").description("Sijoitteluajon yksilöllinen tunniste, tai 'latest' avainsana.")
    parameter pathParam[String]("hakemusOid").description("Hakemuksen yksilöllinen tunniste"))
  get("/:hakuOid/sijoitteluajo/:sijoitteluajoId/hakemus/:hakemusOid", operation(getHakemusBySijoitteluajoSwagger)) {
    val hakuOid = params("hakuOid")
    val sijoitteluajoId = params("sijoitteluajoId")
    val hakemusOid = params("hakemusOid")
    Ok(sijoitteluService.getHakemusBySijoitteluajo(hakuOid, sijoitteluajoId, hakemusOid))
  }

  lazy val getHakukohdeBySijoitteluajoSwagger: OperationBuilder = (apiOperation[Unit]("getHakukohdeBySijoitteluajoSwagger")
    summary "Hakee hakukohteen tiedot tietyssa sijoitteluajossa."
    parameter pathParam[String]("hakuOid").description("Haun yksilöllinen tunniste")
    parameter pathParam[String]("sijoitteluajoId").description("Sijoitteluajon yksilöllinen tunniste, tai 'latest' avainsana.")
    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen yksilöllinen tunniste"))
  get("/:hakuOid/sijoitteluajo/:sijoitteluajoId/hakukohde/:hakukohdeOid", operation(getHakukohdeBySijoitteluajoSwagger)) {
    val hakuOid = params("hakuOid")
    val sijoitteluajoId = params("sijoitteluajoId")
    val hakukohdeOid = params("hakukohdeOid")
    // TODO Ok(sijoitteluService.getHakukohdeBySijoitteluajo(hakuOid, sijoitteluajoId, hakukohdeOid))
    NotImplemented()
  }

  lazy val getHakukohdeErillissijoitteluSwagger: OperationBuilder = (apiOperation[Unit]("getHakukohdeErillissijoitteluSwagger")
    summary "Hakee hakukohteen erillissijoittelun tiedot tietyssa sijoitteluajossa."
    parameter pathParam[String]("hakuOid").description("Haun yksilöllinen tunniste")
    parameter pathParam[String]("sijoitteluajoId").description("Sijoitteluajon yksilöllinen tunniste, tai 'latest' avainsana.")
    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen yksilöllinen tunniste"))
  get("/erillissijoittelu/:hakuOid/sijoitteluajo/:sijoitteluajoId/hakukohde/:hakukohdeOid", operation(getHakukohdeErillissijoitteluSwagger)) {
    val hakuOid = params("hakuOid")
    val sijoitteluajoId = params("sijoitteluajoId")
    val hakukohdeOid = params("hakukohdeOid")
    //TODO Ok(sijoitteluService.getHakukohdeErillissijoittelu(hakuOid, sijoitteluajoId, hakukohdeOid))
    NotImplemented()
  }

// TODO not in use?
//  lazy val getSijoitteluajoHyvaksytytSwagger: OperationBuilder = (apiOperation[Unit]("getSijoitteluajoHyvaksytytSwagger")
//    summary "Sivutettu listaus hakemuksien/hakijoiden listaukseen. Yksityiskohtainen listaus kaikista hakutoiveista ja niiden valintatapajonoista."
//    parameter pathParam[String]("hakuOid").description("Haun yksilöllinen tunniste"))
//  get("/:hakuOid/hyvaksytyt", operation(getSijoitteluajoHyvaksytytSwagger)) {
//    val hakuOid = params("hakuOid")
//    //TODO Ok(sijoitteluService.getSijoitteluajoHyvaksytyt(hakuOid))
//    NotImplemented()
//  }

//  lazy val getSijoitteluajoHyvaksytytByHakukohdeSwagger: OperationBuilder = (apiOperation[Unit]("getSijoitteluajoHyvaksytytByHakukohdeSwagger")
//    summary "Sivutettu listaus hakemuksien/hakijoiden listaukseen. Yksityiskohtainen listaus kaikista hakutoiveista ja niiden valintatapajonoista hakukohteen perusteella."
//    parameter pathParam[String]("hakuOid").description("Haun yksilöllinen tunniste")
//    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen yksilöllinen tunniste"))
//  get("/:hakuOid/hyvaksytyt/hakukohde/:hakukohdeOid", operation(getSijoitteluajoHyvaksytytByHakukohdeSwagger)) {
//    val hakuOid = params("hakuOid")
//    val hakukohdeOid = params("hakukohdeOid")
//    //TODO Ok(sijoitteluService.getSijoitteluajoHyvaksytytByHakukohde(hakuOid, hakukohdeOid))
//    NotImplemented()
//  }

//  lazy val getHakukohdeDtoBySijoitteluajoSwagger: OperationBuilder = (apiOperation[Unit]("getHakukohdeDtoBySijoitteluajoSwagger")
//    summary "Hakee hakukohteen tiedot tietyssa sijoitteluajossa."
//    parameter pathParam[String]("hakuOid").description("Haun yksilöllinen tunniste")
//    parameter pathParam[String]("sijoitteluajoId").description("Sijoitteluajon yksilöllinen tunniste, tai 'latest' avainsana.")
//    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen yksilöllinen tunniste"))
//  get("/:hakuOid/sjoitteluajo/:sijoitteluajoId/hakukohdedto/:hakukohdeOid", operation(getHakukohdeDtoBySijoitteluajoSwagger)) {
//    val hakuOid = params("hakuOid")
//    val sijoitteluajoId = params("sijoitteluajoId")
//    val hakukohdeOid = params("hakukohdeOid")
//    //TODO Ok(sijoitteluService.getHakukohdeDtoBySijoitteluajo(hakuOid, sijoitteluajoId, hakukohdeOid))
//    NotImplemented()
//  }

//  lazy val valintatapajonoIsInSijoitteluSwagger: OperationBuilder = (apiOperation[Unit]("valintatapajonoIsInSijoitteluSwagger")
//    summary "Kertoo jos valintatapajono on sijoittelun käytössä."
//    parameter pathParam[String]("hakuOid").description("Haun yksilöllinen tunniste")
//    parameter pathParam[String]("valintatapajonoOid").description("Valintatapajonon yksilöllinen tunniste"))
//  get("/:hakuOid/valintatapajono-in-use/:valintatapajonoOid", operation(valintatapajonoIsInSijoitteluSwagger)) {
//    val hakuOid = params("hakuOid")
//    val valintatapajonoOid = params("valintatapajonoOid")
//    //TODO Ok(sijoitteluService.valintatapajonoIsInSijoittelu(hakuOid, valintatapajonoOid))
//    NotImplemented()
//  }
}
