# Design

This document outlines the core design principles and patterns used in the Microcks codebase.

## Design Principles
- **API-First:** Microcks treats API specifications as the single source of truth for generating mocks and tests.
- **Kubernetes-Native:** Designed to run efficiently in containerized environments, with easy deployment via Helm or Operators.
- **Extensibility:** Built to support multiple protocols and artifact formats without altering the core engine.

## Coding Standards
- **Backend (Java):** Strict adherence to Eclipse JDT conventions (120-char line limit, 3-space indentation). Managed via Spotless.
- **Frontend (Angular):** Standard Angular style guide and component-based architecture.

## Best Practices
- **Modularity:** Keep components modular and single-purpose.
- **Test Coverage:** Ensure all new features are covered by comprehensive unit and integration tests.
- **Backward Compatibility:** Maintain backward compatibility for API specifications and mocking features wherever possible.
