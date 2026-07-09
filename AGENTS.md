# Instructions for AI Agents

Welcome! If you are an AI coding assistant (like Cursor, GitHub Copilot, or Claude Code) operating in the `microcks/microcks` repository, you must adhere to the following rules to ensure high-quality contributions.

## 1. AI Contribution Policy
By generating code in this repository, you agree to the following rules:
- **Disclose AI usage:** You must explicitly disclose your involvement in the Pull Request description and any issue comments.
- **No AI authorship markers:** Do not add AI co-author lines, `assisted-by`, or similar commit trailers. 
- **Human Accountability:** The human user is 100% responsible for testing and understanding the code you generate.
- **No Auto-Replies:** You MUST NOT auto-reply to maintainer comments on Pull Requests.

## 2. Code Formatting (Mandatory)
Microcks enforces strict Java formatting using the Eclipse JDT conventions.
You can verify formatting by running `mvn spotless:check`.
**Before any commit**, you must run:
```bash
mvn spotless:apply
```

## 3. Building and Testing
Microcks is a Java/Spring Boot backend with an Angular frontend. 
- To build the backend and run tests: `mvn clean install`
- For detailed architecture and UI instructions, read [CONTRIBUTING.md](CONTRIBUTING.md).
