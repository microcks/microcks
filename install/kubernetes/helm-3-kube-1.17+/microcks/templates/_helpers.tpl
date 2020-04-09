{{/*
Generate certificates for microcks ingress
*/}}
{{- define "microcks-ingress.gen-certs" -}}
{{- $cert := genSelfSignedCert .Values.microcks.url 365 -}}
tls.crt: {{ $cert.Cert | b64enc }}
tls.key: {{ $cert.Key | b64enc }}
{{- end -}}


{{/*
Generate certificates for keycloak ingress
*/}}
{{- define "keycloak-ingress.gen-certs" -}}
{{- $cert := genSelfSignedCert .Values.keycloak.url 365 -}}
tls.crt: {{ $cert.Cert | b64enc }}
tls.key: {{ $cert.Key | b64enc }}
{{- end -}}