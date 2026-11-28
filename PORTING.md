# Porting log: Microcks 1.x (master) → 2.0 (`v2/`)

This file records the transformations applied when porting `microcks/microcks` to the 2.0 code base in this
directory. Its purpose is to let us replay upstream changes incrementally: diff upstream `master` from the baseline
commit below, then map each changed file with the tables in this document.

## Baseline

| | |
|---|---|
| Upstream repository | `https://github.com/microcks/microcks` |
| Baseline commit | `f757641ed34c64e4d9885c6c1c89abd17580aeff` (`master`, "chore(deps-dev): bump maven-clean-plugin (#2398)"), synchronized on 2026-11-27 |
| Initial baseline | `608ddb53a824483c781c1ba277040a8971f43b63` (`master`, 2026-10-16, "fix: #2293 Restore package-lock.json") |
| Upstream version | `1.16.0-SNAPSHOT` |

The 2.0 code lives on the `v2` branch of `microcks/microcks`, built from `1e8b6c60` with a pure-rename commit followed by
the 2.0 code, so `git log --follow` and `git cherry-pick` traverse the moves.

Synchronizing the 1.x commits: run `./sync-upstream.sh . report` (dry run: OK / SKIP / CONFLICT / ORPHAN per commit), then
`REFS="Refs #<issue>" SIGNOFF="Signed-off-by: Name <mail>" ./sync-upstream.sh . apply`. Commits are cherry-picked with the
original author and a `(cherry picked from commit ...)` trailer. On CONFLICT or ORPHAN (new files left under `webapp/`),
fix by hand, `git commit` with the same trailer, and re-run. Compile before committing: upstream wildcard imports hide
missing imports. Update the baseline above and the history below after every synchronization.

## Layout and naming

| 1.x | 2.0 |
|---|---|
| `pom.xml` (reactor) | `v2/pom.xml` (independent reactor, parent `io.microcks:microcks-parent:2.0.0-SNAPSHOT`) |
| `commons/model` | `v2/commons/model` |
| `commons/util` | dissolved: validators and helpers moved into the format modules (see below) |
| `commons/util-el` | `v2/commons/util-el` (artifact `microcks-el`) |
| `webapp` (Spring Boot) | `v2/app` (Quarkus, artifact `microcks-app`) |
| `webapp/src/main/java/io/github/microcks/util/<format>` | `v2/artifacts/artifact-<format>` |
| `minions/async` (artifact `microcks-async-minion`) | `v2/minions/async` (same artifactId, groupId `io.microcks`) |
| `distro/uber-async-minion` | `v2/distro/uber-async-minion` (artifact `microcks-uber-async-minion`, parent `microcks-distro`) |
| `distro/uber` | `v2/distro/uber` (artifact `microcks-uber-app`, parent `microcks-distro`, Quarkus on top of `microcks-app`) |
| `samples/` (files used by tests) | `v2/samples/` (duplicated, only referenced files) |

Everything under `v2/` is self-contained: relative paths used by tests (`../samples`, `target/test-classes/...`)
resolve inside `v2/`. Anything upstream that `v2/` references must be duplicated here, not referenced outside.

## Global transformations

1. **Group and packages**: `io.github.microcks` → `io.microcks` (Maven `groupId` and Java packages). External
   artifacts keep their own groupId (for example `io.github.microcks:protoc-jar`).
2. **Version**: `2.0.0-SNAPSHOT`, Java 25.
3. **Frameworks**: Spring Boot → Quarkus (JAX-RS controllers, CDI beans and events, SmallRye Kafka,
   Panache MongoDB repositories).
4. **Persistence**: MongoDB is kept (decision M2-001), on a newer version. Repositories use Panache MongoDB base
   repositories; no custom `AbstractMongoRepository`/`Page`/`PageRequest` classes.
5. **commons Spring-free**: `model` and `util-el` are copies without Spring dependencies.
6. **Mass renames** were done by script (`mv`, `sed`, Python), not by hand. Files were moved first, then every
   reference was rewritten class by class from an old→new FQN map; leftovers were fixed from compiler errors.
7. **Virtual threads**: Quarkus has no global switch like Spring's `spring.threads.virtual.enabled`. Every
   `@Path` resource class carries `io.smallrye.common.annotation.RunOnVirtualThread`. Methods returning `Multi`
   (SSE in `TracingController` and `McpController`) stay on the event loop, so only their sibling methods are
   annotated. New resources must add the annotation.

