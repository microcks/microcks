{{/*
Customized the name of this chart.
*/}}
{{- define "async.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}


{{/*
Print the image
*/}}
{{- define "async.image" -}}
{{- $image := printf "%s:%s" .repository .tag }}
{{- if .registry }}
{{- $image = printf "%s/%s" .registry $image }}
{{- end }}
{{- $image -}}
{{- end }}


{{/*
Default common labels
*/}}
{{- define "microcks-common-labels" -}}
{{- range $name, $value := .Values.commonLabels }}
{{ $name }}: {{ $value | quote }}
{{- end -}}
{{- end -}}

{{/*
Pod labels
*/}}
{{- define "async-pod-labels" -}}
{{- range $name, $value := .Values.features.async.pod.labels }}
{{ $name }}: {{ $value | quote }}
{{- end -}}
{{- end -}}

{{/*
Pod annotations
*/}}
{{- define "async-pod-annotations" -}}
{{- range $name, $value := .Values.features.async.pod.annotations }}
{{ $name }}: {{ $value | quote }}
{{- end -}}
{{- end -}}


{{/*
Service labels
*/}}
{{- define "async-service-labels" -}}
{{- range $name, $value := .Values.features.async.service.labels }}
{{ $name }}: {{ $value | quote }}
{{- end -}}
{{- end -}}

{{/*
Service annotations
*/}}
{{- define "async-service-annotations" -}}
{{- range $name, $value := .Values.features.async.service.annotations }}
{{ $name }}: {{ $value | quote }}
{{- end -}}
{{- end -}}
