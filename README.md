# microcks

## Build Status

Current development version is `0.9.0-SNAPSHOT`. [![Build Status](https://travis-ci.org/microcks/microcks.png?branch=master)](https://travis-ci.org/microcks/microcks)

## Developer Workspace

[![Contribute](https://che.openshift.io/factory/resources/factory-contribute.svg)](http://che-eclipse-che.apps.laurent.openhybridcloud.io/f?id=factoryfp1e8mfo0olbe90v)

## Installation

## Development

For development purposes, frontend GUI and backend APIs have been separated and runs onto 2 different runtime servers.
* Frontend is an Angular 6 application served by `ng serve` with livereload enabled,
* Backend is a Spring Boot application served by Boot internal server

We also need a Keycloak server running on port `8180`. 

### Pre-requisites

* NodeJS (version >= 8.0) and associated tools : NPM and ng-cli (`npm install -g ng-cli`)
* Java Development Kit (version >= 8) and Apache Maven (version >= 3.0)
* Keycloak 4.8.0
* MongoDB 3.4

### Start servers

Let's begin with starting the Keycloak server. Within the installation directory of Keycloak 3.4.0, just run this command:

```
$ cd bin
$ ./standalone.sh -Djboss.socket.binding.port-offset=100
```

In a terminal, start frontend GUI server using NG :

```
$ cd src/main/webapp
$ ng serve
```

Server is started on port `4200`. Open a new browser tab pointing to `http://localhost:4200` where application is hosted.

```
$ mvn spring-boot:run
```

Server is started on port `8080` and will be used as API endpoints root by frontend GUI (URLs starting by `http://localhost:9000/api` will be indeed proxied to port `8080`).

## Build binaries

### Build and run Fat jar

For now, there's still a problem with Frontend integration tests configuration so you should disable them using the following flag:
 
```
$ mvn -Pprod package
```

```
$ java -jar target/microcks-0.9.0-SNAPSHOT.jar
```

### Build and run Docker image

```
$ mvn -Pprod clean package docker:build
```

```
$ docker-compose -f microcks-mongodb.yml up -d
```
