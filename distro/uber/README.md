This is an all-in-one distribution of Microcks that disabled Keycloak need and removed dependency to external MongoDB server. 

For that we're using an embedded MongoDB-protocol compatible Java server that is started conditionally when Spring `uber` profile is active.
That MongoDB Java server is able to run in two different modes:
* Ephemeral in-memory persistence that will not survive a JVM restart (that is the default mode),
* File persistence that will survive restarts.

## In-memory mode

Just start the server using `mvn spring-boot:run` command:

```shell
$ mvn spring-boot:run 
====== OUTPUT ======
[INFO] Attaching agents: []

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.1.1)

11:51:41.518  INFO 10712 --- [      main] i.g.microcks.MicrocksApplication         : Starting MicrocksApplication using Java 17.0.6 with PID 10712 (/Users/laurent/Development/repository/io/github/microcks/microcks-app/1.8.0-SNAPSHOT/microcks-app-1.8.0-SNAPSHOT.jar started by laurent in /Users/laurent/Development/github/microcks/distro/uber)
11:51:41.520 DEBUG 10712 --- [      main] i.g.microcks.MicrocksApplication         : Running with Spring Boot v3.1.1, Spring v6.0.10
11:51:41.520  INFO 10712 --- [      main] i.g.microcks.MicrocksApplication         : The following 1 profile is active: "uber"
11:51:42.363  INFO 10712 --- [      main] i.g.microcks.config.WebConfiguration     : Starting web application configuration, using profiles: [uber]
11:51:42.364  INFO 10712 --- [      main] i.g.microcks.config.WebConfiguration     : Web application fully configured
11:51:42.393  INFO 10712 --- [      main] i.g.m.c.EmbeddedMongoConfiguration       : Creating a new embedded Mongo Java Server with in-memory persistence
11:51:42.474  INFO 10712 --- [      main] de.bwaldvogel.mongo.MongoServer          : started MongoServer(port: 50935, ssl: false)
[...]
11:51:43.177  INFO 10712 --- [      main] i.g.microcks.MicrocksApplication         : Started MicrocksApplication in 1.798 seconds (process running for 2.08)
```

You can see in above logs that `The following 1 profile is active: "uber"` and that is creates a new `embedded Mongo Java Server with in-memory persistence``

## Persistent mode

Persistent file location is controlled via the `mongodb.storage.file` configuration in `application.properties` file,
but you can just set its value using the `MONGODB_STORAGE_PATH` environment variable.

Use this one-line command below:

```shell
$ MONGODB_STORAGE_PATH=target/microcks.mv mvn spring-boot:run 
====== OUTPUT ======
[INFO] Attaching agents: []

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.1.1)

11:53:32.292  INFO 10974 --- [      main] i.g.microcks.MicrocksApplication         : Starting MicrocksApplication using Java 17.0.6 with PID 10974 (/Users/laurent/Development/repository/io/github/microcks/microcks-app/1.8.0-SNAPSHOT/microcks-app-1.8.0-SNAPSHOT.jar started by laurent in /Users/laurent/Development/github/microcks/distro/uber)
11:53:32.294 DEBUG 10974 --- [      main] i.g.microcks.MicrocksApplication         : Running with Spring Boot v3.1.1, Spring v6.0.10
11:53:32.294  INFO 10974 --- [      main] i.g.microcks.MicrocksApplication         : The following 1 profile is active: "uber"
11:53:33.151  INFO 10974 --- [      main] i.g.microcks.config.WebConfiguration     : Starting web application configuration, using profiles: [uber]
11:53:33.152  INFO 10974 --- [      main] i.g.microcks.config.WebConfiguration     : Web application fully configured
11:53:33.172  INFO 10974 --- [      main] i.g.m.c.EmbeddedMongoConfiguration       : Creating a new embedded Mongo Java Server with disk persistence at target/microcks.mv
11:53:33.176  INFO 10974 --- [      main] d.b.mongo.backend.h2.H2Backend           : opening MVStore in 'target/microcks.mv'
11:53:33.273  INFO 10974 --- [      main] de.bwaldvogel.mongo.MongoServer          : started MongoServer(port: 51430, ssl: false)
[...]
11:53:33.946  INFO 10974 --- [      main] i.g.microcks.MicrocksApplication         : Started MicrocksApplication in 1.793 seconds (process running for 2.073)
```

You can see in above logs that `The following 1 profile is active: "uber"` and that is creates a new `embedded Mongo Java Server with disk persistence at target/microcks.mv`.