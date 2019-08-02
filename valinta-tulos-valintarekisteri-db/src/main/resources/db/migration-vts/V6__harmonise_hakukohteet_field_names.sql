alter table hakukohteet rename column "hakukohdeOid" to hakukohde_oid;
alter table hakukohteet rename column "hakuOid" to haku_oid;
alter table hakukohteet rename column kktutkintoonjohtava to kk_tutkintoon_johtava;

alter table vanhat_vastaanotot rename column "kkTutkintoonJohtava" to kk_tutkintoon_johtava;