## `io.microcks.util` modularization (M2-025)

The `util` package of the webapp was split into Maven modules under `v2/commons/` (shared modules) and `v2/artifacts/` (one module per artifact format, plus `artifact-core`).

| Module | Content | Package(s) |
|---|---|---|
| `util` (`commons/util`) | General purpose helpers: `URIBuilder`, `HTTPDownloader`, `UTF8ContentTypeChecker`, `DataUriUtil`, `OperationPathPattern`, `ReferenceResolver`, `*ReferenceURLBuilder*`, `DispatchStyles`, `DispatchCriteriaHelper`, `ObjectMapperFactory`, `IdBuilder`, `Sanitizer` (moved from `model`), `dispatcher/*` | `io.microcks.util`, `io.microcks.util.dispatcher` |
| `artifact-core` | Import/export framework: `MockRepositoryImporter/Exporter` (+ factories, providers, exceptions), `AbstractJsonRepositoryImporter`. Depends on `util`; shares the `io.microcks.util` package with it (split package accepted for now) | `io.microcks.util` |
| `artifact-metadata` | former `util.metadata` | `io.microcks.artifact.metadata` (a few classes still in `io.microcks.util`) |
| `test-core` | `AbstractTestRunner`, `HttpTestRunner`, `SoapHttpTestRunner`, `TestRunnerCommons`, `util.http.*` (except `HttpHeadersUtil`), SPI `ResourceReader` / `ResponseReader` / `SecretReader` | `io.microcks.test`, `io.microcks.util.http` |
| `artifact-openapi` | importer, runner, overlay | `io.microcks.artifact.openapi` |
| `artifact-asyncapi` | importers, runner | `io.microcks.artifact.asyncapi` |
| `artifact-graphql` | importer, runner | `io.microcks.artifact.graphql` |
| `artifact-grpc` | `ProtobufImporter`, `GrpcUtil`, `GrpcMetadataUtil`, runner | `io.microcks.artifact.grpc` |
| `artifact-soapui` | importer, assertions runner, `WritableNamespaceContext` (moved from `io.microcks.util`) | `io.microcks.artifact.soapui`, `io.microcks.artifact.soapui.assertions` |
| `artifact-postman` | collection and workspace importers, test steps runner | `io.microcks.artifact.postman` |
| `artifact-har` | HAR importer | `io.microcks.artifact.har` |
| `artifact-opencollection` | OpenCollection importer (added upstream after the baseline) | `io.microcks.artifact.opencollection` |

Mapping rule: `io.github.microcks.util.<format>.X` → `io.microcks.<format>.X`, in the module `artifact-<format>`.
Tests and test resources follow their classes (`src/test/resources/io/microcks/util/<format>` →
`<module>/src/test/resources/io/microcks/<format>`).

Classes that stay in the webapp:

| 1.x | 2.0 (webapp) |
|---|---|
| `util.openapi.OpenAPIMcpToolConverter`, `util.graphql.GraphQLMcpToolConverter`, `util.grpc.GrpcMcpToolConverter` | `io.microcks.web.mcp` |
| `util.graphql.GraphQLHttpRequest` | `io.microcks.web` |
| `GrpcMockHandlerRegistry`, `ProtoReflectionService` | `io.microcks.web.grpc` |
| `util.http.HttpHeadersUtil` (uses `HttpServletRequest`) | `io.microcks.util.http` |
| `MockRepositoryImporterFactory`, `MockRepositoryExporterFactory` (moved to `artifact-core`, now `ServiceLoader` registries of `MockRepositoryImporterProvider` / `MockRepositoryExporterProvider`; importer priorities: opencollection 5, postman 10-11, openapi 20-21, soapui 30, asyncapi 31-32, grpc 33, metadata 34-35, graphql 36, HAR = default) | `io.microcks.util` |
| `TestRunnerService.retrieveRunner` switch (replaced by `TestRunnerFactory` in `test-core` + `TestRunnerProvider` SPI per format, fed by a `TestRunnerContext`) | `io.microcks.test` |
| `SafeLogger`, `ResourceUtil`, `AbsoluteUrlMatcher`, `ParameterConstraintUtil`, `EntityAlreadyExistsException`, `UsernamePasswordProxyAuthenticator` | `io.microcks.util` |

### Logic-level changes (beyond renames)

