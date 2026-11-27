#docker run -it -v $(pwd)/../install/docker-compose/keycloak-realm:/opt/keycloak/data/import -p 8180:8080 \
#  -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin \
#  -e KC_HOSTNAME_URL=http://localhost:8180 -e KC_HOSTNAME_ADMIN_URL=http://localhost:8180 \
#  quay.io/keycloak/keycloak:24.0.4 start-dev --import-realm

docker run -it -v $(pwd)/../install/docker-compose/keycloak-realm:/opt/keycloak/data/import -p 8180:8080 \
  -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:26.0.0 start-dev --hostname http://localhost:8180 --import-realm
