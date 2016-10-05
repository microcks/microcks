# microcks

## Build Status

Current development version is `0.0.1-SNAPSHOT`. [![Build Status](https://travis-ci.org/microcks/microcks.png?branch=master)](https://travis-ci.org/microcks/microcks)

## Installation

## Development

For development purposes, frontend GUI and backend APIs have been separated and runs onto 2 different runtime servers.
* Frontend is an AngularJS application served by Grunt server with livereload enabled,
* Backend is a Spring Boot application served by Boot internal server

### Pre-requisites

* NodeJS (version >= 4.0) and associated tools : NPM, Bower and Grunt-cli
* Java Development Kit (version 8) and Apache Maven (version >= 3.0)
* MongoDB 3.2

### Start servers

In a terminal, start frontend GUI server using Grunt :

```
$ grunt serve
```

Server is started on port `9000`. Grunt should open a new browser tab pointing to `http://localhost:9000` where application s hosted.

```
$ mvn spring-boot:run
```

Server is started on port `8080` and will be used as API endpoints root by frontend GUI (URLs starting by `http://localhost:9000/api` will be indeed proxied to port `8080`).
