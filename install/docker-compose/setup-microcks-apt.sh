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
jq '.applications |= map(if .name == "microcks-app-js" then .redirectUris = [ "http://'${HOSTNAME}':8080/*" ] else . end) | .sslRequired |= "none"' keycloak-realm/microcks-realm-sample.json.bak > keycloak-realm/microcks-realm-sample.json

perl -i.bak -pe 's|KEYCLOAK_URL=http://localhost:8180/auth|KEYCLOAK_URL=http://'${HOSTNAME}':8180/auth|' microcks.yml
perl -i.bak -pe 's|keycloak.ssl-required=external|keycloak.ssl-required=none|' config/application.properties

if [ ${VERSION} != "latest" ]; then
  echo "Replacing version in configuration files ..."
  perl -i.bak -pe 's|microcks/microcks:latest|microcks/microcks:${VERSION}|' microcks.yml
fi