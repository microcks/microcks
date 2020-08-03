job "microcks" {

  datacenters = ["dc1"]

  group "services" {

    task "microcks" {
      driver = "docker"
      config {
        image = "quay.io/microcks/microcks:nightly"
        volumes = [
          "<current_dir>/config:/deployments/config"
        ]
        network_mode = "nomad_main"
        network_aliases = [
          "microcks"
        ]
        port_map = {
          http = 8080
        }
      }
      resources {
        network {
          port "http" {
            static = "8080"
          }
        }
      }
      service {
        port = "http"
        check {
          type = "http"
          path = "/api/health"
          interval = "10s"
          timeout = "2s"
        }
      }
      env {
        "SPRING_PROFILES_ACTIVE" = "prod"
        "SPRING_DATA_MONGODB_URI" = "mongodb://mongo:27017"
        "SPRING_DATA_MONGODB_DATABASE" = "microcks"
        "POSTMAN_RUNNER_URL" = "http://postman:3000"
        "TEST_CALLBACK_URL" = "http://microcks:8080"
        "SERVICES_UPDATE_INTERVAL" = "0 0 0/2 * * *"
        #"KEYCLOAK_URL" = "https://localhost:8543/auth"
        "KEYCLOAK_URL" = "https://docker.for.mac.localhost:8543/auth"
        #"KEYCLOAK_URL" = "https://docker.for.win.localhost:8543/auth"
      }
    }

    task "postman" {
      driver = "docker"
      config {
        image = "quay.io/microcks/microcks-postman-runtime:latest"
        network_mode = "nomad_main"
        network_aliases = [
          "postman"
        ]
        port_map = {
          http = 3000
        }
      }
      resources {
        network {
          port "http" {
            static = "3000"
          }
        }
      }
    }

    task "keycloak" {
      driver = "docker"
      config {
        image = "jboss/keycloak:10.0.1"
        args = [
          "-b", "0.0.0.0",
          "-Dkeycloak.import=/microcks-keycloak-config/microcks-realm-sample.json"
        ],
        volumes = [
          "<current_dir>/keycloak-realm:/microcks-keycloak-config",
          "<current_dir>/keystore:/etc/x509/https
        ]
        network_mode = "nomad_main"
        network_aliases = [
          "keycloak"
        ]
        port_map = {
          http = 8080
          https = 8443
        }
      }
      env {
        "KEYCLOAK_USER" = "admin"
        "KEYCLOAK_PASSWORD" = "123"
      }
      resources {
        cpu    = 200
        memory = 512
        network {
          port "http" {
            static = "8180"
          }
          port "https" {
            static = "8543"
          }
        }
      }
    }
  }

  group "database" {

    task "mongo" {
      driver = "docker"
      config {
        image = "mongo:3.4.23"
        volumes = [
          "<current_dir>/microcks-data:/data/db"
        ]
        network_mode = "nomad_main"
        network_aliases = [
          "mongo"
        ]
      }
    }
  }
}