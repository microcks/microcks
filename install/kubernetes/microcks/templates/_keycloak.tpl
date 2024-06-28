{{/*
Detect and return the Keycloak's url
*/}}
{{- define "microcks.keycloak.url" -}}
{{- if .Values.keycloak.install -}}
{{-   $url := "keycloak"  -}}
{{- else -}}
{{-   $url := .Values.externalDependencies.keycloak.url -}}
{{- end -}}
{{- end -}}

{{/*
Detect and return the Keycloak's Port
*/}}
{{- define "microcks.keycloak.port" -}}
{{- if .Values.mongodb.install -}}
{{-   $port := "80" -}}
{{- else -}}
{{-   $port := .Values.externalDependencies.keycloak.port -}}
{{- end -}}
{{- end -}}

{{/*
Detect and return the Keycloak's realm
*/}}
{{- define "microcks.keycloak.realm" -}}
{{- if and ( eq .Values.keycloak.install false ) ( .Values.externalDependencies.keycloak.realm.url ) -}}
{{-   $realm := .Values.externalDependencies.keycloak.realm -}}
{{- end -}}
{{- end -}}

{{/*
Detect and return the Keycloak's username in plaintext
*/}}
{{- define "microcks.keycloak.username" -}}
{{- if and ( eq .Values.keycloak.install true ) ( .Values.keycloak.auth.adminUser ) -}}
{{-   $username := .Values.keycloak.auth.adminUser -}}
{{- else -}}
{{-   $username := .Values.externalDependencies.keycloak.auth.adminUsername -}}
{{- end -}}
{{- end -}}

{{/*
Detect and return the Keycloak's password in plaintext
*/}}
{{- define "microcks.keycloak.password" -}}
{{- if and ( eq .Values.keycloak.install true ) ( .Values.keycloak.auth.adminPassword ) -}}
{{-   $password := .Values.keycloak.auth.adminPassword -}}
{{- else -}}
{{-   $password := .Values.externalDependencies.keycloak.auth.adminPassword -}}
{{- end -}}
{{- $password -}}
{{- end -}}

{{/*
Detect and return the Keycloak's authenticates in secret
*/}}
{{- define "microcks.keycloak.secretRef.secret" -}}
{{- if and ( eq .Values.keycloak.install true) ( .Values.keycloak.auth.existingSecret ) -}}
{{-   $secret := .Values.keycloak.auth.existingSecret -}}
{{- else -}}
{{-   $secret := .Values.externalDependencies.keycloak.auth.secretRef.secret -}}
{{- end -}}
{{- end -}}

{{- define "microcks.keycloak.secretRef.usernameKey" -}}
{{- if and ( .Values.externalDependencies.keycloak.auth.secretRef.usernameKey ) ( eq .Values.keycloak.install false-}}
{{-   $secret := .Values.externalDependencies.keycloak.auth.secretRef.usernameKey -}}
{{- end -}}
{{- end -}}

{{- define "microcks.keycloak.secretRef.passwordKey" -}}
{{- if and ( eq .Values.keycloak.install true) ( .Values.keycloak.auth.existingSecret ) -}}
{{-   $passwordKey := .Values.keycloak.auth.passwordSecretKey -}}
{{- else -}}
{{-   $passwordKey := .Values.externalDependencies.keycloak.auth.secretRef.passwordKey -}}
{{- end -}}
{{- end -}}