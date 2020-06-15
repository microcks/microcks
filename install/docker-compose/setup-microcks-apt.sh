#!/bin/bash

HOSTNAME=$1
VERSION=$2

echo "Installing jq and docker-compose packages ..."
apt-get update
apt-get install -y jq docker-compose

cd /opt
echo "Retrieving microcks sources ..."
git clone https://github.com/microcks/microcks.git
if [ ${VERSION} != "latest" ]; then
  git checkout tags/${VERSION}
fi

cd microcks/install/docker-compose/
cp keycloak-realm/microcks-realm-sample.json keycloak-realm/microcks-realm-sample.json.bak

echo "Replacing hostname in configuration files ..."
jq '.applications |= map(if .name == "microcks-app-js" then .redirectUris = [ "https://'${HOSTNAME}':8080/*" ] else . end)' keycloak-realm/microcks-realm-sample.json.bak > keycloak-realm/microcks-realm-sample.json

perl -i.bak -pe 's|- KEYCLOAK_URL=https://docker.for.mac.localhost:8543/auth|#- KEYCLOAK_URL=https://docker.for.mac.localhost:8543/auth|' microcks.yml
perl -i.bak -pe 's|#- KEYCLOAK_URL=https://localhost:8180/auth|- KEYCLOAK_URL=http://'${HOSTNAME}':8543/auth|' microcks.yml

echo "Generating certificates for '$HOSTNAME' ..."
mkdir keystore
docker run -v $PWD/keystore:/certs -e SERVER_HOSTNAMES="$HOSTNAME" -it nmasse/mkcert:0.1
mv ./keystore/server.crt ./keystore/tls.crt
mv ./keystore/server.key ./keystore/tls.key
mv ./keystore/server.p12 ./keystore/microcks.p12

if [ ${VERSION} != "latest" ]; then
  echo "Replacing version in configuration files ..."
  perl -i.bak -pe 's|microcks/microcks:latest|microcks/microcks:${VERSION}|' microcks.yml
fi

echo
echo "Microcks is now installed with self-signed certificates"
echo "------------------------------------------"
echo "Start it with: docker-compose -f microcks.yml up -d"
echo "Stop it with: docker-compose -f microcks.yml stop"
echo "Re-launch it with: docker-compose -f microcks.yml start"
echo "Clean everything with: docker-compose -f microcks.yml down"
echo "------------------------------------------"
echo "Go to https://$HOSTNAME:8080 - first login with admin/123"
echo "Having issues? Check you have changed microcks.yml to your platform"
echo

