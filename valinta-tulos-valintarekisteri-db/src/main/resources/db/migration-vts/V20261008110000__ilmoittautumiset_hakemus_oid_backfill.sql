-- Väliaikainen apusarake hakemus_oid:n täyttöajoa varten (HakemusOidBackfillScheduler).
-- Ajo merkitsee true:ksi rivit, joita on yritetty täyttää mutta joille ei löytynyt yksikäsitteistä hakemusta,
-- jotta samoja rivejä ei käsitellä uudelleen. Muuten arvo on null.
-- Sarake ja indeksi poistetaan uudella migraatiolla, kun täyttöajo poistetaan.
alter table ilmoittautumiset add column hakemus_oid_not_found boolean;

comment on column ilmoittautumiset.hakemus_oid_not_found is 'Väliaikainen: true, jos hakemus_oid:tä yritettiin päätellä jälkikäteen mutta yksikäsitteistä hakemusta ei löytynyt.';

create index ilmoittautumiset_hakemus_oid_kasittelematon_idx on ilmoittautumiset (henkilo, hakukohde)
    where hakemus_oid is null and hakemus_oid_not_found is null;

-- Täyttöajo päivittää vain hakemus_oid:tä ja apusaraketta, eikä se ole ilmoittautumisen muutos. Aiemmin triggerit
-- ajettiin jokaisella päivityksellä, jolloin täyttö olisi siirtänyt system_time-aikaleimaa (ifUnmodifiedSince-tarkistus
-- ja Last-Modified perustuvat siihen) ja kirjoittanut historiaan muutoksen, jossa mikään ei ole muuttunut.
-- Triggereiden when-ehtoa ei voi muuttaa jälkikäteen, joten ne luodaan uudelleen: ne ajetaan vain, kun tila,
-- ilmoittaja tai selite muuttuu. Tämä jää voimaan myös täyttöajon poiston jälkeen.
drop trigger set_system_time_on_ilmoittautumiset_on_update on ilmoittautumiset;
create trigger set_system_time_on_ilmoittautumiset_on_update
before update on ilmoittautumiset
for each row
when (old.tila is distinct from new.tila
    or old.ilmoittaja is distinct from new.ilmoittaja
    or old.selite is distinct from new.selite)
execute procedure set_temporal_columns();

drop trigger update_ilmoittautumiset_history on ilmoittautumiset;
create trigger update_ilmoittautumiset_history
after update on ilmoittautumiset
for each row
when (old.transaction_id <> txid_current()
    and (old.tila is distinct from new.tila
        or old.ilmoittaja is distinct from new.ilmoittaja
        or old.selite is distinct from new.selite))
execute procedure update_ilmoittautumiset_history();
