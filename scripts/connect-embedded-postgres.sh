
PG_PORT=$(docker inspect valintarekisteri-postgres |
 jq -r '.[].NetworkSettings.Ports["5432/tcp"][].HostPort')

psql postgres://oph:oph@localhost:$PG_PORT/valintarekisteri
