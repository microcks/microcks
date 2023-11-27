{{/*
Generate certificates for microcks ingress
*/}}
{{- define "microcks-ingress.gen-certs" -}}
{{- $cert := genSelfSignedCert .Values.microcks.url nil nil 365 -}}
tls.crt: {{ $cert.Cert | b64enc }}
tls.key: {{ $cert.Key | b64enc }}
{{- end -}}


{{/*
Produce GRPC Ingress URL
*/}}
{{- define "microcks-grpc.url" -}}
"{{ regexReplaceAll "^([^.-]+)(.*)" .Values.microcks.url "${1}-grpc${2}" }}"
{{- end -}}


{{/*
Generate certificates for microcks GRPC service
*/}}
{{- define "microcks-grpc.gen-certs" -}}
{{- $grpcUrl := regexReplaceAll "^([^.-]+)(.*)" .Values.microcks.url "${1}-grpc${2}" -}}
{{- $grpcSvc := print .Values.appName "-grpc." .Release.Namespace ".svc.cluster.local" -}}
{{- $cert := genSelfSignedCert .Values.microcks.url nil (list $grpcUrl $grpcSvc "localhost") 3650 -}}
tls.crt: {{ $cert.Cert | b64enc }}
tls.key: {{ $cert.Key | b64enc }}
{{- end -}}


{{/*
Generate certificates for keycloak ingress
*/}}
{{- define "keycloak-ingress.gen-certs" -}}
{{- $cert := genSelfSignedCert .Values.keycloak.url nil nil 365 -}}
tls.crt: {{ $cert.Cert | b64enc }}
tls.key: {{ $cert.Key | b64enc }}
{{- end -}}


{{/*
Produce WS Ingress URL
*/}}
{{- define "microcks-ws.url" -}}
{{ regexReplaceAll "^([^.-]+)(.*)" .Values.microcks.url "${1}-ws${2}" }}
{{- end -}}

{{/*
Generate certificates for microcks WS ingress
*/}}
{{- define "microcks-ws-ingress.gen-certs" -}}
{{- $wsUrl := regexReplaceAll "^([^.-]+)(.*)" .Values.microcks.url "${1}-ws${2}" -}}
{{- $cert := genSelfSignedCert $wsUrl nil (list $wsUrl "localhost") 3650 -}}
tls.crt: {{ $cert.Cert | b64enc }}
tls.key: {{ $cert.Key | b64enc }}
{{- end -}}

{{/*
Generate common labels
*/}}
{{- define "microcks-common-labels" -}}
{{- range $name, $value := .Values.commonLabels }}
{{ $name }}: {{ $value | quote }}
{{- end -}}
{{- end -}}

{{/*
Generate common annotations
*/}}
{{- define "microcks-common-annotations" -}}
{{- range $name, $value := .Values.commonAnnotations }}
{{ $name }}: {{ $value | quote }}
{{- end -}}
{{- end -}}

