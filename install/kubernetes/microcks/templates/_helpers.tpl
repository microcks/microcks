{{/*
Generate certificates for microcks ingress
*/}}
{{- define "microcks-ingress.gen-certs" -}}
{{- $cert := genSelfSignedCert .Values.microcks.url nil (list .Values.microcks.url) 365 -}}
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
{{- define "microcks.keycloak-ingress.gen-certs" -}}
{{- $cert := genSelfSignedCert .Values.keycloak.url nil (list .Values.keycloak.url) 365 -}}
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

{{/*
Create image name value
( dict "imageRoot" .Values.path.to.image "context" $ )
*/}}
{{- define "microcks.renderImage" -}}
{{- $ref := join "/" (compact (list .imageRoot.registry .imageRoot.repository)) -}}
{{- join "" (compact (list $ref (ternary ":" "@" (empty .imageRoot.digest)) (default .imageRoot.tag .imageRoot.digest))) -}}
{{- end -}}

{{/*
Compute microcks image full name
*/}}
{{- define "microcks.image" -}}
{{- include "microcks.renderImage" ( dict "imageRoot" .Values.microcks.image "context" $ ) -}}
{{- end -}}

{{/*
Compute postman image full name
*/}}
{{- define "microcks.postman.image" -}}
{{- include "microcks.renderImage" ( dict "imageRoot" .Values.postman.image "context" $ ) -}}
{{- end -}}

{{/*
Compute mongodb image full name
*/}}
{{- define "microcks.mongodb.image" -}}
{{- include "microcks.renderImage" ( dict "imageRoot" .Values.mongodb.image "context" $ ) -}}
{{- end -}}

{{/*
Compute keycloak image full name
*/}}
{{- define "microcks.keycloak.image" -}}
{{- include "microcks.renderImage" ( dict "imageRoot" .Values.keycloak.image "context" $ ) -}}
{{- end -}}

{{/*
Compute keycloak postgres image full name
*/}}
{{- define "microcks.keycloakPostgres.image" -}}
{{- include "microcks.renderImage" ( dict "imageRoot" .Values.keycloak.postgresImage "context" $ ) -}}
{{- end -}}

{{/*
Compute async-minion image full name
*/}}
{{- define "microcks.async-minion.image" -}}
{{- include "microcks.renderImage" ( dict "imageRoot" .Values.features.async.image "context" $ ) -}}
{{- end -}}
