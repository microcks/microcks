job "microcks" {

  datacenters = ["dc1"]

  group "services" {

    task "microcks" {
      driver = "docker"
      config {
        image = "microcks/microcks:latest"
        volumes = [
          #"./config:/deployments/config"
          "/Users/lbroudou/Development/github/microcks/install/nomad/config:/deployments/config"
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
        #"KEYCLOAK_URL" = "http://localhost:8180/auth"
        "KEYCLOAK_URL" = "http://docker.for.mac.localhost:8180/auth"
        #"KEYCLOAK_URL" = "http://docker.for.win.localhost:8180/auth"
      }
    }

    task "postman" {
      driver = "docker"
      config {
        image = "microcks/microcks-postman-runtime:latest"
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
        image = "jboss/keycloak:3.4.0.Final"
        args = [
          "-b", "0.0.0.0",
          "-Dkeycloak.import=/microcks-keycloak-config/microcks-realm-sample.json"
        ],
        volumes = [
          #"./keycloak-realm:/microcks-keycloak-config"
          "/Users/lbroudou/Development/github/microcks/install/nomad/keycloak-realm:/microcks-keycloak-config"
        ]
        network_mode = "nomad_main"
        network_aliases = [
          "keycloak"
        ]
        port_map = {
          http = 8080
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
        }
      }
    }
  }

  group "database" {

    task "mongo" {
      driver = "docker"
      config {
        image = "mongo:3.3.12"
        volumes = [
          "/Users/lbroudou/tmp/microcks-data:/data/db"
        ]
        network_mode = "nomad_main"
        network_aliases = [
          "mongo"
        ]
      }
    }
  }
}