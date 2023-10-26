docker run -it -v $(pwd)/../install/docker-compose/keycloak-realm:/opt/keycloak/data/import -p 8180:8080 \
  -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin \
  -e KC_HOSTNAME_URL=http://localhost:8180 -e KC_HOSTNAME_ADMIN_URL=http://localhost:8180 \
  quay.io/keycloak/keycloak:22.0.2 start-dev --import-realm
