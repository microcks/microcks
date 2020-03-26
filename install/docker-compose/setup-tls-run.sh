#!/bin/bash

mkdir keystore

echo "Generating local keys, certs and keystore into ./keystore folder ..."
echo

docker run -v $PWD/keystore:/certs -t nmasse/mkcert:0.1

echo
echo "Renaming stuffs to match Microcks and Keycloak constraints ..."
echo

mv ./keystore/server.crt ./keystore/tls.crt
mv ./keystore/server.key ./keystore/tls.key
mv ./keystore/server.p12 ./keystore/microcks.p12

echo
echo "Starting Microcks using docker-compose ..."
echo "------------------------------------------"
echo "Stop it with: docker-compose -f microcks.yml stop"
echo "Re-launch it with: docker-compose -f microcks.yml start"
echo "Clean everything with: docker-compose -f microcks.yml down"
echo "------------------------------------------"
echo "Go to https://localhost:8080 - first login with admin/123"
echo "Having issues? Check you have changed microcks.yml to your platform"
echo

docker-compose -f microcks.yml up -d

