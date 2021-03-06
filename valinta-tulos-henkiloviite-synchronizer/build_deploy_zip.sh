#!/bin/bash
set -e
shopt -s nullglob

SERVICE=valinta-tulos-henkiloviite-synchronizer
BRANCH=master
ZIP=${PWD}/target/${SERVICE}_${BRANCH}_$(date +%Y%m%d%H%M%S).zip

cd ./target
JAR=( ./*jar-with-dependencies.jar )
[ ${#JAR[@]} -lt 1 ] && { printf "No runnable jar found\n"; exit 1; }
[ ${#JAR[@]} -gt 1 ] && { printf "Multiple runnable jars found: %s\n" "${JAR[*]}"; exit 1; }
cp "${JAR[0]}" "${SERVICE}.jar"
zip -m "${ZIP}" "${SERVICE}.jar"
cd -
cd target/classes
zip -r "${ZIP}" oph-configuration
cd -
