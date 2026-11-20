-- Ilmoittautumisen muutoshistoria rajataan hakemukseen, joten myös historiariville tallennetaan hakemus, jolle
-- ilmoittautuminen oli tallennettu. Vanhoilla historiariveillä arvo on null, ja ne näytetään henkilön jokaiselle
-- hakemukselle hakukohteessa kuten ennenkin.
alter table ilmoittautumiset_history add column if not exists hakemus_oid character varying;

comment on column ilmoittautumiset_history.hakemus_oid is 'Hakemus, jolle ilmoittautuminen oli tallennettu. Null riveillä, joille hakemusta ei ollut tallennettu.';

create or replace function update_ilmoittautumiset_history() returns trigger as
$$
begin
    insert into ilmoittautumiset_history (
        hakukohde,
        henkilo,
        hakemus_oid,
        tila,
        ilmoittaja,
        selite,
        transaction_id,
        system_time
    ) values (
        old.hakukohde,
        old.henkilo,
        old.hakemus_oid,
        old.tila,
        old.ilmoittaja,
        old.selite,
        old.transaction_id,
        tstzrange(lower(old.system_time), now(), '[)')
    );
    return null;
end;
$$ language plpgsql;

-- V20261008110000 rajasi triggerit ajettaviksi vain, kun tila, ilmoittaja tai selite muuttuu, jotta hakemus_oid:n
-- täyttöajo ei siirrä system_time-aikaleimaa eikä kirjoita historiaa. Rajaus oli tarpeettoman tiukka: jos henkilo tai
-- hakukohde joskus muuttuisi, muutos jäisi ilman aikaleimaa ja historiaa. Triggerit luodaan uudelleen niin, että vain
-- hakemus_oid:n ja hakemus_oid_not_found:n muutokset ohitetaan. Hakemuksen vaihtuminen tallentuu silti historiaan,
-- koska se tapahtuu vain tilan muuttuessa (storeIlmoittautuminen).
drop trigger if exists set_system_time_on_ilmoittautumiset_on_update on ilmoittautumiset;
create trigger set_system_time_on_ilmoittautumiset_on_update
before update on ilmoittautumiset
for each row
when (old.henkilo is distinct from new.henkilo
    or old.hakukohde is distinct from new.hakukohde
    or old.tila is distinct from new.tila
    or old.ilmoittaja is distinct from new.ilmoittaja
    or old.selite is distinct from new.selite)
execute procedure set_temporal_columns();

drop trigger if exists update_ilmoittautumiset_history on ilmoittautumiset;
create trigger update_ilmoittautumiset_history
after update on ilmoittautumiset
for each row
when (old.transaction_id <> txid_current()
    and (old.henkilo is distinct from new.henkilo
        or old.hakukohde is distinct from new.hakukohde
        or old.tila is distinct from new.tila
        or old.ilmoittaja is distinct from new.ilmoittaja
        or old.selite is distinct from new.selite))
execute procedure update_ilmoittautumiset_history();
