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
Pod labels
*/}}
{{- define "async-pod-labels" -}}
{{- range $name, $value := .Values.async.labels }}
{{ $name }}: {{ $value | quote }}
{{- end -}}
{{- end -}}

{{/*
Pod annotations
*/}}
{{- define "async-pod-annotations" -}}
{{- range $name, $value := .Values.async.annotations }}
{{ $name }}: {{ $value | quote }}
{{- end -}}
{{- end -}}


{{/*
Service labels
*/}}
{{- define "async-service-labels" -}}
{{- range $name, $value := .Values.async.service.labels }}
{{ $name }}: {{ $value | quote }}
{{- end -}}
{{- end -}}

{{/*
Service annotations
*/}}
{{- define "async-service-annotations" -}}
{{- range $name, $value := .Values.async.service.annotations }}
{{ $name }}: {{ $value | quote }}
{{- end -}}
{{- end -}}

{{/*
configmap labels
*/}}
{{- define "async-configmap-labels" -}}
{{- range $name, $value := .Values.async.configmap.labels }}
{{ $name }}: {{ $value | quote }}
{{- end -}}
{{- end -}}

{{/*
configmap annotations
*/}}
{{- define "async-configmap-annotations" -}}
{{- range $name, $value := .Values.async.configmap.annotations }}
{{ $name }}: {{ $value | quote }}
{{- end -}}
{{- end -}}

{{/*
configmap annotations
*/}}
{{- define "async-cert-annotations" -}}
{{- range $name, $value := .Values.async.cert.annotations }}
{{ $name }}: {{ $value | quote }}
{{- end -}}
{{- end -}}
