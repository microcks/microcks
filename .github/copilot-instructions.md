# Copilot Instructions

## Code Formatting

Before committing any Java code changes, run `mvn spotless:apply` from the repository root to auto-format all files according to the project's Eclipse JDT conventions (120-char line limit, 3-space indentation):

```bash
mvn spotless:apply
```

If `mvn spotless:check` fails during the build, the violation diff shown in the error output indicates exactly which lines need reformatting. Run `spotless:apply` to fix them automatically.
