## Setup

For development purposes, frontend GUI and backend APIs have been separated and runs onto 2 different runtime servers.
* Frontend is an Angular 8 application served by `ng serve` with livereload enabled,
* Backend is a Spring Boot application served by Boot internal server

To run the dependencies we rely on ([MongoDB](https://mongodb.com) and [Keycloak](https://keycloak.org))
we recommend using [Docker](https://www.docker.com/). We provide some useful scripts in the `/dev` folder of the
repository root that pull the correct versions and auto-configure them.

### Pre-requisites

* NodeJS (version >= 16.0) and associated tools : NPM and ng-cli (`npm i -g @angular/cli`)
* Java Development Kit (version >= 17) and [Apache Maven](https://maven.apache.org) (version >= 3.5)

### Start dependencies

If you chose to run [MongoDB](https://mongodb.com) and [Keycloak](https://keycloak.org) via containers, you'll
need to open a first terminal and run:

```shell
$ cd dev
$ ./start-mongodb-docker.sh
# or ./start-mongodb-podman.sh if you prefer Podman
```

MongoDB is started on port `27017`.

Keycloak is optional depending on your will to try out authentication and authorization features.
If you need Keycloak, open a second terminal and run:

```shell
$ cd dev
$ ./start-keycloak.sh
# or ./start-keycloak-podman.sh if you prefer Podman
```

Keycloak is started on port `8180`.

### Start servers

In a terminal, start frontend GUI server using NG :

```shell
$ cd src/main/webapp
$ npm install --legacy-peer-deps
$ ng serve
```

Server is started on port `4200`. Open a new browser tab pointing to `http://localhost:4200` where application is hosted.

with Keycloak:

```shell
$ mvn spring-boot:run
```

with Keycloak disabled:

```shell
$ KEYCLOAK_ENABLED=false mvn spring-boot:run
```

Server is started on port `8080` and will be used as API endpoints root by frontend GUI (URLs starting by `http://localhost:4200/api` will be in fact proxied to port `8080`).

### Formatting and linting

We use [Spotless](https://github.com/diffplug/spotless) to format our Java code. Spotless is configured using an Eclipse 
code style file, which is located at `eclipse-formatter.xml` in the root folder of this repository. Spotless check is automatically
triggered during the build process, but you can also run it manually using the following command:

```shell
$ mvn spotless:apply
```