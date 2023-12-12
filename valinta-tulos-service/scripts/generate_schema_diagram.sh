#!/bin/bash

set -e

if [ $# -ne 4 ]
then
    printf "Usage: $0 <host> <port> <output dir> <version>\n"
    exit 1
fi

HOST=$1
PORT=$2
OUT=$3
VERSION=$4
PREFIX="$3/valintarekisteri-${VERSION}"

mkdir -p "${OUT}"
postgresql_autodoc -s public -d valintarekisteri -h "${HOST}" -p "${PORT}" \
                   -f "${PREFIX}"
dot -Tpng "${PREFIX}.dot" -o"${PREFIX}.png"
