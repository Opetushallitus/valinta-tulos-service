-- Väliaikainen apusarake hakemus_oid:n täyttöajoa varten (VastaanottoHakemusOidBackfillScheduler).
-- Ajo merkitsee true:ksi rivit, joita on yritetty täyttää mutta joille ei löytynyt yksikäsitteistä hakemusta,
-- jotta samoja rivejä ei käsitellä uudelleen. Muuten arvo on null.
-- Poistetaan sarake ja indeksi uudella migraatiolla, kun täyttöajo poistetaan.
alter table vastaanotot add column hakemus_oid_not_found boolean;

comment on column vastaanotot.hakemus_oid_not_found is 'Väliaikainen: true, jos hakemus_oid:tä yritettiin päätellä jälkikäteen mutta yksikäsitteistä hakemusta ei löytynyt.';

create index vastaanotot_hakemus_oid_kasittelematon_idx on vastaanotot (id)
    where hakemus_oid is null and hakemus_oid_not_found is null;
