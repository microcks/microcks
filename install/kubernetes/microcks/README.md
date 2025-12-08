# Microcks

This chart bootstraps a new [Microcks](http://microcks.io) application using the [Helm](https://helm.sh) package manager.

Resources within this directory should work with Helm version 3+ (which do not need the Tiller server-side component).

## Installing the Chart

### Simple install - with no asynchronous mocking

From the [Helm Hub](https://hub.helm.sh) directly - assuming here for the example, you are running `minikube`:

```console
$ helm repo add microcks https://microcks.io/helm

$ kubectl create namespace microcks

$ helm install microcks microcks/microcks —-version 1.10.0 --namespace microcks --set microcks.url=microcks.$(minikube ip).nip.io --set keycloak.url=keycloak.$(minikube ip).nip.io --set keycloak.privateUrl=http://microcks-keycloak.microcks.svc.cluster.local:8080
  
NAME: microcks
LAST DEPLOYED: Wed Jul 31 17:38:43 2024
NAMESPACE: microcks
STATUS: deployed
REVISION: 1
TEST SUITE: None
NOTES:
Thank you for installing microcks.

Your release is named microcks.

To learn more about the release, try:

  $ helm status microcks
  $ helm get microcks

Microcks is available at https://microcks.192.168.64.6.nip.io.

GRPC mock service is available at "microcks-grpc.192.168.64.6.nip.io".
It has been exposed using TLS passthrough on the Ingress controller, you should extract the certificate for your client using:

  $ kubectl get secret microcks-microcks-grpc-secret -n microcks -o jsonpath='{.data.tls\.crt}' | base64 -d > tls.crt
  
Keycloak has been deployed on https://keycloak.192.168.64.6.nip.io to protect user access.
You may want to configure an Identity Provider or add some users for your Microcks installation by login in using the
username and password found into 'microcks-keycloak-admin' secret.
```

From the sources cloned locally:

```console
$ git clone https://github.com/microcks/microcks

$ cd install/kubernetes

$ helm install microcks ./microcks --namespace microcks \
   --set microcks.url=microcks.$(minikube ip).nip.io \
   --set keycloak.url=keycloak.$(minikube ip).nip.io \
   --set keycloak.privateUrl=http://microcks-keycloak.microcks.svc.cluster.local:8080

NAME: microcks
LAST DEPLOYED: Wed Jul 31 18:02:12 2024
NAMESPACE: microcks
STATUS: deployed
REVISION: 1
TEST SUITE: None
NOTES:
Thank you for installing microcks.

Your release is named microcks.

To learn more about the release, try:

  $ helm status microcks
  $ helm get microcks

Microcks is available at https://microcks.192.168.64.6.nip.io.

GRPC mock service is available at "microcks-grpc.192.168.64.6.nip.io".
It has been exposed using TLS passthrough on the Ingress controller, you should extract the certificate for your client using:

  $ kubectl get secret microcks-microcks-grpc-secret -n  -o jsonpath='{.data.tls\.crt}' | base64 -d > tls.crt

Keycloak has been deployed on https://keycloak.192.168.64.6.nip.io to protect user access.
You may want to configure an Identity Provider or add some users for your Microcks installation by login in using the
username and password found into 'microcks-keycloak-admin' secret.
```

### Advanced install - with asynchronous mocking

Microcks supports mocking of event-driven API thanks to [AsyncAPI Spec](https://asyncapi.com). Microcks will take care of publishing sample messages for you on a message broker. You may reuse an existing broker or let Microcks deploy its own (this is the default when turning on this feature).

To install a Kafka message broker during its deployment, Microcks relies on [Strimzi Operator](https://strimzi.io) and will try to create such custom resources such as `Kafka` and `KafkaTopic`. When using this configuration, you will thus need to install Strimzi Operator cluster-wide or on targeted namespace.

Here are some commands below on how to do that onto a Minikube instance:

```console
$ helm repo add strimzi https://strimzi.io/charts/
$ helm repo add microcks https://microcks.io/helm

$ kubectl create namespace microcks

$ helm install strimzi strimzi/strimzi-kafka-operator --namespace microcks

$ helm install microcks ./microcks --namespace=microcks \
    --set appName=microcks --set features.async.enabled=true \
    --set microcks.url=microcks.$(minikube ip).nip.io \
    --set keycloak.url=keycloak.$(minikube ip).nip.io \
    --set keycloak.privateUrl=http://microcks-keycloak.microcks.svc.cluster.local:8080 \
    --set features.async.kafka.url=$(minikube ip).nip.io 

NAME: microcks
LAST DEPLOYED: Wed Jul 31 18:27:35 2024
NAMESPACE: microcks
STATUS: deployed
REVISION: 1
TEST SUITE: None
NOTES:
Thank you for installing microcks.

Your release is named microcks.

To learn more about the release, try:

  $ helm status microcks
  $ helm get microcks

Microcks is available at https://microcks.192.168.64.6.nip.io.

GRPC mock service is available at "microcks-grpc.192.168.64.6.nip.io".
It has been exposed using TLS passthrough on the Ingress controller, you should extract the certificate for your client using:

  $ kubectl get secret microcks-microcks-grpc-secret -n  -o jsonpath='{.data.tls\.crt}' | base64 -d > tls.crt

Keycloak has been deployed on https://keycloak.192.168.64.6.nip.io to protect user access.
You may want to configure an Identity Provider or add some users for your Microcks installation by login in using the
username and password found into 'microcks-keycloak-admin' secret.

Kafka broker has been deployed on microcks-kafka.192.168.64.6.nip.io.
It has been exposed using TLS passthrough of the Ingress controller, you shoud extract the certificate for your client using:

  $ kubectl get secret microcks-kafka-cluster-ca-cert -o jsonpath='{.data.ca\.crt}' | base64 -d > ca.crt
```

[This video](https://www.youtube.com/watch?v=u7SP1bQ8_FE) details the setup.

## Configuration

All configurable variables and default values can be seen in `values.yaml`, with reasonable comments.

Typically, you may want to configure the following blocks and options:

* Global part is mandatory and contain attributes like `appName` of your install,
* `microcks` part is mandatory and contain attributes like the number of `replicas` and the access `url` if you want some customizations,
* `postman` part is mandatory for the number of `replicas`
* `keycloak` part is optional and allows specifying :
  * if you want a new install or reuse an existing instance: `keycloak.install`
  * if you want microcks to delegate authentication to keycloak: `keycloak.enabled`.
  * Note that `keycloak.enabled=false` forces keycloak not be installed in this release
* `mongodb` part is optional and allows specifying if you want a new install or reuse an existing instance.
* `features` part is optional and allows enabling and configuring opt-in features of Microcks.

The table below describes all the fields of the `values.yaml`, providing information on what's mandatory and what's optional as well as default values.

| Section    | Property                    | Description                                                                                                                                                                                                                                                                                                           |
|------------|-----------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `<root>`   | `appName`                   | **Mandatory**. Name of the Microcks deployment (Default to `Microcks`)                                                                                                                                                                                                                                                |
| `<root>`   | `ingresses`                 | **Mandatory**. Enable or Disable ingresses creation and associated secrets (Default to `true`)                                                                                                                                                                                                                        |
| `<root>`   | `gatewayRoutes`             | **Mandatory**. Enable or Disable `HTTPRoute` creation instead of `Ingress` (Default to `false`)                                                                                                                                                                                                                       |
| `<root>`   | `gatewayRefName`            | **Optional**. Set the name of the `Gateway` to reference when `gatewayRoutes` is enabled (Default to `default`)                                                                                                                                                                                                       |
| `<root>`   | `gatewayRefNamespace`       | **Optional**. Set the name of the `Gateway` to reference when `gatewayRoutes` is enabled (Default to `empty` which means "local namespace")                                                                                                                                                                           |
| `<root>`   | `gatewayRefSectionName`     | **Optional**. Set the name of the `Gateway` section/listener to reference from `HTTPRoute` (Default to `https`)                                                                                                                                                                                                       |
| `<root>`   | `grpcGatewayRefSectionName` | **Optional**. Set the name of the `Gateway` section/listener to reference from `GRPCRoute` (Default to `grpc`)                                                                                                                                                                                                        |
| `<root>`   | `serviceAccount.name`       | **Optional**. Defines a service account to use for the Microcks internal components (Default to `default`)                                                                                                                                                                                                            |
| `<root>`   | `serviceAccount.create`     | **Optional**. Whether the service account must be created by this chart, if not the `default` service account. (Default to `true`)                                                                                                                                                                                    |
| `<root>`   | `commonLabels`              | **Optional**. Defines the list of labels that will be added to all kubernetes resources (Default to `empty`)                                                                                                                                                                                                          |
| `<root>`   | `commonAnnotations`         | **Optional**. Defines the list of annotations that will be added to all kubernetes resources (Default to `empty`)                                                                                                                                                                                                     |
| `microcks` | `url`                       | **Mandatory**. The URL to use for exposing `Ingress`                                                                                                                                                                                                                                                                  |
| `microcks` | `ingressSecretRef`          | **Optional**. The name of a TLS Secret for securing `Ingress`. If missing, self-signed certificate is generated.                                                                                                                                                                                                      |
| `microcks` | `ingressAnnotations`        | **Optional**. A map of annotations that will be added to the `Ingress` or `HTTPRoute` for Microcks main pod. If these annotations are triggering a Certificate generation (for example through [cert-mamanger.io](https://cert-manager.io/)). The `generateCert` property should be set to `false`.                   |
| `microcks` | `generateCert`              | **Optional**. Whether to generate self-signed certificate or not if no valid `ingressSecretRef` provided. Default is `true`                                                                                                                                                                                           |
| `microcks` | `gatewayRefName`            | **Optional**. Overrides the parameter defined at `<root>` level if defined. Default is `undefined`.                                                                                                                                                                                                                   |
| `microcks` | `gatewayRefNamespace`       | **Optional**. Overrides the parameter defined at `<root>` level if defined. Default is `undefined`.                                                                                                                                                                                                                   |
| `microcks` | `gatewayRefSectionName`     | **Optional**. Overrides the parameter defined at `<root>` level if defined. Default is `undefined`.                                                                                                                                                                                                                   |
| `microcks` | `replicas`                  | **Optional**. The number of replicas for the Microcks main pod. Default is `1`.                                                                                                                                                                                                                                       |
| `microcks` | `image`                     | **Optional**. The reference of container image used. Chart comes with its default version.                                                                                                                                                                                                                            |
| `microcks` | `serviceType`               | **Optional**. The service type used. Defaults to `ClusterIP`.                                                                                                                                                                                                                                                         |
| `microcks` | `grpcEnableTLS`             | **Optional**. Flag to disable TLS on gRPC endpoint. Default value is `true`.                                                                                                                                                                                                                                          |
| `microcks` | `grpcSecretRef`             | **Optional**. The name of a TLS Secret for securing the gRPC endpoint. If missing, self-signed certificate is generated.                                                                                                                                                                                              |
| `microcks` | `grpcIngressAnnotations`    | **Optional**. A map of annotations that will be added to the `Ingress` or `GRPCRoute` for Microcks GRPC mocks. This allows you to specify specific ingress class and GRPC specific settings.                                                                                                                          |
| `microcks` | `grpcGatewayRefName`        | **Optional**. Overrides the parameter defined at `<root>` level if defined. Default is `undefined`.                                                                                                                                                                                                                   |
| `microcks` | `grpcGatewayRefNamespace`   | **Optional**. Overrides the parameter defined at `<root>` level if defined. Default is `undefined`.                                                                                                                                                                                                                   |
| `microcks` | `grpcGatewayRefSectionName` | **Optional**. Overrides the parameter defined at `<root>` level if defined. Default is `undefined`.                                                                                                                                                                                                                   |
| `microcks` | `resources`                 | **Optional**. Some resources constraints to apply on Microcks pods. This should be expressed using [Kubernetes syntax](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/#resource-requests-and-limits-of-pod-and-container).                                                             |
| `microcks` | `env`                       | **Optional**. Some environment variables to add on Microcks container. This should be expressed using [Kubernetes syntax](https://kubernetes.io/docs/tasks/inject-data-application/define-environment-variable-container/#define-an-environment-variable-for-a-container).                                            |
| `microcks` | `logLevel`                  | **Optional**. Allows to tune the verbosity level of logs. Default is `INFO`. You can use `DEBUG` for more verbosity or `WARN` for less.                                                                                                                                                                               |
| `microcks` | `mockInvocationStats`       | **Optional**. Allows to disable invocation stats on mocks. Default is `true` (enabled).                                                                                                                                                                                                                               |
| `microcks` | `extraProperties`           | **Optional**. Allows to add yaml extra application configurations. Default is `empty` (disabled).                                                                                                                                                                                                                     |
| `microcks` | `customSecretRef`           | **Optional**. Permit to use a secret (for exemple a keystore). Default is `false` (disabled).                                                                                                                                                                                                                         |
| `postman`  | `replicas`                  | **Optional**. The number of replicas for the Microcks Postman pod. Default is `1`.                                                                                                                                                                                                                                    |
| `postman`  | `image`                     | **Optional**. The reference of container image used. Chart comes with its default version.                                                                                                                                                                                                                            |
| `postman`  | `resources`                 | **Optional**. Some resources constraints to apply on Postman pods. This should be expressed using [Kubernetes syntax](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/#resource-requests-and-limits-of-pod-and-container)..                                                             |
| `keycloak` | `enabled`                   | **Optional**. Flag for using Keycloak to protect Microcks API. Default is `true`. Set to `false` if you want to bypass the authorization checks, **this is not recommended for production environment**.                                                                                                              |
| `keycloak` | `install`                   | **Optional**. Flag for Keycloak installation. Default is `true`. Set to `false` if you want to reuse an existing Keycloak instance.                                                                                                                                                                                   |
| `keycloak` | `realm`                     | **Optional**. Name of Keycloak realm to use. Should be setup only if `install` is `false` and you want to reuse an existing realm. Default is `microcks`.                                                                                                                                                             |
| `keycloak` | `url`                       | **Mandatory**. The URL of Keycloak install - indeed just the hostname + port part - if it already exists or the one used for exposing Keycloak `Ingress`.                                                                                                                                                             |
| `keycloak` | `privateUrl`                | **Optional**. A private URL - a full URL here - used by the Microcks component to internally join Keycloak. This is also known as `backendUrl` in [Keycloak doc](https://www.keycloak.org/docs/latest/server_installation/#_hostname). When specified, the `keycloak.url` is used as `frontendUrl` in Keycloak terms. |
| `keycloak` | `ingressSecretRef`          | **Optional**. The name of a TLS Secret for securing `Ingress`. If missing, self-signed certificate is generated.                                                                                                                                                                                                      |
| `keycloak` | `ingressAnnotations`        | **Optional**. A map of annotations that will be added to the `Ingress` or `HTTPRoute` for Keycloak pod. If these annotations are triggering a Certificate generation (for example through [cert-mamanger.io](https://cert-manager.io/)). The `generateCert` property should be set to `false`.                        |
| `keycloak` | `generateCert`              | **Optional**. Whether to generate self-signed certificate or not if no valid `ingressSecretRef` provided. Default is `true`                                                                                                                                                                                           |
| `keycloak` | `gatewayRefName`            | **Optional**. Overrides the parameter defined at `<root>` level if defined. Default is `undefined`.                                                                                                                                                                                                                   |
| `keycloak` | `gatewayRefNamespace`       | **Optional**. Overrides the parameter defined at `<root>` level if defined. Default is `undefined`.                                                                                                                                                                                                                   |
| `keycloak` | `gatewayRefSectionName`     | **Optional**. Overrides the parameter defined at `<root>` level if defined. Default is `undefined`.                                                                                                                                                                                                                   |
| `keycloak` | `secretRef`                 | **Optional**. Reference of a Secret containing credentials for connecting a provided Keycloak instance. Mandatory if `install` is `false`.                                                                                                                                                                            |
| `keycloak` | `pvcAnnotations`            | **Optional**. A map of annotations that will be added to the `pvc` for the Keycloak pod.                                                                                                                                                                                                                              |
| `keycloak` | `image`                     | **Optional**. The reference of container image used. Chart comes with its default version.                                                                                                                                                                                                                            |
| `keycloak` | `serviceType`               | **Optional**. The service type used. Defaults to `ClusterIP`.                                                                                                                                                                                                                                                         |
| `keycloak` | `resources`                 | **Optional**. Some resources constraints to apply on Keycloak pods. This should be expressed using [Kubernetes syntax](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/#resource-requests-and-limits-of-pod-and-container).                                                             |
| `keycloak` | `persistent`                | **Optional**. Flag for Keycloak persistence. Default is `true`. Set to `false` if you want an ephemeral Keycloak installation.                                                                                                                                                                                        |
| `keycloak` | `volumeSize`                | **Optional**. Size of persistent volume claim for Keycloak. Default is `1Gi`. Not used if not persistent install asked.                                                                                                                                                                                               |
| `keycloak` | `storageClassName`          | **Optional**. The cluster storage class to use for persistent volume claim. If not specified, we rely on cluster default storage class.                                                                                                                                                                               |
| `keycloak` | `postgresImage`             | **Optional**. The reference of container image used. Chart comes with its default version.                                                                                                                                                                                                                            |
| `keycloak` | `postgresResources`         | **Optional**. Some resources constraints to apply on Keycloak Postgres pods. This should be expressed using [Kubernetes syntax](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/#resource-requests-and-limits-of-pod-and-container).                                                    |
| `keycloak` | `serviceAccount`            | **Optional**. A service account to create into Microcks Keycloak realm. Default is `microcks-serviceaccount`.                                                                                                                                                                                                         |
| `keycloak` | `serviceAccountCredentials` | **Optional**. The credentials of Keycloak realm service account for Microcks. Default is `ab54d329-e435-41ae-a900-ec6b3fe15c54`.                                                                                                                                                                                      |
| `mongodb`  | `install`                   | **Optional**. Flag for MongoDB installation. Default is `true`. Set to `false` if you want to reuse an existing MongoDB instance.                                                                                                                                                                                     |
| `mongodb`  | `uri`                       | **Optional**. MongoDB URI in case you're reusing existing MongoDB instance (must include `mongo://` or `mongo+srv://` prefix). Mandatory if `install` is `false`.                                                                                                                                                     |
| `mongodb`  | `uriParameters`             | **Optional**. Allows you to add parameters to the mongodb uri connection string.                                                                                                                                                                                                                                      |
| `mongodb`  | `database`                  | **Optional**. MongoDB database name in case you're reusing existing MongoDB instance. Used if `install` is `false`. Default to `appName`.                                                                                                                                                                             |
| `mongodb`  | `secretRef`                 | **Optional**. Reference of a Secret containing credentials for connecting a provided MongoDB instance. Mandatory if `install` is `false`.                                                                                                                                                                             |
| `mongodb`  | `pvcAnnotations`            | **Optional**. A map of annotations that will be added to the `pvc` for the MongoDB pod.                                                                                                                                                                                                                               |
| `mongodb`  | `resources`                 | **Optional**. Some resources constraints to apply on MongoDB pods. This should be expressed using [Kubernetes syntax](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/#resource-requests-and-limits-of-pod-and-container).                                                              |
| `mongodb`  | `persistent`                | **Optional**. Flag for MongoDB persistence. Default is `true`. Set to `false` if you want an ephemeral MongoDB installation.                                                                                                                                                                                          |
| `mongodb`  | `volumeSize`                | **Optional**. Size of persistent volume claim for MongoDB. Default is `2Gi`. Not used if not persistent install asked.                                                                                                                                                                                                |
| `mongodb`  | `storageClassName`          | **Optional**. The cluster storage class to use for persistent volume claim. If not specified, we rely on cluster default storage class.                                                                                                                                                                               |
| `features` | `repositoryFilter`          | **Optional**. Feature allowing to filter API and services on main page. Must be explicitly `enabled`. See [Organizing repository](https://microcks.io/documentation/using/advanced/organizing/#master-level-filter) for more information.                                                                             |
| `features` | `repositoryTenancy`         | **Optional**. Feature allowing to segment and delegate API and services management according the `repositoryFilter` master criteria. Must be explicitly `enabled`. See [Organizing repository](https://microcks.io/documentation/using/advanced/organizing/#rbac-security-segmentation) for more information.         |
| `features` | `microcksHub.enabled`       | **Optional**. Feature allowing to directly import mocks coming from `hub.microcks.io` marketplace. Default is `true`. See [Micorkcs Hub](https://microcks.io/documentation/using/advanced/microcks-hub) for more information.                                                                                         |
| `features` | `async.enabled`             | **Optional**. Feature allowing to mock an tests asynchronous APIs through Events. Enabling it requires an active message broker. Default is `false`.                                                                                                                                                                  |
| `features` | `async.image`               | **Optional**. The reference of container image used for `async-minion` component. Chart comes with its default version.                                                                                                                                                                                               |
| `features` | `async.env`                 | **Optional**. Some environment variables to add on `async-minion` container. This should be expressed using [Kubernetes syntax](https://kubernetes.io/docs/tasks/inject-data-application/define-environment-variable-container/#define-an-environment-variable-for-a-container).                                      |
| `features` | `aiCopilot`                 | **Optional**. Feature allowing to activate AI Copilot. Must be explicitly `enabled`. Default is `false`.                                                                                                                                                                                                              |

### AI Copilot feature

Here are below the configuration properties of the AI Copilot feature:

| Section                     | Property           | Description                                                                                                                               |
|-----------------------------| ------------------ |-------------------------------------------------------------------------------------------------------------------------------------------|
| `features.aiCopilot`        | `enabled`          | **Optional**. Flag for enabling the feature. Default is `false`. Set to `true` to activate.                                               |
| `features.aiCopilot`        | `implementation`   | **Optional**. Allow to choose an AI service implementation. Default is `openai` and its the only supported value at the moment.           |
| `features.aiCopilot.openai` | `apiKey`           | **Madantory** when enabled. Put here your OpenAI API key.                                                                                 |
| `features.aiCopilot.openai` | `timeout`          | **Optional**. Allow the customization of the timeout when requesting OpenAI. Default to `20` seconds.                                     |
| `features.aiCopilot.openai` | `model`            | **Optional**. Allow the customization of the OpenAI model to use. Default may vary from one Microcks version to another.                  |
| `features.aiCopilot.openai` | `maxTokens`        | **Optional**. Allow the customization of the maximum number of tokens that may be exchanged by OpenAI services. Default to `2000` tokens? |

### Kafka feature details

Here are below the configuration properties of the Kafka support feature:

| Section                               | Property                        | Description                                                                                                                                                                                                            |
| ------------------------------------- |---------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `features.async.kafka`                | `install`                       | **Optional**. Flag for Kafka installation. Default is `true` and require Strimzi Operator to be setup. Set to `false` if you want to reuse an existing Kafka instance.                                                 |
| `features.async.kafka`                | `enableKraft`                   | **Optional**. Flag for Kafka installation. Default is `true` and require Strimzi Operator >= 0.42 to be setup. Set to `false` if you use an older version and want to stick to Zookeeper based Kafka cluster.          |
| `features.async.kafka`                | `url`                           | **Optional**. The URL of Kafka broker if it already exists or the one used for exposing Kafka `Ingress` when we install it. In this later case, it should only be the subdomain part (eg: `apps.example.com`).         |
| `features.async.kafka`                | `ingressClassName`              | **Optional**. The ingress class to use for exposing broker to the outer world when installing it. Default is `nginx`.                                                                                                  |
| `features.async.kafka`                | `persistent`                    | **Optional**. Flag for Kafka persistence. Default is `false`. Set to `true` if you want a persistent Kafka installation.                                                                                               |
| `features.async.kafka`                | `volumeSize`                    | **Optional**. Size of persistent volume claim for Kafka. Default is `2Gi`. Not used if not persistent install asked.                                                                                                   |
| `features.async.kafka.schemaRegistry` | `url`                           | **Optional**. The API URL of a Kafka Schema Registry. Used for Avro based serialization                                                                                                                                |
| `features.async.kafka.schemaRegistry` | `confluent`                     | **Optional**. Flag for indicating that registry is a Confluent one, or using a Confluent compatibility mode. Default to `true`                                                                                         |
| `features.async.kafka.schemaRegistry` | `username`                      | **Optional**. Username for connecting to the specified Schema registry. Default to ``                                                                                                                                  |
| `features.async.kafka.schemaRegistry` | `credentialsSource`             | **Optional**. Source of the credentials for connecting to the specified Schema registry. Default to `USER_INFO`                                                                                                        |
| `features.async.kafka.authentication` | `type`                          | **Optional**. The type of authentication for connecting to a pre-existing Kafka broker. Supports `SSL` or `SASL_SSL`. Default to `none`                                                                                |
| `features.async.kafka.authentication` | `truststoreType`                | **Optional**. For TLS transport, you'll always need a truststore to hold your cluster certificate. Default to `PKCS12`                                                                                                 |
| `features.async.kafka.authentication` | `truststoreSecretRef`           | **Optional**. For TLS transport, the reference of a Secret holding truststore and its password. Set `secret`, `storeKey` and `passwordKey` properties                                                                  |
| `features.async.kafka.authentication` | `keystoreType`                  | **Optional**. In case of `SSL` type, you'll also need a keystore to hold your user private key for mutual TLS authentication. Default to `PKCS12`                                                                      |
| `features.async.kafka.authentication` | `keystoreSecretRef`             | **Optional**. For mutual TLS authentication, the reference of a Secret holding keystore and its password. Set `secret`, `storeKey` and `passwordKey` properties                                                        |
| `features.async.kafka.authentication` | `saslMechanism`                 | **Optional**. For SASL authentication, you'll have to specify an additional authentication mechanism such as `SCRAM-SHA-512` or `OAUTHBEARER`                                                                          |
| `features.async.kafka.authentication` | `saslJaasConfig`                | **Optional**. For SASL authentication, you'll have to specify a JAAS configuration line with login module, username and password.                                                                                      |
| `features.async.kafka.authentication` | `saslLoginCallbackHandlerClass` | **Optional**. For SASL authentication, you may want to provide a Login Callback Handler implementations. This implementation may be provided by extending the main and `async-minion` images and adding your own libs. |

#### MQTT feature details

Here are below the configuration properties of the MQTT support feature:

| Section               | Property   | Description                                                                                                                              |
| --------------------- | ---------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| `features.async.mqtt` | `url`      | **Optional**. The URL of MQTT broker (eg: `my-mqtt-broker.example.com:1883`). Default is undefined which means that feature is disabled. |
| `features.async.mqtt` | `username` | **Optional**. The username to use for connecting to secured MQTT broker. Default to `microcks`.                                          |
| `features.async.mqtt` | `password` | **Optional**. The password to use for connecting to secured MQTT broker. Default to `microcks`.                                          |

#### WebSocket feature details

Here are below the configuration properties of the WebSocket support feature:

| Section             | Property                | Description                                                                                                                                                                                                                                                                                |
| ------------------- |-------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `features.async.ws` | `ingressSecretRef`      | **Optional**. The name of a TLS Secret for securing WebSocket `Ingress`. If missing, self-signed certificate is generated.                                                                                                                                                                 |
| `features.async.ws` | `ingressAnnotations`    | **Optional**. A map of annotations that will be added to the `Ingress` for Microcks WebSocket mocks. If these annotations are triggering a Certificate generation (for example through [cert-mamanger.io](https://cert-manager.io/)). The `generateCert` property should be set to `false`.|
| `features.async.ws` | `generateCert`          | **Optional**. Whether to generate self-signed certificate or not if no valid `ingressSecretRef` provided. Default is `true`                                                                                                                                                                |
| `features.async.ws` | `gatewayRefName`        | **Optional**. Overrides the parameter defined at `<root>` level if defined. Default is `undefined`.                                                                                                                                                                                        |
| `features.async.ws` | `gatewayRefNamespace`   | **Optional**. Overrides the parameter defined at `<root>` level if defined. Default is `undefined`.                                                                                                                                                                                        |
| `features.async.ws` | `gatewayRefSectionName` | **Optional**. Overrides the parameter defined at `<root>` level if defined. Default is `undefined`.                                                                                                                                                                                        |

#### AMQP feature details

Here are below the configuration properties of the AMQP support feature:

| Section               | Property   | Description                                                                                                                              |
| --------------------- | ---------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| `features.async.amqp` | `url`      | **Optional**. The URL of AMQP broker (eg: `my-amqp-broker.example.com:5672`). Default is undefined which means that feature is disabled. |
| `features.async.amqp` | `username` | **Optional**. The username to use for connecting to secured AMQP broker. Default to `microcks`.                                          |
| `features.async.amqp` | `password` | **Optional**. The password to use for connecting to secured AMQP broker. Default to `microcks`.                                          |

#### NATS feature details

Here are below the configuration properties of the NATS support feature:

| Section               | Property   | Description                                                                                                                              |
| --------------------- | ---------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| `features.async.nats` | `url`      | **Optional**. The URL of NATS broker (eg: `my-nats-broker.example.com:4222`). Default is undefined which means that feature is disabled. |
| `features.async.nats` | `username` | **Optional**. The username to use for connecting to secured NATS broker. Default to `microcks`.                                          |
| `features.async.nats` | `password` | **Optional**. The password to use for connecting to secured NATS broker. Default to `microcks`.                                          |

#### Google PubSub feature details

Here are below the configuration properties of the Google PubSub support feature:

| Section                       | Property  | Description                                                                                                                          |
|-------------------------------|-----------|--------------------------------------------------------------------------------------------------------------------------------------|
| `features.async.googlepubsub` | `project` | **Optional**. The GCP project id of PubSub (eg: `my-gcp-project-347219`). Default is undefined which means that feature is disabled. |
| `features.async.googlepubsub` | `serviceAccountSecretRef` | **Optional**. The name of a Generic Secret holding Service Account JSON credentiels. Set `secret` and `fileKey` properties.          |

#### Amazon SQS feature details

Here are below the configuration properties of the Amazon SQS support feature:

| Section              | Property               | Description                                                                                                                                                                                                                                                                     |
|----------------------|------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `features.async.sqs` | `region`               | **Optional**. The AWS region for connecting SQS service (eg: `eu-west-3`). Default is undefined which means that feature is disabled.                                                                                                                                           |
| `features.async.sqs` | `credentialsType`      | **Optional**. The type of credentials we use for authentication. 2 options here `env-variable` or `profile`. Default to `env-variable`.                                                                                                                                         |
| `features.async.sqs` | `credentialsProfile`   | **Optional**. When using `profile` authent, name of profile to use for authenticating to SQS. This profile should be present into a credentials file mounted from a Secret (see below). Default to `microcks-sqs-admin`.                                                        |
| `features.async.sqs` | `credentialsSecretRef` | **Optional**. The name of a Generic Secret holding either environment variables (set `secret` and `accessKeyIdKey`, `secretAccessKeyKey` and optional `sessionTokenKey` properties) or an AWS credentials file with referenced profile (set `secret` and `fileKey` properties). |
| `features.async.sqs` | `endpointOverride`     | **Optional**. The AWS endpoint URI used for API calls. Handy for using SQS via [LocalStack](https://localstack.cloud).                                                                                                                                                          |

#### Amazon SNS feature details

Here are below the configuration properties of the Amazon SNS support feature:

| Section              | Property               | Description                                                                                                                                                                                                                                                                     |
|----------------------|------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `features.async.sns` | `region`               | **Optional**. The AWS region for connecting SNS service (eg: `eu-west-3`). Default is undefined which means that feature is disabled.                                                                                                                                           |
| `features.async.sns` | `credentialsType`      | **Optional**. The type of credentials we use for authentication. 2 options here `env-variable` or `profile`. Default to `env-variable`.                                                                                                                                         |
| `features.async.sns` | `credentialsProfile`   | **Optional**. When using `profile` authent, name of profile to use for authenticating to SQS. This profile should be present into a credentials file mounted from a Secret (see below). Default to `microcks-sns-admin`.                                                        |
| `features.async.sns` | `credentialsSecretRef` | **Optional**. The name of a Generic Secret holding either environment variables (set `secret` and `accessKeyIdKey`, `secretAccessKeyKey` and optional `sessionTokenKey` properties) or an AWS credentials file with referenced profile (set `secret` and `fileKey` properties). |
| `features.async.sns` | `endpointOverride`     | **Optional**. The AWS endpoint URI used for API calls. Handy for using SNS via [LocalStack](https://localstack.cloud).                                                                                                                                                          |

> **Note:** Enabling both SQS and SNS features and using `env-variable` credentials type for both, may lead to collision as both clients rely on the
> same environment variables. So you have to specify `credentialsSecretRef` on only one of those two services and be sure that the access key and secret
> access key mounted refers to a IAM account having write access to both services.

### Examples

You may want to launch custom installation with such a command:

 ```console
 $ helm install microcks ./microcks --namespace=microcks \
    --set appName=mocks --set mongodb.volumeSize=5Gi \
    --set microcks.url=mocks-microcks.apps.example.com \
    --set keycloak.url=keycloak-microcks.apps.example.com
 ```

or - with included Kafka for async mocking turned on:

 ```console
 $ helm install microcks ./microcks --namespace=microcks \
    --set appName=microcks --set features.async.enabled=true \
    --set microcks.url=microcks.$(minikube ip).nip.io \
    --set keycloak.url=keycloak.$(minikube ip).nip.io \
    --set features.async.kafka.url=$(minikube ip).nip.io
 ```

## Checking everything is OK

Just check you've got this 5 running pods:

```console
$ kubectl get pods -n microcks
NAME                                            READY   STATUS    RESTARTS   AGE
microcks-7f8445887d-f7wt9                       1/1     Running   0          39s
microcks-keycloak-bbbfcb-8flrr                  1/1     Running   0          39s
microcks-keycloak-postgresql-6dc77c4968-5dcjd   1/1     Running   0          39s
microcks-mongodb-6d558666dc-zdhxl               1/1     Running   0          39s
microcks-postman-runtime-58bf695b59-nm858       1/1     Running   0          39s
```

## Deleting the Chart

```console
helm delete microcks
helm del --purge microcks
```
