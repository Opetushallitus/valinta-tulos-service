alter table vastaanotot add column hakemus_oid character varying;

comment on column vastaanotot.hakemus_oid is 'Hakemus, jonka hakutoiveen vastaanotosta on kyse. Null vanhoilla riveillä, joille hakemusta ei ole tallennettu.';
