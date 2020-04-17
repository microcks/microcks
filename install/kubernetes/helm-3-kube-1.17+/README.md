# Microcks

This chart bootstraps a new [Microcks](http://microcks.github.io) application using the [Helm](https://helm.sh) package manager.

Resources within this directory are intended to work with Helm version 3+ (which do not need the Tiller server-side component).

## Installing the Chart

From the [Helm Hub](https://hub.helm.sh) directly - assuming here for the example, you are running `minikube`:

```console
$ helm repo add microcks https://microcks.io/helm

$ kubectl create namespace microcks

$ helm install microcks microcks/microcks —-version 0.9.0 --namespace microcks --set microcks.url=microcks.$(minikube ip).nip.io --set keycloak.url=keycloak.$(minikube ip).nip.io
  
NAME: microcks
LAST DEPLOYED: Wed Apr 15 19:35:33 2020
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

$ cd install/kubernetes/helm-3-kube-1.17+

$ helm install microcks ./microcks --namespace microcks \
   --set microcks.url=microcks.$(minikube ip).nip.io \
   --set keycloak.url=keycloak.$(minikube ip).nip.io 

NAME: microcks
LAST DEPLOYED: Wed Apr 15 19:27:15 2020
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

## Configuration

All configurable variables and default values can be seen in `values.yaml`, with reasonable comments.

Typically, you may want to configure the following blocks and options:
* Global part is mandatory and contain attributes like `appName` of your install,
* `microcks` part is mandatory and contain attributes like the number of `replicas` and the access `url` if you want some customizations, 
* `postman` part is mandatory for the number of `replicas`
* `keycloak` part is optional and allows to specifiy if you want a new install or reuse an existing instance,
* `mongodb` part is optional and allows to specifiy if you want a new install or reuse an existing instance.
* `features` part is optional and allowd to enable and configure opt-in features of Microcks.

The table below describe all the fields of the `values.yaml`, providing informations on what's mandatory and what's optional as well as default values.

| Section       | Property           | Description   |
| ------------- | ------------------ | ------------- |
| `microcks`    | `url`              | **Mandatory**. The URL to use for exposing `Ingress` | 
| `microcks`    | `ingressSecretRef` | **Optional**. The name of a TLS Secret for securing `Ingress`. If missing, self-signed certificate is generated. | 
| `microcks`    | `replicas`         | **Optional**. The number of replicas for the Microcks main pod. Default is `1`. |
| `microcks`    | `image`            | **Optional**. The reference of container image used. Chart comes with its default version. |
| `postman`     | `replicas`         | **Optional**. The number of replicas for the Microcks Postman pod. Default is `1`. |
| `postman`     | `image`            | **Optional**. The reference of container image used. Chart comes with its default version. |
| `keycloak`    | `install`          | **Optional**. Flag for Keycloak installation. Default is `true`. Set to `false` if you want to reuse an existing Keycloak instance. |
| `keycloak`    | `url`              | **Mandatory**. The URL of Keycloak install if it already exists or the one used for exposing Keycloak `Ingress` | 
| `keycloak`    | `ingressSecretRef` | **Optional**. The name of a TLS Secret for securing `Ingress`. If missing, self-signed certificate is generated. |  
| `keycloak`    | `image`            | **Optional**. The reference of container image used. Chart comes with its default version. |
| `keycloak`    | `persistent`       | **Optional**. Flag for Keycloak persistence. Default is `true`. Set to `false` if you want an ephemeral Keycloak installation. |
| `keycloak`    | `volumeSize`       | **Optional**. Size of persistent volume claim for Keycloak. Default is `1Gi`. Not used if not persistent install asked. |
| `keycloak`    | `postgresImage`    | **Optional**. The reference of container image used. Chart comes with its default version. |
| `mongodb`     | `install`          | **Optional**. Flag for MongoDB installation. Default is `true`. Set to `false` if you want to reuse an existing MongoDB instance. |
| `mongodb`     | `uri`              | **Optional**. MongoDB URI in case you're reusing existing MongoDB instance. Mandatory if `install` is `false` |
| `mongodb`     | `database`         | **Optional**. MongoDB database name in case you're reusing existing MongoDB instance. Used if `install` is `false`. Default to `appName` |
| `mongodb`     | `secretRef`        | **Optional**. Reference of a Secret containing credentials for connecting a provided MongoDB instance. Mandatory if `install` is `false` |
| `mongodb`     | `persistent`       | **Optional**. Flag for MongoDB persistence. Default is `true`. Set to `false` if you want an ephemeral MongoDB installation. |
| `mongodb`     | `volumeSize`       | **Optional**. Size of persistent volume claim for MongoDB. Default is `2Gi`. Not used if not persistent install asked. |
| `features`    | `repositoryFilter` | **Optional**. Feature allowing to filter API and services on main page. Must be explicitely `enabled`. See [Organizing repository](https://microcks.io/documentation/using/advanced/organizing/#master-level-filter) for more informations |


You may want to launch custom installation with such a command:
 
 ```console
 $ helm install microcks ./microcks --namespace=microcks \
    --set appName=mocks --set mongodb.volumeSize=5Gi \
    --set microcks.url=mocks-microcks.apps.example.com \
    --set keycloak.url=keycloak-microcks.apps.example.com
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