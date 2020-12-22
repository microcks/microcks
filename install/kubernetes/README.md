# Microcks

This chart bootstraps a new [Microcks](http://microcks.io) application using the [Helm](https://helm.sh) package manager.

Resources within this directory are intended to work with Helm version 3+ (which do not need the Tiller server-side component).

## Installing the Chart

### Simple install - with no asynchronous mocking

From the [Helm Hub](https://hub.helm.sh) directly - assuming here for the example, you are running `minikube`:

```console
$ helm repo add microcks https://microcks.io/helm

$ kubectl create namespace microcks

$ helm install microcks microcks/microcks —-version 1.1.0 --namespace microcks --set microcks.url=microcks.$(minikube ip).nip.io --set keycloak.url=keycloak.$(minikube ip).nip.io
  
NAME: microcks
LAST DEPLOYED: Mon Aug 10 18:57:46 2020
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

Keycloak has been deployed on https://keycloak.192.168.64.6.nip.io/auth to protect user access.
You may want to configure an Identity Provider or add some users for your Microcks installation by login in using the
username and password found into 'microcks-keycloak-admin' secret.
```

From the sources cloned locally:

```console
$ git clone https://github.com/microcks/microcks

$ cd install/kubernetes

$ helm install microcks ./microcks --namespace microcks \
   --set microcks.url=microcks.$(minikube ip).nip.io \
   --set keycloak.url=keycloak.$(minikube ip).nip.io 

NAME: microcks
LAST DEPLOYED: Mon Aug 10 18:59:17 2020
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

Keycloak has been deployed on https://keycloak.192.168.64.6.nip.io/auth to protect user access.
You may want to configure an Identity Provider or add some users for your Microcks installation by login in using the
username and password found into 'microcks-keycloak-admin' secret.
```

### Advanced install - with asynchronous mocking

Since release `1.0.0`, Microcks support mocking of event-driven API thanks to [AsyncAPI Spec](https://asyncapi.com). Microcks will take car of publishing sample messages for you on a message broker. You mey reuse an existing broker of let Microcks deploy its own (this is the default when turning on this feature).

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
    --set features.async.kafka.url=$(minikube ip).nip.io 

NAME: microcks
LAST DEPLOYED: Mon Aug 10 19:22:38 2020
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

Keycloak has been deployed on https://keycloak.192.168.64.6.nip.io/auth to protect user access.
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
* `keycloak` part is optional and allows to specify if you want a new install or reuse an existing instance,
* `mongodb` part is optional and allows to specify if you want a new install or reuse an existing instance.
* `features` part is optional and allows to enable and configure opt-in features of Microcks.

The table below describe all the fields of the `values.yaml`, providing informations on what's mandatory and what's optional as well as default values.

| Section    | Property           | Description   |
| ------------- | ------------------ | ------------- |
| `microcks`    | `url`              | **Mandatory**. The URL to use for exposing `Ingress` | 
| `microcks`    | `ingressSecretRef` | **Optional**. The name of a TLS Secret for securing `Ingress`. If missing, self-signed certificate is generated. |
| `microcks`    | `ingressAnnotations`  | **Optional**. A map of annotations that will be added to the `Ingress` for Microcks main pod. | 
| `microcks`    | `replicas`         | **Optional**. The number of replicas for the Microcks main pod. Default is `1`. |
| `microcks`    | `image`            | **Optional**. The reference of container image used. Chart comes with its default version. |
| `postman`     | `replicas`         | **Optional**. The number of replicas for the Microcks Postman pod. Default is `1`. |
| `postman`     | `image`            | **Optional**. The reference of container image used. Chart comes with its default version. |
| `keycloak`    | `install`          | **Optional**. Flag for Keycloak installation. Default is `true`. Set to `false` if you want to reuse an existing Keycloak instance. |
| `keycloak`    | `realm`            | **Optional**. Name of Keycloak realm to use. Should be setup only if `install` is `false` and you want to reuse an existing realm. Default is `microcks`. |
| `keycloak`    | `url`              | **Mandatory**. The URL of Keycloak install - indeed just the hostname + port part - if it already exists or the one used for exposing Keycloak `Ingress`. |
| `keycloak`    | `privateUrl`       | **Optional**. A private URL - a full URL here - used by the Microcks component to internally join Keycloak. This is also known as `backendUrl` in [Keycloak doc](https://www.keycloak.org/docs/latest/server_installation/#_hostname). When specified, the `keycloak.url` is used as `frontendUrl` in Keycloak terms. | 
| `keycloak`    | `ingressSecretRef` | **Optional**. The name of a TLS Secret for securing `Ingress`. If missing, self-signed certificate is generated. |
| `keycloak`    | `ingressAnnotations`  | **Optional**. A map of annotations that will be added to the `Ingress` for Keycloak pod. |  
| `keycloak`    | `image`            | **Optional**. The reference of container image used. Chart comes with its default version. |
| `keycloak`    | `persistent`       | **Optional**. Flag for Keycloak persistence. Default is `true`. Set to `false` if you want an ephemeral Keycloak installation. |
| `keycloak`    | `volumeSize`       | **Optional**. Size of persistent volume claim for Keycloak. Default is `1Gi`. Not used if not persistent install asked. |
| `keycloak`    | `postgresImage`    | **Optional**. The reference of container image used. Chart comes with its default version. |
| `keycloak`    | `serviceAccount`    | **Optional**. A service account to create into Microcks Keycloak realm. Default is `microcks-serviceaccount`. |
| `keycloak`    | `serviceAccountCredentials`    | **Optional**. The credentials of Keycloak realm service account for Microcks. Default is `ab54d329-e435-41ae-a900-ec6b3fe15c54`. |
| `mongodb`     | `install`          | **Optional**. Flag for MongoDB installation. Default is `true`. Set to `false` if you want to reuse an existing MongoDB instance. |
| `mongodb`     | `uri`              | **Optional**. MongoDB URI in case you're reusing existing MongoDB instance. Mandatory if `install` is `false`. |
| `mongodb`     | `database`         | **Optional**. MongoDB database name in case you're reusing existing MongoDB instance. Used if `install` is `false`. Default to `appName`. |
| `mongodb`     | `secretRef`        | **Optional**. Reference of a Secret containing credentials for connecting a provided MongoDB instance. Mandatory if `install` is `false`. |
| `mongodb`     | `persistent`       | **Optional**. Flag for MongoDB persistence. Default is `true`. Set to `false` if you want an ephemeral MongoDB installation. |
| `mongodb`     | `volumeSize`       | **Optional**. Size of persistent volume claim for MongoDB. Default is `2Gi`. Not used if not persistent install asked. |
| `features`    | `repositoryFilter` | **Optional**. Feature allowing to filter API and services on main page. Must be explicitely `enabled`. See [Organizing repository](https://microcks.io/documentation/using/advanced/organizing/#master-level-filter) for more informations. |
| `features`    | `async.enabled`    | **Optional**. Feature allowing to mock an tests asynchronous APIs through Events. Enebling it requires an active message broker. Default is `false`. |
| `features`    | `async.image`      | **Optional**. The reference of container image used for `async-minion` component. Chart comes with its default version. |


### Kafka feature details

Here are below the configuration properties of the Kafka support features:
 
| Section    | Property           | Description   |
| ------------- | ------------------ | ------------- |
| `features.async.kafka` | `install`    | **Optional**. Flag for Kafka installation. Default is `true` and required Strinzi Operator to be setup. Set to `false` if you want to reuse an existing Kafka instance. |
| `features.async.kafka` | `url`        | **Optional**. The URL of Kafka broker if it already exists or the one used for exposing Kafka `Ingress` when we install it. In this later case, it should only be the subdomain part (eg: `apps.example.com`). |
| `features.async.kafka` | `persistent` | **Optional**. Flag for Kafka persistence. Default is `false`. Set to `true` if you want a persistent Kafka installation. |
| `features.async.kafka` | `volumeSize` | **Optional**. Size of persistent volume claim for Kafka. Default is `2Gi`. Not used if not persistent install asked. |
| `features.async.kafka.schemaRegistry` | `url` | **Optional**. The API URL of a Kafka Schema Registry. Used for Avro based serialization |
| `features.async.kafka.schemaRegistry` | `confluent` | **Optional**. Flag for indicating that registry is a Confluent one. Default to `true` |
| `features.async.kafka.schemaRegistry` | `username`  | **Optional**. Username for connecting to the specified Schema registry. Default to `` |
| `features.async.kafka.schemaRegistry` | `credentialsSource`  | **Optional**. Source of the credentials for connecting to the specified Schema registry. Default to `USER_INFO` |

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
$ helm delete microcks
$ helm del --purge microcks
```