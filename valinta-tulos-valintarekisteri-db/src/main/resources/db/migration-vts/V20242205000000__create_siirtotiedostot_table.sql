create table if not exists siirtotiedostot (
                                             id serial,
                                             uuid varchar,
                                             window_start varchar not null,
                                             window_end varchar not null,
                                             run_start timestamp with time zone,
                                             run_end timestamp with time zone,
                                             info jsonb,
                                             success boolean,
                                             error_message varchar,
                                             PRIMARY KEY (id)
);

COMMENT ON column siirtotiedostot.run_start IS 'Siirtotiedosto-operaation suorituksen alkuaika';
COMMENT ON column siirtotiedostot.run_end IS 'Siirtotiedosto-operaation suorituksen loppuaika';
COMMENT ON column siirtotiedostot.window_start IS 'Siirtotiedosto-operaation käsittelemän aikaikkunan alkuaika';
COMMENT ON column siirtotiedostot.window_end IS 'Siirtotiedosto-operaation käsittelemän aikaikkunan loppuaika';
COMMENT ON column siirtotiedostot.info IS 'Tietoja tallennetuista entiteeteistä, mm. lukumäärät';
COMMENT ON column siirtotiedostot.error_message IS 'Mahdollisen operaation katkaisseen virheen otsikko';

--Add a baseline success, so that the first timed run doesn't try to process all the data at once. Data prior to 2024-08-01 00:00:00.000000 +00:00 should be manually processed through swagger once.
insert into siirtotiedostot(id, uuid, window_start, window_end, run_start, run_end, info, success, error_message)
    values (1, '57be2612-ba79-429e-a93e-c38346f1d62d',  '1990-01-01 10:41:20.538107 +00:00', '2024-08-01 00:00:00.000000 +00:00', now(), now(), '{"entityTotals": {}}'::jsonb, true, null) on conflict do nothing;