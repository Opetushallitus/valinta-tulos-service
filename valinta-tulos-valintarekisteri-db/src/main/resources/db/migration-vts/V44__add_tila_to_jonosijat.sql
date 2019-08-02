alter table jonosijat add column tila valinnantila;
alter table jonosijat add column tarkenteen_lisatieto varchar;
alter table jonosijat add column tilankuvaus_hash bigint references valinnantilan_kuvaukset(hash);

update jonosijat as j
set tila=v.tila, tarkenteen_lisatieto=v.tarkenteen_lisatieto, tilankuvaus_hash=v.tilankuvaus_hash
from valinnantulokset as v
where j.hakemus_oid = v.hakemus_oid
  and j.sijoitteluajo_id = v.sijoitteluajo_id and j.valintatapajono_oid = v.valintatapajono_oid;