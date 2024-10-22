package fi.vm.sade.valintatulosservice.ovara.config

case class SiirtotiedostoConfig(aws_region: String,
                                s3_bucket: String,
                                role_arn: String,
                                ilmoittautumisetSize: Int,
                                vastaanototSize: Int,
                                valintatapajonotSize: Int,
                                jonosijatSize: Int,
                                hyvaksytytJulkaistutSize: Int,
                                lukuvuosimaksutSize: Int,
                                hakukohdeGroupSize: Int)
