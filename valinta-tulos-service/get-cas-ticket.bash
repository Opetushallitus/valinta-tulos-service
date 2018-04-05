#!/bin/bash

if [[ ! -x `which http` ]]; then
  echo "You need to have httpie (http) in path."
  exit 2
fi

if [ -z $2 ]; then
  echo "Usage: $0 <username> <password> [virkailija base url] [target service URL]"
  exit 3
fi

USERNAME=$1
PASSWORD=$2

if [ -z $3 ]; then
  VIRKAILIJA=https://virkailija.testiopintopolku.fi
else
  VIRKAILIJA=$3
fi

if [ -z $4 ]; then
  SERVICE=$VIRKAILIJA/valinta-tulos-service
else
  SERVICE=$4
fi

echo "Making the TGT request with:"
TGT_REQUEST="http --pretty none --form --print h POST $VIRKAILIJA/cas/v1/tickets username=$USERNAME password=$PASSWORD"
echo $TGT_REQUEST
TGT_LOCATION_LINE=`$TGT_REQUEST | grep Location | cut -f 2 -d ' '`
echo "TGT_LOCATION_LINE: $TGT_LOCATION_LINE"
TGT_LOCATION=`echo ${TGT_LOCATION_LINE} | awk '{gsub(/[[:cntrl:]]/,"")}1'`

echo "Making the service ticket request to \"$TGT_LOCATION\" "

http --form --print b POST $TGT_LOCATION service=$SERVICE

