create table siirtotiedosto (
                                 id serial, --todo, voisi katsoa, jos kaikkeen tiedostojen muodostamiseen liittyvään lokitukseen tai ainakin virheisiin saataisiin mukaan tämä id
                                 window_start varchar not null,
                                 window_end varchar not null,
                                 run_start timestamp with time zone,
                                 run_end timestamp with time zone,
                                 success boolean,
                                 error_message varchar, -- Tyhjä string, jos mikään ei mennyt vikaan
                                 PRIMARY KEY (id)
);

--alter table siirtotiedosto owner to oph;

--todo, what are the initial values? This is just something to get the poc started.
insert into siirtotiedosto(id, window_start, window_end, run_start, run_end, success, error_message) values (1, '1990-01-01 10:41:20.538107 +00:00', '2024-04-10 10:41:20.538107 +00:00', now(), now(), true, null) on conflict do nothing;