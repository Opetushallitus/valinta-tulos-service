alter table ehdollisen_hyvaksynnan_ehto drop constraint if exists ehdollisen_hyvaksynnan_ehto_valinnantulokset;
alter table valinnantulokset drop constraint if exists valinnantulokset_pkey;
drop index if exists valinnantulokset_pkey;
--
alter table valinnantulokset add constraint valinnantulokset_pkey_hakemus_oid_ensin
    primary key (hakemus_oid, valintatapajono_oid, hakukohde_oid);
alter table ehdollisen_hyvaksynnan_ehto add constraint ehdollisen_hyvaksynnan_ehto_valinnantulokset_hakemus_oid_ensin
    foreign key (hakemus_oid, valintatapajono_oid, hakukohde_oid) references valinnantulokset (hakemus_oid, valintatapajono_oid, hakukohde_oid);
create unique index valinnantulokset_pkey_idx
    on valinnantulokset (hakemus_oid, valintatapajono_oid, hakukohde_oid);