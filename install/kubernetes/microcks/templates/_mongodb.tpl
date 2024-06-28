{{/*
Detect and return the MongoDB's url
*/}}
{{- define "microcks.mongodb.url" -}}
{{- if .Values.mongdb.install -}}
{{-   $url := "mongodb"  -}}
{{- else -}}
{{-   $url := .Values.externalDependencies.mongodb.url -}}
{{- end -}}
{{- end -}}

{{/*
Detect and return the MongoDB's Port
*/}}
{{- define "microcks.mongodb.port" -}}
{{- if .Values.mongodb.install -}}
{{- default "27017" .Values.mongodb.containerPorts.mongodb -}}
{{- else -}}
{{- default "27017" .Values.externalDependencies.mongodb.port -}}
{{- end -}}
{{- end -}}

{{/*
Detect and return the MongoDB's database
*/}}
{{- define "microcks.mongodb.database" -}}
{{- if .Values.mongodb.install -}}
{{-   $database := "microcks" -}}
{{- else -}}
{{-   $database := default "microcks" .Values.externalDependencies.mongodb.database -}}
{{- end -}}
{{- end -}}

{{/*
Detect and return the MongoDB's username in plaintext
*/}}
{{- define "microcks.mongodb.username" -}}
{{- if and ( eq .Values.mongodb.install true) ( .Values.mongodb.rootUser) -}}
{{-   $username := .Values.mongodb.rootUser -}}
{{- else -}}
{{-   $username := .Values.externalDependencies.mongodb.auth.username -}}
{{- end -}}
{{- end -}}

{{/*
Detect and return the MongoDB's password in plaintext
*/}}
{{- define "microcks.mongodb.password" -}}
{{- if and ( eq .Values.mongodb.install true) ( .Values.mongodb.rootPassword) -}}
{{-   $password := .Values.mongodb.rootPassword -}}
{{- else -}}
{{-   $password := .Values.externalDependencies.mongodb.auth.password -}}
{{- end -}}
{{- end -}}

{{/*
Detect and return the MongoDB's authenticates in secret
*/}}
{{- define "microcks.mongodb.secretRef.secret" -}}
{{- if and ( eq .Values.mongodb.install true) ( .Values.mongodb.auth.existingSecret ) -}}
{{-   $secret := .Values.mongodb.auth.existingSecret -}}
{{- else -}}
{{-   $secret := .Values.externalDependencies.mongodb.auth.secretRef.secret -}}
{{- end -}}
{{- end -}}

{{- define "microcks.mongodb.secretRef.usernameKey" -}}
{{- if and ( .Values.externalDependencies.mongodb.auth.secretRef.usernameKey ) ( eq .Values.mongodb.install false-}}
{{-   $secret := .Values.externalDependencies.mongodb.auth.secretRef.usernameKey -}}
{{- end -}}
{{- end -}}

{{- define "microcks.mongodb.secretRef.passwordKey" -}}
{{- if and ( eq .Values.mongodb.install true) ( .Values.mongodb.auth.existingSecret ) -}}
{{-   $passwordKey := mongodb-root-password -}}
{{- else -}}
{{-   $passwordKey := .Values.externalDependencies.mongodb.auth.secretRef.passwordKey -}}
{{- end -}}
{{- end -}}