- `StringToStringsMap` and `HttpHeadersStringToStringsMap` moved from `util.script` to `util.http` to break the
  `http` ⇄ `script` cycle.
- Test runners no longer depend on Mongo repositories: they use `ResourceReader`, `ResponseReader`, `SecretReader`
  (`test-core`), implemented directly by the webapp Panache repositories.
  `SoapHttpTestRunner` field renamed `resourceRepository` → `resourceReader` (same for the other runners).
- `HTTPDownloader` decodes PEM certificates with `java.util.Base64.getMimeDecoder()` instead of commons-codec.
- `GrpcMetadataUtil`: `com.nimbusds.jose.util.StandardCharset` → `java.nio.charset.StandardCharsets`.
- `SimpleContainsAssertion`: `io.micrometer.common.util.StringUtils.isNotEmpty` → plain null/empty check.

### Validators and helpers of the former `util` module

`commons/util` no longer exists. Its classes moved next to their format, with the `io.microcks.<format>` packages
(the `util` segment is dropped). Upstream changes to `io.github.microcks.util.*` map as follows:

| 1.x class(es) | 2.0 module (package) |
|---|---|
| `JsonSchemaValidator`, `JsonSchemaDialect` | `artifact-jsonschema` (`io.microcks.artifact.jsonschema`), shared by OpenAPI, AsyncAPI, GraphQL |
| `OpenAPISchemaValidator`, `SwaggerSchemaValidator`, `OpenAPISchemaBuilder` | `artifact-openapi` (`io.microcks.artifact.openapi`) |
| `AsyncAPISchemaValidator`, `AsyncAPISchemaUtil`, `AsyncAPISchemaException`, `AvroUtil`, `SchemaMap` | `artifact-asyncapi` (`io.microcks.artifact.asyncapi`), owns the `avro` dependency |
| `GraphQLSchemaValidator`, `JsonSchemaBuilderQueryVisitor` | `artifact-graphql` (`io.microcks.artifact.graphql`) |
| `SoapMessageValidator`, `XmlUtil`, `XmlSchemaValidator`, `XmlSchemaURLResolver`, `XmlErrorHandler`, `MalformedXmlException`, SOAP/XML `.xsd` | `artifact-soap` (`io.microcks.artifact.soap`) |

`SoapHttpTestRunner` stays in `test-core` for now (it depends on `artifact-soap`); it will move when the test and
mock-controller classes are refactored. Moved unit tests keep their fixtures under `src/test/resources/io/microcks/<format>/validation/`
(`avro/`, `soap/` and `jsonschema/` for the others) to avoid name clashes with the importer fixtures.

## Async minion (`minions/async`, `distro/uber-async-minion`)

- Packages `io.github.microcks.*` → `io.microcks.*`, including test resource directories
  (`src/test/resources/io/microcks/minion/async`) and `META-INF/native-image/io.microcks/microcks-async-minion`.
  Avro `namespace` values used as test data (`io.github.microcks.User`) are data and were not renamed.
- Dependencies: `microcks-model`, `microcks-el` and `microcks-artifact-asyncapi` (replaces the former `microcks-util`;
  `AvroUtil` and `SchemaMap` → `io.microcks.artifact.asyncapi`, `AsyncAPISchemaUtil` and `AsyncAPISchemaValidator`
  → `io.microcks.artifact.asyncapi`). The Spring Data MongoDB exclusion on the model is no longer needed.
- Quarkus version comes from the parent (`quarkus.platform.version`, BOM `io.quarkus.platform:quarkus-bom`) instead of
  a per-module `quarkus-universe-bom`. As the platform BOM only covers core extensions, `minions/async` and
  `distro/uber-async-minion` additionally import `quarkus-amazon-services-bom` and `quarkus-google-cloud-services-bom`
  (same version) for the Quarkiverse extensions.
- The `native` profile of the minion uses `quarkus.native.enabled` (`quarkus.package.type` no longer exists).
- Behavior is unchanged: the uber minion keeps the WebSocket client endpoints `/api/services-updates` and
  `/api/asyncapi-triggers` of 1.x, and the REST calls on `/api/services`. Keycloak is gone: the minion asks
  `/api/auth/config` and, when authentication is enabled, gets a token with `client_credentials` on
  `/api/auth/token` (`microcks.serviceaccount`, `microcks.serviceaccount.credentials`), renewed before expiry and sent
  as Bearer on REST calls and on the WebSocket handshake (`MicrocksTokenProvider`).
