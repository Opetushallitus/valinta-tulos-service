#!/bin/bash

test -z "$VIRKAILIJA" && VIRKAILIJA=virkailija.testiopintopolku.fi
test -z "$SERVICE" \
&& SERVICE="https://$VIRKAILIJA/valinta-tulos-service/auth/login"
test -z "$LOGINURL" \
&& LOGINURL="http://localhost:8097/valinta-tulos-service/auth/login"

TGT_URL=$(cat scripts/cas-tgt-url.txt)
test -z "$TGT_URL" && echo "Use scripts/create-cas-tgt.sh first" && exit 1

ST=$(curl -s "$TGT_URL" -H 'Caller-id: panutest' --data-urlencode "service=$SERVICE")
test -z "$ST" && echo "Service ticket was not created" && exit 1

curl -c scripts/cookies.txt -H "ticket: $ST" -H 'caller-id: panutesti' "$LOGINURL"
