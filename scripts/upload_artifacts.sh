#!/bin/bash

set -e  # Exit on error

BASE_URL="${BASE_URL:?Missing BASE_URL}"
MICROCKS_TOKEN="${MICROCKS_TOKEN:?Missing MICROCKS_TOKEN}"

upload_file() {
  local file_path="$1"
  local main_artifact="$2"

  echo "Uploading: $file_path (mainArtifact=$main_artifact)"

  curl -X POST "${BASE_URL}/api/artifact/upload?mainArtifact=${main_artifact}" \
    -H "Authorization: Bearer ${MICROCKS_TOKEN}" \
    -F "file=@${file_path}" -k
}

# Upload artifacts for Graphql API
upload_file "/samples/films.graphql" true
upload_file "/samples/films-postman.json" false
upload_file "/samples/films-metadata.yml" false
# Upload artifacts for REST API
upload_file "/samples/APIPastry-openapi.yaml" true

echo "All files uploaded successfully!"