- Native image (`distro/uber-async-minion`): the `native` profile adds `org.tukaani:xz` (optional dependency of
  commons-compress, pulled by Avro, that must be resolvable at build time). The native build is run with
  `mvn install -pl distro/uber-async-minion -DskipTests -Dnative -Dquarkus.native.container-build=true`
  after installing the minion jar (do not add `-am`: the minion's own `native` profile has no native build arguments,
  as in 1.x, and fails on `sun.java2d.pipe.Region`). It needs roughly 10 GB of memory.
- M2-026, app → minion link: Kafka is no longer an infrastructure dependency of the minion (Kafka stays a supported
  mock protocol). The `@Incoming` channels, the two Kafka deserializers, `mp.messaging.incoming.*`,
  `quarkus-messaging-kafka` and `quarkus-reactive-messaging-http` are removed. Both the full and uber minions now use
  `io.microcks.minion.async.client.MicrocksWebSocketConnector` (Vert.x WebSocket client, in `minions/async`) on
  `/api/services-updates` and `/api/asyncapi-triggers`. The WS URL is derived from the single
  `io.microcks.minion.async.client.MicrocksAPIConnector/mp-rest/url` property (http→ws, https→wss); the uber minion sets
  it from `MICROCKS_HOST_PORT`. The connector connects first, then runs `AsyncMinionApp.synchronize()` (full resync,
  idempotent), then applies events sequentially in arrival order on a single worker thread. On close/error/unreachable
  app it reconnects with exponential backoff (1 s → 30 s, `minion.microcks-ws.min-backoff`, `.max-backoff`, in ms) and
  resynchronizes, as the app does not replay events. Keep-alive pings every `minion.microcks-ws.ping-interval` ms.
  `ProducerScheduler.scheduleAllProducerJobs()` no longer duplicates jobs.
- Tests: `*Test` classes run with `mvn install`; `*IT` classes (Testcontainers) run with `-Pit`.

## Uber distribution (`distro/uber`)

- Spring `EmbeddedMongoConfiguration` → `EmbeddedMongoServer` (static holder, in-memory by default, H2 file backend when
  `mongodb.storage.file` or `MONGODB_STORAGE_PATH` is set, stopped by a shutdown hook) started lazily by
  `EmbeddedMongoConfigSourceFactory` (SmallRye `ConfigSourceFactory`, property `microcks.uber.mongodb-url`, referenced by
  `quarkus.mongodb.connection-string`). The server is therefore up before the first Mongo connection, with no Dev
  Service and no `QuarkusTestResource`. Dev Services for Mongo and Kafka are disabled.
- `app` in dev mode (`quarkus:dev`) uses a persistent MongoDB on port 27018 (Dev Services disabled: they cannot mount a volume): `docker compose -f dev/compose.yaml up -d`, data in the `mongo-data` volume (`down -v` wipes it), override with `MONGODB_URL`.
- The module is always in "uber mode" (no `uber` profile as in 1.x): authentication is disabled there (`microcks.auth.enabled=false`). Kafka libraries
  stay on the classpath; `KafkaEventPublicationChannel` is excluded (`quarkus.arc.exclude-types`) and the outgoing
  channels are disabled.
- `WebSocketEventPublicationChannel` (CDI `@Alternative @Priority(1)` over `EventPublicationChannel`, so the publishers
  in `v2/app` are unchanged) with `quarkus-websockets-next` endpoints `/api/services-updates` and
  `/api/asyncapi-triggers`; same paths and JSON as 1.x, ignoring the minion's `_ready_` message. Sends are serialized per
  channel and blocking, so the order seen by the channel is kept, one connection gets each message once. A client not
  acknowledging a send within 10 s is dropped. Events fired with CDI `fireAsync` may already be reordered upstream.
- `microcks-app` and `microcks-model` have no Jandex index: `quarkus.index-dependency` entries are needed. The module
  shares the packages `io.microcks.config`/`listener` with the app (split packages warning).
