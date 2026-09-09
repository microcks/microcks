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
