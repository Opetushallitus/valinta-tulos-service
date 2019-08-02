create table viestinnan_ohjaus (
    hakukohde_oid text not null,
    valintatapajono_oid text not null,
    hakemus_oid text not null,
    previous_check timestamp with time zone,
    sent timestamp with time zone,
    done timestamp with time zone,
    message text,
    primary key (hakukohde_oid, valintatapajono_oid, hakemus_oid)
);

alter table viestinnan_ohjaus owner to oph;

insert into deleted_valinnantulokset (poistaja, selite) values ('', 'Uudempi sijoitteluajo tallennettu');
update valinnantulokset set deleted = currval('deleted_valinnantulokset_id')
where sijoitteluajo_id not in (select max(id) from sijoitteluajot group by haku_oid);

insert into viestinnan_ohjaus (
    hakukohde_oid,
    valintatapajono_oid,
    hakemus_oid,
    previous_check,
    sent,
    done,
    message
) select
    hakukohde_oid,
    valintatapajono_oid,
    hakemus_oid,
    previous_check,
    sent,
    done,
    message
from valinnantulokset
where deleted is null;

alter table valinnantulokset drop column previous_check;
alter table valinnantulokset drop column sent;
alter table valinnantulokset drop column done;
alter table valinnantulokset drop column message;
