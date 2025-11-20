package fi.vm.sade.valintatulosservice.vastaanottomeili

import fi.oph.viestinvalitys.ViestinvalitysClient
import fi.oph.viestinvalitys.vastaanotto.model.{Lahetys, Liite, LuoLahetysSuccessResponse, LuoLiiteSuccessResponse, LuoViestiSuccessResponse, VastaanottajaResponse, Viesti}
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.logging.Logging

import java.util
import java.util.{Optional, UUID}
import scala.beans.BeanProperty
import scala.collection.mutable

class FakeViestinvalitysClient extends ViestinvalitysClient with Logging {
  private val lahetykset = mutable.Map[UUID, Lahetys]()
  private val viestit = mutable.Map[UUID, List[Viesti]]()

  def getLahetykset: Map[UUID, Lahetys] = lahetykset.toMap

  def getViestit: Iterable[Viesti] = viestit.values.flatten

  def getViestit(lahetystunnus: UUID): List[Viesti] = viestit(lahetystunnus)

  override def luoLahetys(lahetys: Lahetys): LuoLahetysSuccessResponse = {
    logger.info(s"create lahetys: ${JsonFormats.javaObjectToJsonString(lahetys)}")
    val lahetysId = UUID.randomUUID()
    lahetykset.put(lahetysId, lahetys)
    viestit.put(lahetysId, List.empty)

    LuoLahetysSuccessResponseImpl(lahetysId)
  }

  override def luoLiite(liite: Liite): LuoLiiteSuccessResponse = ???

  override def luoViesti(viesti: Viesti): LuoViestiSuccessResponse = {
    logger.info(s"send viesti: ${JsonFormats.javaObjectToJsonString(viesti)}")

    if (viesti.getLahetysTunniste.isEmpty) {
      throw new NotImplementedError("Not implemented for stand-alone messages")
    }

    val viestiId = UUID.randomUUID()
    val lahetysId = UUID.fromString(viesti.getLahetysTunniste.get())

    if (viestit.contains(lahetysId)) {
      viestit(lahetysId) = viesti :: viestit(lahetysId)
    } else {
      throw new NotImplementedError(s"Not implemented for unknown lahetysId ($lahetysId)")
    }

    LuoViestiSuccessResponseImpl(viestiId, lahetysId)
  }

  override def getVastaanottajat(uuid: UUID, optional: Optional[Integer]): util.Iterator[util.List[VastaanottajaResponse]] = ???

  private case class LuoViestiSuccessResponseImpl(@BeanProperty viestiTunniste: UUID, @BeanProperty lahetysTunniste: UUID) extends LuoViestiSuccessResponse

  private case class LuoLahetysSuccessResponseImpl(@BeanProperty lahetysTunniste: UUID) extends LuoLahetysSuccessResponse
}
