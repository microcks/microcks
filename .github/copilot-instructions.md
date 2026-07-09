# Copilot Instructions

## Code Formatting — MANDATORY

**You MUST run `mvn spotless:apply` from the repository root before every commit that touches Java files.** This is not optional — CI will fail if any file has a formatting violation.

```bash
mvn spotless:apply
```

This auto-formats all Java files according to the project's Eclipse JDT conventions (120-char line limit, 3-space indentation).

If `mvn spotless:check` fails during the build, the violation diff shown in the error output indicates exactly which lines need reformatting. Run `spotless:apply` to fix them automatically.

> **Reminder:** Every single Java commit — no exceptions — must have `mvn spotless:apply` applied before it is pushed.

## AI Contribution Policy
Please read the `@AGENTS.md` file in the root of the repository for full instructions regarding AI contributions. Ensure that your human operator discloses your assistance in any Pull Request you help create.
