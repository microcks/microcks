# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

The primary users are API developers building or integrating API providers and consumers. They use Microcks while
designing, implementing, and validating APIs so that dependent teams can work in parallel before every implementation is
available.

Platform engineers and API architects are supporting audiences. They operate shared API lifecycle infrastructure,
establish contract-testing practices, and govern API quality across teams.

## Product Purpose

Microcks turns API and microservice contracts into live mocks in seconds and reuses the same contracts for conformance
and non-regression testing. It exists to shorten feedback loops, enable parallel development, and make API behavior
testable throughout delivery rather than only after integration.

Success means teams can move from an existing contract to a usable mock or meaningful compatibility test with minimal
custom code, across the protocols they already use.

## Positioning

Microcks unifies mocking and contract testing around the API artifact itself. OpenAPI, AsyncAPI, GraphQL schemas, gRPC
protobufs, Postman collections, and SoapUI projects remain the source material for both simulated behavior and
implementation verification, instead of requiring separate mock definitions and test suites.

## Operating Context

- Developers import or synchronize API artifacts, inspect discovered services and operations, manage examples, and run
	tests from the web application.
- Teams integrate contract tests into delivery workflows through the Microcks CLI and integrations such as Jenkins,
	GitHub Actions, and Tekton.
- Operators deploy Microcks in containerized and Kubernetes environments, with MongoDB for persistence and optional
	Keycloak-based authentication and authorization.
- API workflows may be request-response or event-driven and may span REST, GraphQL, gRPC, SOAP, and AsyncAPI/Kafka.

## Capabilities and Constraints

- Generate live mocks from existing API specifications without requiring implementation code.
- Run contract conformance and non-regression tests against API implementations.
- Preserve broad protocol support; core workflows must not assume that every API is REST-based.
- Remain Kubernetes-native and extensible through integrations and specialized minions.
- Maintain backward compatibility for established API specifications and mocking behavior wherever possible.
- Continue expanding AsyncAPI protocol support, DataContracts validation, CI/CD integration, and developer experience.

## Brand Commitments

Microcks is an open-source, community-governed project focused on API mocking and testing. Product language should be
technical, direct, and credible. Preserve the Microcks name, its API-first and cloud-native identity, and its established
project assets; do not manufacture customer, adoption, performance, or compatibility claims.

## Evidence on Hand

- The root README documents supported artifact formats, mocking and testing capabilities, CI/CD integrations, release
	status, project health, security scans, supply-chain documentation, and community links.
- `ADOPTERS.md` and the public project governance files are the sources for real adoption and community evidence.
- `samples/`, `api/`, and `specs/` contain concrete contracts and examples that can demonstrate supported workflows.
- No testimonial, benchmark, or customer claim should be introduced unless it is backed by a repository source or other
	user-provided evidence.

## Product Principles

1. Treat API contracts as the source of truth for mocks and tests.
2. Shorten feedback loops so API producers and consumers can develop in parallel.
3. Give request-response and event-driven protocols first-class workflows.
4. Fit cloud-native delivery practices from local development through CI/CD and Kubernetes operation.
5. Preserve trust through backward compatibility, verifiable claims, and open community governance.
