create table if not exists siirtotiedostot (
                                             id serial, --todo, voisi katsoa, jos kaikkeen tiedostojen muodostamiseen liittyvään lokitukseen tai ainakin virheisiin saataisiin mukaan tämä id
                                             uuid varchar,
                                             window_start varchar not null,
                                             window_end varchar not null,
                                             run_start timestamp with time zone,
                                             run_end timestamp with time zone,
                                             info jsonb, --ainakin tilastot tiedostoihin päätyneistä entiteettimääristä tyypeittäin, {"entityTotals": {"tyyppi1": 300, "tyyppi2": 13}}
                                             success boolean,
                                             error_message varchar,
                                             PRIMARY KEY (id)
);

COMMENT ON column siirtotiedostot.run_start IS 'Siirtotiedosto-operaation suorituksen alkuaika';
COMMENT ON column siirtotiedostot.run_end IS 'Siirtotiedosto-operaation suorituksen loppuaika';
COMMENT ON column siirtotiedostot.info IS 'Tietoja tallennetuista entiteeteistä, mm. lukumäärät';
COMMENT ON column siirtotiedostot.error_message IS 'null, jos mikään ei mennyt vikaan';

--alter table siirtotiedosto owner to oph;

--todo, what are the initial values? This is just something to get the poc started.
insert into siirtotiedostot(id, uuid, window_start, window_end, run_start, run_end, info, success, error_message)
    values (1, '57be2612-ba79-429e-a93e-c38346f1d62d',  '1990-01-01 10:41:20.538107 +00:00', '2024-04-10 10:41:20.538107 +00:00', now(), now(), '{"entityTotals": {}}'::jsonb, true, null) on conflict do nothing;