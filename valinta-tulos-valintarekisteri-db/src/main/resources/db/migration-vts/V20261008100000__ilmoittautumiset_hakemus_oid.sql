-- hakemus_oid ei tallennu ilmoittautumiset_history-tauluun: update_ilmoittautumiset_history() luettelee sarakkeet
-- eksplisiittisesti, eikä historiataulua tai sen triggeriä muuteta.
alter table ilmoittautumiset add column hakemus_oid character varying;

comment on column ilmoittautumiset.hakemus_oid is 'Hakemus, jonka hakutoiveen ilmoittautumisesta on kyse. Null vanhoilla riveillä, joille hakemusta ei ole tallennettu.';
