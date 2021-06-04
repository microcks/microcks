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
{{- $capture := print "^(" .Values.appName ")(.*)" -}}
{{ regexReplaceAll $capture .Values.microcks.url "${1}-grpc${2}" }}
{{- end -}}


{{/*
Generate certificates for microcks GRPC service
*/}}
{{- define "microcks-grpc.gen-certs" -}}
{{- $capture := print "^(" .Values.appName ")(.*)" -}}
{{- $grpcUrl := regexReplaceAll $capture .Values.microcks.url "${1}-grpc${2}" -}}
{{- $grpcSvc := print .Values.appName "." .Release.Namespace ".svc.cluster.local" -}}
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
