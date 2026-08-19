# Architecture

Microcks is designed as a cloud-native, Kubernetes-ready application.

## Core Components
- **Backend:** A Java-based Spring Boot application providing the core API and orchestration logic.
- **Frontend:** An Angular-based SPA for the web user interface.
- **Database:** MongoDB for storing mock definitions, tests, and configuration data.
- **IAM/Auth:** Keycloak is used for securing endpoints, managing users, and handling authentication (OAuth2/OIDC).
- **Minions:** Specialized async workers (e.g., AsyncAPI minion) for handling message-driven protocols like Kafka.

## Interfaces
- **REST API:** The primary interface for the frontend UI and the Microcks CLI to interact with the backend.
- **Microcks CLI:** A Go-based command-line tool for CI/CD pipeline integration.

## Extensibility
Microcks is built to be highly extensible. Through the use of plugins and dedicated Minion workers, it can easily adapt to new enterprise protocols or integrate with external Identity Providers.
