-- Hakufamily

drop index one_active_reception_per_family_index;
alter table vastaanotot drop constraint votto_fk;
alter table vastaanotot add constraint vastaanotot_hakukohteet_fk foreign key (hakukohde) references hakukohteet("hakukohdeOid");
alter table vastaanotot drop column "familyId";

drop index kohde_family_index;
alter table hakukohteet drop constraint haku_fk;
alter table hakukohteet add constraint hakukohteet_haku_fk foreign key ("hakuOid") references haut("hakuOid");
alter table hakukohteet drop constraint kausi_xor_family;
alter table hakukohteet drop column "familyId";

drop index haku_family_index;
alter table haut drop constraint family_fk;
alter table haut drop column "familyId";

drop table hakufamilies;

-- kausi
create domain kausi as text check (value ~ '^[0-9]{4}[KS]$');
alter table hakukohteet drop column "kausi";
alter table koulutukset drop constraint kausi_fk;
drop table kaudet;
alter table koulutukset alter column alkamiskausi set data type kausi;
