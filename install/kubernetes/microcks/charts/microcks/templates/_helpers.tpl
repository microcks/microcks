{{/*
Print the image
*/}}
{{- define "pod.image" -}}
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
{{- define "microcks-pod-labels" -}}
{{- range $name, $value := .Values.features.async.pod.labels }}
{{ $name }}: {{ $value | quote }}
{{- end -}}
{{- end -}}

{{/*
Pod annotations
*/}}
{{- define "microcks-pod-annotations" -}}
{{- range $name, $value := .Values.features.async.pod.annotations }}
{{ $name }}: {{ $value | quote }}
{{- end -}}
{{- end -}}


{{/*
Service labels
*/}}
{{- define "microcks-service-labels" -}}
{{- range $name, $value := .Values.features.async.service.labels }}
{{ $name }}: {{ $value | quote }}
{{- end -}}
{{- end -}}

{{/*
Service annotations
*/}}
{{- define "microcks-service-annotations" -}}
{{- range $name, $value := .Values.features.async.service.annotations }}
{{ $name }}: {{ $value | quote }}
{{- end -}}
{{- end -}}
