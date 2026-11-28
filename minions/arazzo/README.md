# Microcks Arazzo minion

The Arazzo minion plays the runs of [Arazzo](https://spec.openapis.org/arazzo/latest.html) workflows for Microcks.
Microcks imports the Arazzo documents, links their source descriptions to its APIs and keeps the runs; the minion
calls the APIs step by step and reports each step back. Microcks itself never calls the APIs of a workflow.

```
Microcks app ── POST /api/runs ──────────────▶ Arazzo minion
     ▲                                              │ one call per step
     │  POST /api/workflows/runs/{id}/steps            ├──▶ Microcks mocks  /rest/<API>/<version>/…
     └─ POST /api/workflows/runs/{id}/outcome ◀────────┤    (W3C baggage microcks-arazzo-*)
                                                    └──▶ implementations (base URL + secret)
```

## What it does

- Receives a run request: the Arazzo document, the workflow, its inputs, the base URL of each source (a relative URL
  means the Microcks mocks), the request media types of each operation, an optional secret, timeout and schema
  validation.
- Plays the steps in the order of the list: parameters, request body, success criteria, outputs, `onSuccess` /
  `onFailure` actions (`end`, `retry`), with the runtime expressions of `microcks-artifact-arazzo`.
- Validates each JSON response against the OpenAPI or Swagger contract when the run asks for it
  (`microcks-artifact-openapi`).
- Sends the run secret to implementations only, never to the mocks. Calls to the mocks carry a W3C `baggage`
  (`microcks-arazzo-run`, `-workflow`, `-step`, `-attempt`) that Microcks records in Live Traces.
- Reports every step, then the outcome. An unexpected error ends the run as `FAILURE` with its reason, so a run never
  stays in progress.

Not played yet: AsyncAPI steps (Microcks refuses the launch), steps in another order than the list (`dependsOn` is
accepted when it names earlier steps).

## Configuration

| Property | Environment variable | Default | What |
|---|---|---|---|
| `quarkus.http.port` | `QUARKUS_HTTP_PORT` | `8082` | Port of the run endpoint |
| `microcks.url` | `MICROCKS_URL` | `http://localhost:8080` | Microcks, to report the runs and to reach its mocks |

On the Microcks side, `arazzo-minion.url` (`ARAZZO_MINION_URL`, default `http://localhost:8082`) points to the minion.

Known gap: when Microcks runs with authentication, the minion reports without a token.

## Build, run and test

```bash
# from the repository root
mvn -pl minions/arazzo -am package -DskipTests
java -jar minions/arazzo/target/quarkus-app/quarkus-run.jar

# unit and Quarkus tests
mvn -pl minions/arazzo test
# integration tests with Testcontainers (Docker needed)
mvn -pl minions/arazzo verify -Pit
```

The contract between Microcks and the minion is pinned on both sides by `ArazzoMinionContractTest` and the shared
example `arazzo-minion-contract.json`: change both together.

## Code map

| Package | Role |
|---|---|
| `io.microcks.minion.arazzo` | `RunResource` (`POST /api/runs`), `ArazzoRunManager` (one virtual thread per run) |
| `io.microcks.minion.arazzo.client` | DTOs of the run request and of the reports, REST client to Microcks |
| `io.microcks.minion.arazzo.runner` | `WorkflowRunner`, HTTP transport, criteria, schema validation, baggage, secret |
