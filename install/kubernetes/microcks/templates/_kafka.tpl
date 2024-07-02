{{/*
Detect and return the kafka's url
*/}}
{{- define "microcks.kafka.url" -}}
{{- $url := -}}
{{- if .Values.kafka.install -}}
{{-   $url := "kafka"  -}}
{{- else -}}
{{-   $url := .Values.externalDependencies.kafka.url -}}
{{- end -}}

{{/*
Detect and return the kafka's Port
*/}}
{{- define "microcks.kafka.port" -}}
{{- if and ( eq .Values.kafka.install true) ( eq .Values.kafka.externalAccess.enabled true) -}}
{{- default "9094" .Values.kafka.externalAccess.controller.service.ports.external -}}
{{- else if and ( eq .Values.kafka.install true) ( eq .Values.kafka.externalAccess.enabled false) -}}
{{- default "9092" .Values.kafka.listeners.controller.containerPort -}}
{{- else if (eq .Values.kafka.install false) -}}
{{- .Values.externalDependencies.kafka.port -}}
{{- end -}}

{{/*
Detect and return the kafka's protocol
*/}}
{{- define "microcks.kafka.protocol" -}}
{{- if and ( eq .Values.kafka.install true) ( eq .Values.kafka.externalAccess.enabled true) -}}
{{- default "PLAINTEXT" .Values.kafka.listeners.external.protocol -}}
{{- else if and ( eq .Values.kafka.install true) ( eq .Values.kafka.externalAccess.enabled false) -}}
{{- default "PLAINTEXT" .Values.kafka.listeners.controller.protocol -}}
{{- else if (eq .Values.kafka.install false) -}}
{{- .Values.externalDependencies.kafka.protocol -}}
{{- end -}}

{{/*
Detect and return the schema registry's url
*/}}
{{- define "microcks.schemaRegistry.url" -}}
{{- $url := -}}
{{- if .Values.schema-registry.install -}}
{{-   $url := "schema-registry" -}}
{{- else if haskey .Values.externalDependencies.schema-registry.url -}}
{{-   $url := ".Values.externalDependencies.schema-registry.url" -}}
{{- end -}}

{{/*
Detect and return the schema registry's port
*/}}
{{- define "microcks.schemaRegistry.port" -}}
{{- $port := -}}
{{- if .Values.schema-registry.install -}}
{{-   $url := "8081" -}}
{{- else if hasKey .Values.externalDependencies.schema-registry.port -}}
{{-   $port := ".Values.externalDependencies.schema-registry.port" -}}
{{- end -}}