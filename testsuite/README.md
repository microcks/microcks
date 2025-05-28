# Function Testsuite for Microcks instance

This collection of scripts and test cases is designed to validate the installation methods and the functionalities of a Microcks instance.
It is designed so that same scripts and tests can/should be applied to different methods and to validate different Microcks distribution and flavors.

The tests are organized in two main categories:
* **Installation Tests**: Validate the installation methods and the Microcks instance itself. This is what we called the "health checks" below,
* **Functional Tests**: Validate the functionalities of Microcks instance by running a set of tests that cover the following aspects:
  * Microcks own APIs,
  * Mocking & Testing REST APIs,
  * Mocking & Testing GraphQL APIs,
  * Mocking & Testing gRPC APIs,
  * Mocking & Testing SOAP APIs,
  * Mocking & Testing AsyncAPI APIs.

The Testsuite is intended to be run on the current development branch of Microcks to validate the latest changes of the installation scripts 
and the features embedded into the `nighlty` tagged container images. 

## Health Checks

Health checks consist in installing a Microcks instance using the provided Docker or Podman compose scripts as well as the Helm chart
provided in a Microcks release.

The installations can be driven via the `install-compose.sh` or `install-helm.sh` scripts that provides wrappers around the Docker Compose and Helm commands.

Example:
```shell
./install-compose.sh --method docker --mode default --addons async
```

The validation is actually done using the `health-check.sh` script that will check the readiness probes of the different containers started
by the different installation methods.

Example:
```shell
./check-health.sh --method docker
```


All those checks are intended to be scheduled and embedded into a [GitHub Action matrix job](https://docs.github.com/en/actions/writing-workflows/choosing-what-your-workflow-does/running-variations-of-jobs-in-a-workflow).

## Functional Tests

The goal is to validate the functionalities of Microcks instances using different distribution and flavours.

The different configuration/combinations to consider are:
* Microcks Regular distribution with authentication enabled (`quay.io/microcks/microcks:nightly`),
* Microcks Regular distribution without authentication (aka `devmode`),
* Microcks Uber distribution using the JVM based image (`quay.io/microcks/microcks-uber:nightly`),
* Microcks Uber distribution using the native image (`quay.io/microcks/microcks-uber:nightly-native`),

Based on our experience, the following APIs and the features listed below are the most used and the most important to validate
for each different combination (unchecked boxes are not yet implemented).

### Regular distribution with authentication

#### Microcks own APIs

* [ ] Getting the configuration (`GET /api/features/config`) should return 200 when not authenticated,
* [ ] Getting the Keycloak configuration (`GET /api/keycloak/config`) should return 200 when not authenticated,
* [ ] Getting the list of services (`GET /api/services`) should return 403 when not authenticated,
* [ ] Authentication (with default _Service Account_) should return an `AccessToken` when successful,
* [ ] Getting the list of services (`GET /api/services`) should return 200 when authenticated (using `AcessToken` as a `Bearer` token),
* [ ] Getting the list of jobs (`GET /api/jobs`) should return 403 when not authenticated or authenticated as user,
* [ ] Getting the list of jobs (`GET /api/jobs`) should return 200 when authenticated as manager or admin,

#### Mocking APIs

* [ ] REST Mocks for "Pastry API 2.0 - 2.0.0" should return 200,
* [ ] GraphQL Mocks for "Movie Graph API - 1.0" should return 200,
* [ ] SOAP Mocks for "HelloService Mock - 0.9" should return 200,
* [ ] gRPC Mocks for "org.acme.petstore.v1.PetstoreService - v1" should return OK,

### Regular distribution without authentication (devmode)

#### Microcks own APIs

* [ ] Getting the configuration (`GET /api/features/config`) should return 200 when not authenticated,
* [ ] Getting the Keycloak configuration (`GET /api/keycloak/config`) should return 200 when not authenticated,
* [ ] Getting the list of services (`GET /api/services`) should return 200 when not authenticated,
* [ ] Getting the list of jobs (`GET /api/jobs`) should return 200 when not authenticated,

#### Mocking APIs

Embed the same tests as the _Regular distribution with authentication_.

### Uber distribution using the JVM based image

Embed the same tests as the _Regular distribution without authentication (devmode)_ for both the Microcks own APIs and the Mocking APIs.

### Uber distribution using the native image

Embed the same tests as the _Uber distribution using the JVM based image_ + these one in addition:

* [ ] REST Mocks using `JSON_BODY`, `FALLBACK` and `PROXY_FALLBACK` dispatcher should return 200,

The following tests must be excluded:

* [ ] SOAP Mocks for "HelloService Mock - 0.9" (as the native image is not able to execute Groovy `SCRIPT` dispatcher),