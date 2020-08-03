#!/bin/bash

mkdir keystore
mkdir microcks-data

echo "Generating local keys, certs and keystore into ./keystore folder ..."
echo

docker run -v $PWD/keystore:/certs -t nmasse/mkcert:0.1

echo
echo "Renaming stuffs to match Microcks and Keycloak constraints ..."
echo

mv ./keystore/server.crt ./keystore/tls.crt
mv ./keystore/server.key ./keystore/tls.key
mv ./keystore/server.p12 ./keystore/microcks.p12

echo "Creating docker network for nomad job ..."
docker network create --driver bridge nomad_main

echo "Replacing mount paths with current path"
export pwd=`pwd`
perl -i.bak -pe 's|<current_dir>/nomad|${pwd}|' microcks.nomad
