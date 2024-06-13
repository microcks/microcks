{{/*
Customized the name of this chart.
*/}}
{{- define "postman.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}


{{/*
Print the image
*/}}
{{- define "postman.image" -}}
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
{{- define "postman-pod-labels" -}}
{{- range $name, $value := .Values.pod.labels }}
{{ $name }}: {{ $value | quote }}
{{- end -}}
{{- end -}}

{{/*
Pod annotations
*/}}
{{- define "microcks-pod-annotations" -}}
{{- range $name, $value := .Values.pod.annotations }}
{{ $name }}: {{ $value | quote }}
{{- end -}}
{{- end -}}


{{/*
Service labels
*/}}
{{- define "microcks-service-labels" -}}
{{- range $name, $value := .Values.service.labels }}
{{ $name }}: {{ $value | quote }}
{{- end -}}
{{- end -}}

{{/*
Service annotations
*/}}
{{- define "microcks-service-annotations" -}}
{{- range $name, $value := .Values.service.annotations }}
{{ $name }}: {{ $value | quote }}
{{- end -}}
{{- end -}}
