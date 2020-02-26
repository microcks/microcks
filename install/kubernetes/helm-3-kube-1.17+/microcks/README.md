# Microcks

This chart bootstraps a new [Microcks](http://microcks.github.io) application using the [Helm](https://helm.sh) package manager.

Resources within this directory are intended to work with Helm version 3+ (which do not need the Tiller server-side component).

## Installing the Chart

```console
$ cd install/kubernetes/helm-3-kube-1.17+
$ helm install microcks ./microcks --namespace microcks
```

## Configuration
All configurable variables can be seen in `values.yaml`.

Typically, you may want to configure the following options:
* `appName` give all your components a common prefix as well as a Kubernetes `app` label value, 
* `microcksHost` allows to configure the Host that will be use for `Ingress` definition and thus later accepting users,
* `keycloakHost` allows to configure the Host that will allow access to Keycloak instance,
* `persistent.enable` flag make usage of `PersistenceVolumeClaim` when set o true (default is false).

You may want to launch custom installation with such a command:
 
 ```console
 $ helm install microcks ./microcks --namespace=microcks \
    --set appName=mocks --set persistent.enable=true \
    --set microcksHost=mocks-microcks.apps.example.com \
    --set keycloakHost=keycloak-microcks.apps.example.com
 ```

## Deleting the Chart

```console
$ helm delete microcks
$ helm del --purge microcks
```