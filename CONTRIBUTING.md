# Contributing guide

**Want to contribute? Great!** We try to make it easy, and all contributions, even the smaller ones, are more than welcome. This includes bug reports, fixes, documentation, examples... 
But first, read this page.

## Reporting an issue

This project uses GitHub issues to manage the issues. Open an issue directly in GitHub.

If you believe you found a bug, and it's likely possible, please indicate a way to reproduce it, what you are seeing and what you would expect to see.
Don't forget to indicate your Java, Maven and/or Docker version.

## Setup

## Build

### Build the whole project

```
$ git clone https://github.com/microcks/microcks.git
[...]
$ cd microcks
$ mvn clean install
[...] 
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary for Microcks 1.0.0-SNAPSHOT:
[INFO] 
[INFO] Microcks ........................................... SUCCESS [  0.234 s]
[INFO] Microcks Model ..................................... SUCCESS [  1.602 s]
[INFO] Microcks EL ........................................ SUCCESS [  1.907 s]
[INFO] Microcks App ....................................... SUCCESS [ 28.038 s]
[INFO] Microcks Async Minion .............................. SUCCESS [  8.007 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  40.036 s
[INFO] Finished at: 2020-06-12T09:05:45+02:00
[INFO] ------------------------------------------------------------------------
```

### Build and run webapp jar

For now, there's still a problem with Frontend integration tests configuration so you should disable them using the following flag:
 
```
$ cd webapp
$ mvn -Pprod package
[...]
$ java -jar target/microcks-x.y.z-SNAPSHOT.jar
```

### Build and run webapp Docker image

```
$ cd webapp
$ mvn -Pprod clean package docker:build
[...]
$ cd ../install/docker-compose
$ docker-compose -f docker-compose.yml up -d
```
After spinning up the containers, you will now have access to Keycloak for account management, and microcks webapp to setup mocking, etc.

You can login to keycloak on `http://localhost:18080/` with username and password `admin`.
You can login to microcks webapp with the username `admin` and password `microcks123`.

## Before you contribute

To contribute, use GitHub Pull Requests, from your **own** fork.
