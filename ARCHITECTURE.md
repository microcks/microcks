# Architecture

Microcks is designed as a cloud-native, Kubernetes-ready application.

## Core Components
- **Backend:** A Java-based Quarkus application providing the core API and orchestration logic.
- **Frontend:** A SvelteKit application for the web user interface, with a server layer (BFF) that calls the REST API.
- **Database:** MongoDB for storing mock definitions, tests, and configuration data.
- **IAM/Auth:** Keycloak is used for securing endpoints, managing users, and handling authentication (OAuth2/OIDC).
- **Minions:** Specialized async workers (e.g., AsyncAPI minion) for handling message-driven protocols like Kafka.

## Interfaces
- **REST API:** The primary interface for the frontend UI and the Microcks CLI to interact with the backend.
- **Microcks CLI:** A Go-based command-line tool for CI/CD pipeline integration.

## Extensibility
Microcks is built to be highly extensible. Through the use of plugins and dedicated Minion workers, it can easily adapt to new enterprise protocols or integrate with external Identity Providers.

## Arazzo workflows

Microcks imports [Arazzo](https://spec.openapis.org/arazzo/latest.html) documents, which describe how several APIs are
called together, and reuses the APIs it already mocks to run, mock and verify those workflows.

```
                      ┌──────────────────── Microcks app ─────────────────────┐
 Arazzo document ───▶ │ import ─▶ arazzoDocument          runs ─▶ workflowResult│
 ArazzoMetadata  ───▶ │ links  ─▶ arazzoMetadata (source → API)               │
 ArazzoExamples  ───▶ │ examples ▶ arazzoExamples                             │
                      │  /api/arazzo/**   Mock URL /arazzo/**   sessions      │
                      └───────┬──────────────────────▲────────────────────────┘
                     run request│                      │ step reports, outcome
                              ▼                      │
                      ┌──────────── Arazzo minion ────┴──┐
                      │ plays the steps (artifact-arazzo) │──▶ Microcks mocks / implementations
                      └───────────────────────────────────┘
```

- **Artifacts.** An Arazzo document (`arazzo: 1.0.x | 1.1.x`) is imported once, without creating a Service. Two
  Microcks kinds complete it, matched by `metadata.name` / `version` = the document's `info.title` / `info.version`:
  `ArazzoMetadata` links each source description to a Microcks API, `ArazzoExamples` records expected runs per
  workflow. JSON Schemas: `api/ArazzoMetadata-v1alpha1-schema.json`, `api/ArazzoExamples-v1alpha1-schema.json`.
  Documents outside the supported subset are refused at import with their reason.
- **Kernel.** `artifacts/artifact-arazzo` reads documents, the step order and the runtime expressions. It depends on
  Jackson only, so both the app and the minion use it.
- **App.** Arazzo code lives in `io.microcks.{domain,repository,service,web}.arazzo`. It exposes the REST API under
  `/api/arazzo` (documents, workflows, runs, history, examples, sessions), described in
  `api/microcks-openapi-v2.0.yaml`, the Mock URL
  `POST /arazzo/{title}/{version}/{workflowId}` that plays a workflow on the mocks and answers its outputs, and
  verification sessions: calls to the ordinary mocks carrying `x-microcks-session` (or the `microcks-session` baggage)
  are recorded, answered consistently along the workflow, can fail on purpose, then are verified.
- **Minion.** `minions/arazzo` is a separate Quarkus service that plays runs and reports each step back
  (see its README). The app sends it, per source, a base URL (relative for the mocks), the request media types of
  each operation and, when asked, the OpenAPI contract for schema validation.
- **Web UI.** The Workflows screens of the SvelteKit app read `/api/arazzo` through the BFF (`lib/server/workflows.ts`,
  `runs-arazzo.ts`, `sessions-arazzo.ts`). Features not served yet are disabled through one rule,
  `lib/workflows/availability.ts`.
