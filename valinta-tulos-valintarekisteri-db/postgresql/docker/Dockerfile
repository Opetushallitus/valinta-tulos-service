# Based on <https://hub.docker.com/_/postgres/>

FROM docker.io/library/postgres:15

# COPY ../postgresql.conf /var/lib/postgresql
COPY ./configure-postgres.sh /docker-entrypoint-initdb.d/
COPY ./configure-valintarekisteri-database.sh /docker-entrypoint-initdb.d/
