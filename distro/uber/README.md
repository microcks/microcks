This is an all-in-one distribution of Microcks 2.0 that removes the need for Keycloak, Kafka and an external MongoDB
server. It is a Quarkus application built on top of `microcks-app`.

For that we're using an embedded MongoDB-protocol compatible Java server (`mongo-java-server`). It is started before
the Quarkus MongoDB client connects: `EmbeddedMongoConfigSourceFactory` resolves `microcks.uber.mongodb-url`, which
starts the server on a random local port and is referenced by `quarkus.mongodb.connection-string`. The server runs in
two different modes:
* Ephemeral in-memory persistence that will not survive a restart (that is the default mode),
* File persistence (H2 MVStore) that will survive restarts.

The events normally sent to Kafka are pushed to the Uber async minion through WebSocket endpoints
(`/api/services-updates` and `/api/asyncapi-triggers`, same paths and JSON messages as Microcks 1.x), implemented by
`WebSocketEventPublicationChannel`. Hazelcast (the cluster event bus of the full mode) is neither packaged nor started: a single instance delivers events directly to the connected minion.
There is no authentication on these endpoints.

## In-memory mode

Build and start the server:

```shell
$ cd v2
$ mvn install -pl distro/uber -am -DskipTests
$ java -jar distro/uber/target/quarkus-app/quarkus-run.jar
====== OUTPUT ======
INFO: Creating a new embedded Mongo Java Server with in-memory persistence
[...]
INFO  [io.quarkus] microcks-uber-app 2.0.0-SNAPSHOT on JVM (powered by Quarkus 3.40.1.2) started in 2.1s. Listening on: http://0.0.0.0:8080
```

You can then import an artifact and call its mock:

```shell
$ curl -F file=@samples/APIPastry-openapi.yaml "localhost:8080/api/artifact/upload?mainArtifact=true"
$ curl "localhost:8080/rest/API+Pastry+-+2.0/2.0.0/pastry/Millefeuille"
```

## Persistent mode

Persistent file location is controlled via the `mongodb.storage.file` configuration property, but you can just set its
value using the `MONGODB_STORAGE_PATH` environment variable:

```shell
$ MONGODB_STORAGE_PATH=target/microcks.mv java -jar distro/uber/target/quarkus-app/quarkus-run.jar
====== OUTPUT ======
INFO: Creating a new embedded Mongo Java Server with disk persistence at target/microcks.mv
INFO: opening MVStore in 'target/microcks.mv'
[...]
```

## Native executable

The native executable is built in a container (Mandrel builder image, no local GraalVM needed, around 10 GB of memory):

```shell
$ cd v2
$ mvn install -pl distro/uber -am -DskipTests
$ mvn package -pl distro/uber -DskipTests -Dnative -Dquarkus.native.container-build=true
$ MONGODB_STORAGE_PATH=target/microcks.mv distro/uber/target/microcks-uber-app-2.0.0-SNAPSHOT-runner
```

Import and mocks work the same way, in memory or with file persistence. The gRPC mocks are served on the main HTTP port (Vert.x gRPC) and work in native too.
`GrpcServiceLoaderSupport` registers the Netty client channel provider explicitly (used by the gRPC test runner),
because gRPC's default registries are empty in the native image. The native configuration lives in `application.properties` (build
arguments, resources) and `NativeConfiguration` (reflection and serialization hints).

## Container images

`src/main/docker` contains `Dockerfile.jvm` (needs `target/quarkus-app`) and `Dockerfile.native` (needs the native
runner), with the build commands in their header. Mount a volume and set `MONGODB_STORAGE_PATH` to keep data between
container restarts.
