# Tested Configurations

This document lists the configurations that the project team is using to develop and test the different versions of Microcks.
It includes details about the versions of external dependencies, runtime environments, or any specific configurations that were 
used during integration development and testing.

> 💡 This is _not an exhaustive list of all possible configurations_ that Microcks can work with, but **rather a reference point 
for users and contributors** to understand the environments in which it has been validated.

There are other configurations that work well with Microcks. Look at [community compatibility matrix](https://github.com/microcks/community/blob/main/install/COMPATIBILITY-MATRIX.md)
for more working configurations. If you have a running configuration, please consider sharing it with the community by opening
a pull request against the community file 🙏


## Microcks 1.13.x

### Dependencies

* **Microcks Postman Runtime**: 0.7.0
* **MongoDB**: 4.4.29 - wire protocol version 8 as the minimum
* **Keycloak**: 26.0.0

### Runtime Environments

* **Minikube**: v1.32.0 with Kubernetes v1.28.3, v1.36.0 with Kubernetes v1.32.1, v1.37.0 with Kubernetes v1.34.0
* **Kind**: v0.25.0 with Kubernetes v1.28.3

### Integrations

* `microcks-cli`: 0.9.0
* **Jenkins**: v2.462.3 with `microcks-jenkins-plugin` 0.6.0
* **Backstage**: 1.33.0 with `microcks-backstage-provider` 0.0.6


## Microcks 1.12.x

### Dependencies

* **Microcks Postman Runtime**: 0.6.0
* **MongoDB**: 4.4.29 - wire protocol version 6 as the minimum
* **Keycloak**: 26.0.0

### Runtime Environments

* **Minikube**: v1.32.0 with Kubernetes v1.28.3
* **Kind**: v0.25.0 with Kubernetes v1.28.3

### Integrations

* `microcks-cli`: 0.9.0
* **Jenkins**: v2.462.3 with `microcks-jenkins-plugin` 0.6.0
* **Backstage**: 1.33.0 with `microcks-backstage-provider` 0.0.6