- `io.grpc:grpc-netty-shaded` is excluded (native-image cannot link the shaded Netty); gRPC runs on `grpc-netty`.
- Native (`-Dnative -Dquarkus.native.container-build=true`, Mandrel `jdk-25` builder image, about 10 GB of memory): the
  profile adds `org.brotli:dec`, `org.tukaani:xz` and `com.github.luben:zstd-jni` (optional codecs of commons-compress,
  pulled by Avro, linked at build time); run-time initialization of `net.datafaker`, `org.h2`, `de.bwaldvogel.mongo`,
  `org.apache.commons.compress`, `graphql.util.IdGenerator`, `sun.java2d.pipe.Region` and the random generator of
  `AbstractRandomELFunction`; `META-INF/services` and protoc includes as resources; reflection for `ServiceLoader`
  providers (importers, runners, gRPC) and Java serialization hints for what mongo-java-server stores in H2.
  The v1 `reflect-config.json` is not ported (Spring/Netty specific).
- Verified: JVM and native import of `APIPastry-openapi.yaml`, mock call, `GET /api/services`, file persistence across
  restarts. Tests: 3 `@QuarkusTest` in `UberDistributionTest`.
- gRPC in native: the default `ServerRegistry`/`ManagedChannelRegistry` are empty in the image (ServiceLoader not resolved), so `GrpcServiceLoaderSupport` registers `NettyServerProvider`/`NettyChannelProvider` at startup (verified with grpcurl). Known gaps: WebSocket endpoints are unauthenticated (M2-002); not tested
  against the real minion `MicrocksWebSocketConnector`; no `-Pnative` integration test.

## Test resources

- Each format module owns its resources. The webapp reads them through `<testResources>` entries in
  `v2/app/pom.xml` pointing to `../artifacts/artifact-*/src/test/resources`.
- `films-postman.json` is duplicated in `artifact-postman` (used by a Postman test and by GraphQL tests).
- `v2/samples/` duplicates `HelloService-soapui-project.xml` and `PetstoreAPI-collection.json` from upstream
  `samples/` (used by `MockRepositoryImporterFactoryTest`).

## Web UI (M2-009)

- `v2/web-ui` is a new SvelteKit 3 + Tailwind 4 UI (Deep Ink design system), not a port of the Angular `webapp-ui`.
  Nothing upstream maps to it. See `v2/web-ui/README.md`.
- It currently runs on an in-memory demo store, not on the Quarkus API.
- `v2/samples/` also holds the sample contracts used to populate the UI (27 files copied from upstream `samples/`).
- All remaining demo screens (Cockpit, Service, Behavior, Copilot, Workflows, Runs, Agent test, Tests, Usage, Traces, Hub, Map, 404) are ported; see `web-ui/README.md` for the placeholder status.
- The *Administration* page (`/admin`) is new: groups and memberships (`/api/groups`), secrets, snapshots and configuration are real; the Users tab shows the accounts created at first OIDC sign-in (`/api/users`) joined with memberships; see `v2/demo/keycloak/`.
- The *Run a test* page (`/tests/create`) is also new: runners are derived from imported artifact kinds; launch records a request only.
- Service detail: the "Used in N workflows" block lists the workflows of the imported Arazzo documents whose linked sources point at the service (hidden on placeholder data).

## Arazzo (fork `SebastienDegodez/microcks`, branch `v2/arazzo`)

Port of the Arazzo POC of `feature/arazzo` (1.x, Spring Boot + Angular, kept untouched as the reference), story by
story. The Angular screens are not ported: the SvelteKit Workflows screens of the Deep Ink mockup are wired to the API.
Architecture: see the Arazzo section of `ARCHITECTURE.md` and `minions/arazzo/README.md`.

