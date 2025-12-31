# OpenCollection Tests

This folder contains unit tests for the OpenCollection importer functionality.

## Test Coverage

The tests ensure that the OpenCollection importer:
- ✅ Correctly parses valid OpenCollection v1.x files
- ✅ Extracts 11 operations from the sample collection
- ✅ Creates proper request/response pairs with dispatch criteria
- ✅ Handles multiple response examples per operation
- ✅ Validates version requirements
- ✅ Provides meaningful error messages for invalid inputs
- ✅ Supports operation name equivalence across different formats

## Test Overview

### OpenCollectionImporterTest
Tests the core importer functionality that parses OpenCollection JSON files and converts them into Microcks domain objects.

**What it verifies:**
- **Service Import**: Validates that service metadata (name, version, type) is correctly extracted from OpenCollection files
- **Resource Storage**: Confirms that the original collection is stored as a `OPEN_COLLECTION` resource type
- **Operation Extraction**: Ensures all HTTP operations (GET, POST, PATCH, DELETE) are properly identified and created
- **Request/Response Pairs**: Verifies that request and response examples are correctly paired and imported
- **Multiple Examples**: Tests that multiple response examples per operation are handled (e.g., 4 POST examples for creating different pastries)
- **HTTP Status Codes**: Confirms correct extraction of status codes (200, 201, 204, 404)
- **Error Responses**: Validates that error responses (404 Not Found) are properly imported alongside success cases
- **Content Validation**: Checks that response bodies contain expected data (pastry names, countries, etc.)
- **Version Validation**: Tests that invalid OpenCollection versions are rejected with appropriate error messages
- **Error Handling**: Verifies proper exception handling for malformed collections

### OpenCollectionUtilTest
Tests utility functions for OpenCollection operations.

**What it verifies:**
- **Operation Name Matching**: Tests case-insensitive comparison of operation names
- **Path Parameter Conversion**: Validates conversion between OpenAPI `{param}` and OpenCollection `:param` syntax
- **HTTP Verb Handling**: Ensures operations match with or without HTTP verb prefixes
- **Version Validation**: Tests that only OpenCollection v1.x versions are accepted

## Test Fixtures

Test data files located in `src/test/resources/io/github/microcks/util/opencollection/`:

- **sample-rest-api.json**: Main test fixture with a complete pastry API including:
  - GET operations (list all, get by name)
  - POST operations (create Belgian Waffle, Churro, Gateau piments, Napolitaine)
  - PATCH operation (update pastry)
  - DELETE operation (delete pastry)
  - Success and error response examples
  - Nested folder structure

- **invalid-version.json**: Tests rejection of unsupported versions (v2.0.0)
- **missing-version.json**: Tests error handling for missing version field




