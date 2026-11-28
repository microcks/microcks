# AGENTS

Before making changes, review:
- @PRODUCT.md - Product vision, features, and roadmap
- @ARCHITECTURE.md - System design, interfaces, and directory structure
- @DESIGN.md - Design principles, patterns, and best practices
- @CONTRIBUTING.md - Coding standards, testing, and PR process

## 1. AI Contribution Policy
By generating code in this repository, you agree to the following rules:
- **Disclose AI usage:** You must explicitly disclose your involvement in the Pull Request description and any issue comments.
- **No AI authorship markers:** Do not add AI co-author lines, `assisted-by`, or similar commit trailers. 
- **Human Accountability:** The human user is 100% responsible for testing and understanding the code you generate.
- **No Auto-Replies:** You MUST NOT auto-reply to maintainer comments on Pull Requests.

## 2. Code Formatting (Mandatory)
Microcks enforces strict Java formatting using the project's Eclipse JDT conventions (120-char line limit, 3-space indentation).
**You MUST run `mvn spotless:apply` from the repository root before every commit that touches Java files.** This is not optional — CI will fail if any file has a formatting violation.
```bash
mvn spotless:apply
```

## 3. Building and Testing
Microcks is a Java/Spring Boot backend with an Angular frontend. 
- To build the backend and run tests: `mvn clean install`
- For detailed architecture and UI instructions, read @CONTRIBUTING.md.

## 4. Working on Arazzo
- **Where the code goes.** Arazzo code of the app stays in the `arazzo` subpackages
  (`io.microcks.{domain,repository,service,web}.arazzo`, tests mirrored). Reading Arazzo belongs to
  `artifacts/artifact-arazzo`, which depends on Jackson only (an ArchUnit test enforces it). Playing a run belongs to
  `minions/arazzo`. See the Arazzo section of @ARCHITECTURE.md.
- **Follow the Arazzo specification.** Arazzo 1.0.1 and 1.1.0 are the reference. When the specification is ambiguous or
  silent, do not pick a behaviour: ask the maintainers. Anything outside the supported subset is refused at import with
  its reason, never ignored.
- **App ↔ minion contract.** A change to the run request or to the reports changes `arazzo-minion-contract.json` and
  `ArazzoMinionContractTest` on both sides, in the same commit.
- **Artifacts.** `ArazzoMetadata` (source links) and `ArazzoExamples` (expected runs) are Microcks kinds detected by
  their `kind`, like `APIMetadata` and `APIExamples`; keep their JSON Schemas in `api/` in sync.
- **Web UI.** A feature whose backend is not there yet is shown disabled, not hidden, through
  `lib/workflows/availability.ts`; flip `PORTED[feature]` when it lands. Demo (placeholder) data keeps its behaviour.
- **Tests.** One test per observable behaviour. The minion's integration tests (`*IT`) need Docker (`-Pit`); the app's
  Arazzo integration tests start the packaged minion: run `mvn -pl minions/arazzo package -DskipTests` first.
