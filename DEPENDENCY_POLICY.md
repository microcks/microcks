# Microcks External Dependency Policy

## External dependencies dashboard

The list of external dependencies in this Microcks repository with their current version is available at
[network/dependencies](../../network/dependencies)

## Declaring external dependencies

Depending on the Microcks Core or Module/extension technology stack, all external dependencies should be declared
in either [pom.xml](pom.xml), [package.json](package.json) or [go.mod](go.mod) root files.

Dependency declarations must:

* Provide a meaningful project name and URL.
* State the version in the `version` field. String interpolation can be used to aggregate all versions declaration at the same place.
* Versions should prefer release versions over main branch GitHub SHA tarballs. A comment is necessary if the latter is used. 
  This comment should contain the reason that a non-release version is being used.

## New external dependencies

The criteria below are used to evaluate new dependencies. They apply to all core dependencies and any extension
that is robust to untrusted downstream or upstream traffic. The criteria are guidelines, exceptions may be granted 
with solid rationale. Precedent from existing extensions does not apply; there are extant extensions in violation 
of this policy which we will be addressing over time, they do not provide grounds to ignore policy criteria below.

|Criteria|Requirement|Mnemonic|Weight|Rationale|
|--------|-----------|--------|------|---------|
|Cloud Native Computing Foundation (CNCF) [approved license](https://github.com/cncf/foundation/blob/master/allowed-third-party-license-policy.md#approved-licenses-for-allowlist)|MUST|License|High||
|Dependencies must not substantially increase the binary size unless they are optional (i.e. confined to specific extensions)|MUST|BinarySize|High|Microcks Uber is sensitive to binary size. We should pick dependencies that are used in core with this criteria in mind.|
|No duplication of existing dependencies|MUST|NoDuplication|High|Avoid maintenance cost of multiple utility libs with same goals (ex: JSON parsers)|
|CVE history appears reasonable, no pathological CVE arcs|MUST|SoundCVEs|High|Avoid dependencies that are CVE heavy in the same area (e.g. buffer overflow)
|Security vulnerability process exists, with contact details and reporting/disclosure process|MUST|SecPolicy|High|Lack of a policy implies security bugs are open zero days|
|Code review (ideally PRs) before merge|MUST|Code-Review|Normal|Consistent code reviews|
|> 1 contributor responsible for a non-trivial number of commits|MUST|Contributors|Normal|Avoid bus factor of 1|
|Tests run in CI|MUST|CI-Tests|Normal|Changes gated on tests|
|Hosted on a git repository and the archive fetch must directly reference this repository. We will NOT support intermediate artifacts built by-hand located on GCS, S3, etc.|MUST|Source|Normal|Flows based on manual updates are fragile (they are not tested until needed), often suffer from missing documentation and shared exercise, may fail during emergency zero day updates and have no audit trail (i.e. it's unclear how the artifact we depend upon came to be at a later date).|
|High test coverage (also static/dynamic analysis, fuzzing)|SHOULD|Test-Coverage|Normal|Key dependencies must meet the same quality bar as Envoy|
|Do other significant projects have shared fate by using this dependency?|SHOULD|SharedFate|High|Increased likelihood of security community interest, many eyes.|
|Releases (with release notes)|SHOULD|Releases|Normal|Discrete upgrade points, clear understanding of security implications. We have many counterexamples today (e.g. CEL, re2).|
|Commits/releases in last 90 days|SHOULD|Active|Normal|Avoid unmaintained deps, not compulsory since some code bases are “done”|


## Maintaining existing dependencies

We rely on community volunteers to help track the latest versions of dependencies. On a best effort
basis:

* Core Microcks dependencies will be updated by the Microcks maintainers/security team.

* Module/extension [CODEOWNERS](./CODEOWNERS) should update extension specific dependencies.

Where possible, we prefer the latest release version for external dependencies, rather than main branch GitHub SHA tarballs.

If you intend to update a dependency, please assign the relevant ticket to yourself and/or associate any Pull Request (eg by adding `chore: #1234`) with the issue.


## Policy exceptions

The following dependencies are exempt from the policy:

* Any developer-only facing tooling or the documentation build.

* Transitive build time dependencies.