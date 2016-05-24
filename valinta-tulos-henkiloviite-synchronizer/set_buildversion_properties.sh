#!/bin/bash
set -e
shopt -s nullglob

PROPERTIES=$1
VERSION=$2
BRANCH=$(git rev-parse --abbrev-ref HEAD)
COMMIT=$(git rev-parse HEAD)
TIMESTAMP=$(date --iso-8601=seconds)

set_property() {
    sed -ri "s/($1)=.*/\1=$2/" "${PROPERTIES}"
    if ! grep -q "$1" "${PROPERTIES}"
    then
        printf "$1=%s\n" "$2" >> "${PROPERTIES}"
    fi
}

if [ -e "${PROPERTIES}" ]
then
    set_property "henkiloviite.buildversion.version" "${VERSION}"
    set_property "henkiloviite.buildversion.branch" "${BRANCH}"
    set_property "henkiloviite.buildversion.commit" "${COMMIT}"
    set_property "henkiloviite.buildversion.timestamp" "${TIMESTAMP}"
fi
