# Based on <https://hub.docker.com/_/postgres/>

FROM docker.io/library/postgres:11.9

# COPY ../postgresql.conf /var/lib/postgresql
COPY ./configure-postgres.sh /docker-entrypoint-initdb.d/
COPY ./configure-valintarekisteri-database.sh /docker-entrypoint-initdb.d/
