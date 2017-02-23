package fi.vm.sade.valintatulosservice.organisaatio

case class Organisaatiot(organisaatiot: Seq[Organisaatio])

case class Organisaatio(oid: String, oppilaitosKoodi: Option[String], children: Seq[Organisaatio])
