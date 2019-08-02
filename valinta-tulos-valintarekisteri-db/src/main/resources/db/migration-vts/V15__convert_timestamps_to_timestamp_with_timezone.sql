alter table vastaanotot
    alter column "timestamp" type timestamp with time zone
        using timestamp with time zone 'epoch' + "timestamp" * interval '1 ms',
    alter column "timestamp" set default now();

alter table deleted_vastaanotot
    alter column "timestamp" type timestamp with time zone
        using timestamp with time zone 'epoch' + "timestamp" * interval '1 ms',
    alter column "timestamp" set default now();

alter table vanhat_vastaanotot
    alter column "timestamp" type timestamp with time zone
        using timestamp with time zone 'epoch' + "timestamp" * interval '1 ms',
    alter column "timestamp" set default now();
