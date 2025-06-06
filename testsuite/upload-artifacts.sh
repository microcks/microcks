#!/bin/bash

set -euo pipefail

: "${BASE_URL:?Missing BASE_URL}"
: "${MICROCKS_TOKEN:?Missing MICROCKS_TOKEN}"

upload_file() {
  local file_path="$1"
  local main_artifact="${2:-false}"

  if [[ ! -f "$file_path" ]]; then
    echo "File not found: $file_path"
    return 1
  fi

  echo "Uploading: $file_path (mainArtifact=$main_artifact)"

  status_code=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "${BASE_URL}/api/artifact/upload?mainArtifact=${main_artifact}" \
    -H "Authorization: Bearer ${MICROCKS_TOKEN}" \
    -F "file=@${file_path}" -k)

  if [[ "$status_code" -ne 201 ]]; then
    echo "Upload failed for $file_path — HTTP status code: $status_code"
    return 1
  fi

  echo "Upload succeeded for $file_path"
}

# Hello REST API Soapui
upload_file "/samples/HelloAPI-soapui-project.xml" true

# Hello Service gRPC
upload_file "/samples/hello-v1.proto" true
upload_file "/samples/HelloService.postman.json" false
upload_file "/samples/HelloService.metadata.yml" false

# HelloService Soapui API
upload_file "/samples/HelloService-soapui-project.xml" true

# Petstore API
upload_file "/samples/PetstoreAPI-collection.json" true

# User SignedUp API
upload_file "/samples/UserSignedUpAPI-asyncapi.yml" true

# GraphQL API
upload_file "/samples/films.graphql" true
upload_file "/samples/films-postman.json" false
upload_file "/samples/films-metadata.yml" false

# REST API
upload_file "/samples/APIPastry-openapi.yaml" true

echo "All files uploaded successfully!"