| 1.x (`feature/arazzo`) | 2.0 |
|---|---|
| `commons/util` `io.github.microcks.util.arazzo` (reader, expressions) | `artifacts/artifact-arazzo` (`io.microcks.artifact.arazzo`), Jackson only (ArchUnit test); the minion's expression evaluator and JSON Pointer moved there too |
| Flat `domain`, `repository`, `service`, `web` | `io.microcks.{domain,repository,service,web}.arazzo` (+ `web.arazzo.dto`), tests mirrored; moved by a pure-rename commit |
| Spring Data entities and `Custom*Repository` + `MongoTemplate` | `@MongoEntity` + Panache `ObjectIdRepository` (same collection names); `ObjectIdRepository.save` added; `Object` values need `config.AnyValueCodecProvider`; TTL and unique indexes created at startup by the repositories (no `MongoConfiguration`) |
| `@Service`, `@Value`, Spring MVC controllers | CDI `@ApplicationScoped`, `@ConfigProperty`, JAX-RS `@RunOnVirtualThread` under the same paths; an unknown enum filter still answers 400 |
| `SecurityConfiguration` matchers | `AccessRule`s of `security.AuthenticationFilter` (MANAGER to import and relink documents) |
| `HttpArazzoMinionClient` (`RestClient`) | JDK `HttpClient` produced as `ArazzoMinionClient` (`ArazzoMinionClientProducer`), swapped with `QuarkusMock` in tests |
| `minions/arazzo` (standalone BOM, `io.github.microcks`) | parent `microcks-parent`, `io.microcks.minion.arazzo`, depends on `microcks-artifact-arazzo` and `microcks-artifact-openapi` |
| `commons/util` `JsonSchemaValidator` pointers, OpenAPI response validation | `artifacts/artifact-jsonschema` (`JsonSchemaViolation`), `artifacts/artifact-openapi` (`OpenAPIResponseValidator`, used by `OpenAPITestRunner` too) |
| `ServiceStateStore.put` read-then-save | `ServiceStateRepository.upsert` + unique `(serviceId, key)` index (race fix) |
| `kind: ArazzoSources` overlay (links + examples) | two kinds, like `APIMetadata` / `APIExamples`: `ArazzoMetadata` (links) and `ArazzoExamples` (`workflows.<id>.<example>`), schemas in `api/` |
| Tests `@SpringJUnitConfig`, Mockito on controllers | `@QuarkusTest` (database emptied by `MongoCleanupCallback`), RestAssured; the app's Arazzo ITs start the packaged minion |

New or different in 2.0:
- **Mixed runs (S31).** `target: {kind: "mixed", perSource: {<source>: "mock" | "implementation"}}` with
  `implementations` for the sources sent to an implementation; recorded as `MIXED`. The secret goes to
  implementations only.
- **Import.** The import analysis warns about a `dependsOn` between synchronous steps (Arazzo 1.1 §5.8.5.2) and imports
  the document anyway. `ArazzoExamples` are refused until their document is imported.
- **Request content type.** A request body without `contentType` takes the single request media type of its
  operation (the app sends `requestMediaTypes` per operation to the minion); with several or none the step fails
  without a call, the specification being silent.
- **Robustness.** An unexpected minion error ends the run as `FAILURE` ("Unexpected error in the Arazzo minion: …").
- **Sessions.** `GET /api/arazzo/sessions?documentId=&workflowId=` and `GET /api/arazzo/sessions/{id}` (live call
  count, `state`, `verificationRunId`) were added for the Sessions tab.
- **Other endpoints.** `GET /api/arazzo/documents/{id}/source` (the document as uploaded); a document is named by id
  or `<title>:<version>`.
- **API description.** The 1.x POC's `microcks-openapi-v1.16.yaml` became `api/microcks-openapi-v2.0.yaml`; the
  `v1.*` descriptions are untouched. It describes the whole 2.0 API (`/keycloak/config` became `/auth/config`, token
  at `/auth/token`, OIDC login at `/auth/login/oidc` and `/auth/callback/oidc`); `MicrocksApiDescriptionCoverageTest`
  fails when an endpoint under `/api` is missing from it, or described but gone.

Web UI:
- `lib/server/workflows.ts`, `runs-arazzo.ts` and `sessions-arazzo.ts` map `/api/arazzo` to the Deep Ink types;
  placeholder (demo) data is used only when the backend is unreachable.
- Features not served are disabled, not hidden, through `lib/workflows/availability.ts` (today: AI Copilot generation
  and document edit).
- BFF routes `/api/workflows/documents/{id}/metadata` and `/examples` give the ArazzoMetadata and ArazzoExamples files.
- Import analyses the document first (refusals and warnings before import); relink shows the effect of the chosen API;
  examples, retries, Mock URL, AsyncAPI steps, `dependsOn` branches, Live Traces attributes and sessions are wired.

Known gaps:
- AsyncAPI steps are read and shown, not runnable (launch refused).
- The minion plays steps in list order; `dependsOn` only relaxes the order checked by session verification.
- Live Traces does not filter by Arazzo run yet (attributes are shown on the run step).
- With authentication enabled, the minion reports without a token.
- The minion is not in `install/` (docker-compose, Kubernetes) yet.

