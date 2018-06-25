insert into pg_enum (enumtypid, enumlabel, enumsortorder)
    select 'valinnantilanTarkenne'::regtype::oid, 'PeruuntunutHyvaksyttyAlemmalleHakutoiveelle',
      ( select max(enumsortorder) + 1 from pg_enum where enumtypid = 'valinnantilanTarkenne'::regtype );
