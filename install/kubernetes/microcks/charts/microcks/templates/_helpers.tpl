{{/*
Print the image
*/}}
{{- define "microcks.image" -}}
{{- $image := printf "%s:%s" .repository .tag }}
{{- if .registry }}
{{- $image = printf "%s/%s" .registry $image }}
{{- end }}
{{- $image -}}
{{- end }}

{{/*
Pod labels
*/}}
{{- define "microcks-pod-labels" -}}
{{- range $name, $value := .Values.microcks.labels }}
{{ $name }}: {{ $value | quote }}
{{- end -}}
{{- end -}}

{{/*
Pod annotations
*/}}
{{- define "microcks-pod-annotations" -}}
{{- range $name, $value := .Values.microcks.annotations }}
{{ $name }}: {{ $value | quote }}
{{- end -}}
{{- end -}}


{{/*
Service labels
*/}}
{{- define "microcks-service-labels" -}}
{{- range $name, $value := .Values.microcks.service.labels }}
{{ $name }}: {{ $value | quote }}
{{- end -}}
{{- end -}}

{{/*
Service annotations
*/}}
{{- define "microcks-service-annotations" -}}
{{- range $name, $value := .Values.microcks.service.annotations }}
{{ $name }}: {{ $value | quote }}
{{- end -}}
{{- end -}}