| Story | Status |
|---|---|
| S1–S12: import, links, browse, steps and diagram, runs on mocks and implementations, missing inputs, failures, history, coherence, schema validation | ported |
| S14 import dialog and relink, S19 importers, S21 retries and early ends, run through the API, recorded examples (S16/S20) | ported |
| S27 Mock URL, S28 AsyncAPI steps (shown), S29 Live Traces, S30 `dependsOn` | ported |
| Session verification, S15, S16, S17 (saga compensation), race fix | ported |
| S31 mixed run (new in 2.0) | done |

## Known divergences and TODO

- Check `ServiceLoader` provider registration under Quarkus native mode (unverified).
- Validators not consolidated into the format modules.
- Security (M2-002): Keycloak is replaced by an internal JWT (resource-server, OIDC login at the backend, service
  accounts, WebSocket upgrade check) and the SvelteKit UI keeps it in an HttpOnly cookie; Groups/Membership (M2-003)
  are internal (`/api/groups`, resolved from the store per request, not from IDP claims; the SvelteKit Groups and Users tabs
  are wired to it); Angular frontend is not served by the Quarkus webapp yet;
  Dockerfiles and CI are not adapted.

## Build and validation

```
mvn -f pom.xml -pl app -am install      # from v2/
```

Last validated state: build success, 260 tests in the webapp plus the tests of each commons module (0 failures).

## Synchronization history

| Date | Upstream commit | Notes |
|---|---|---|
| 2026-10-16 | `608ddb53a824483c781c1ba277040a8971f43b63` | Initial baseline |
| 2026-11-02 | `1e8b6c60d786363d7c9c85f7985189802f350015` | Manual port into `v2/` (study) |
| 2026-11-27 | `f757641ed34c64e4d9885c6c1c89abd17580aeff` | 17 commits via `sync-upstream.sh`: 15 picked, 1 skipped (`webapp/pom.xml`), 2 conflicts fixed by hand (`OpenCollectionImporter` imports, `NativeConfiguration`) |

## Synchronization log

### 2026-11-02: `608ddb53..1e8b6c60` (22 upstream commits)

Ported (behavior and tests):

| Upstream | 2.0 |
|---|---|
| OpenCollection importer (#2353, #2401): `ResourceType.OPEN_COLLECTION`, `util/opencollection/*`, sample, fixtures, tests | New module `artifacts/artifact-opencollection` (`io.microcks.artifact.opencollection`) with `OpenCollectionImporterProvider` (priority 5). Test sample duplicated in `v2/samples/`; tests need `logback-classic` (test scope) to capture warnings |
| Importer factory: OpenCollection detected first on the raw line (BOM, column 0 root key) | New `MockRepositoryImporterProvider.accepts(line, rawLine)` (defaults to `accepts(line)`), used by the factory |
| `OperationPathPattern` (regex metacharacters and unresolved `{{var}}` kept literal in fallback matching, #2227, #2366) | `util` `io.microcks.util.OperationPathPattern`, used by `RestController.findOperationByPathPattern` |
| `ExpressionParser`: malformed curly braces raise `ParseException` (#2377), `hasArgs` bounds check | `commons/util-el`, same change, tests added |
| `DispatchCriteriaHelper.extractFromParamMap`: sorted keys and word-boundary match (#2386) | `util`, same change |
| `MetricsService`: conformance trend recorded on first call (#2389) | Same change; `MetricsServiceTest` uses plain reflection and Mockito instead of Spring `ReflectionTestUtils` |
| `TriggerWebhookTask`, `MockControllerCommons`: template evaluation failures logged (and traced) instead of thrown | Same change |
| `DocumentationController`: URL-encode resource name/id in the spec URL | `URLEncoder` (spaces as `%20`) instead of Spring `UriUtils`; `DocumentationControllerTest` uses plain instantiation |
| `redoc.html` rendered with Redoc v3 from `/assets/redoc/redoc.standalone.js` | Template copied as is. The asset is copied by the Angular build (`angular.json`), which is not part of `v2/` yet |

Not ported (out of scope for `v2/`): GitHub workflows, `MAINTAINERS.md`, `README.md`, `testsuite/`, `distro/uber` native
`reflect-config.json` (#2374, replaced by the Quarkus native configuration above), async minion MQTT persistence (#2375), `webapp/pom.xml` frontend-maven-plugin bump, Angular
frontend changes (Angular 21.2.27, `redoc` dependency, `service.model.ts` and its spec, lockfile bumps).
