create function overriden_ilmoittautuminen_deleted_id()
    returns bigint immutable language sql as
'select -2::bigint';

insert into deleted_ilmoittautumiset (id, poistaja, "timestamp", selite)
values (overriden_ilmoittautuminen_deleted_id(), '', to_timestamp(0), 'Korvaava ilmoittautumistieto tallennettu');
