# OpenTelemetry observability on Microcks instance

This folder provides resources for configuring OpenTelemetry support in Microcks and running an observability stack on your
local development machine.

## OpenTelemetry configuration

In order to enable OpenTelemetry on your Microcks instance, you'll have to define those 1 environment variables:

* `OTEL_EXPORTER_OTLP_ENDPOINT` is by default set to a local dummy endpoint, so you'll have to set it to an OpenTelemetry collector endpoint
  of your environment. Something like `http://otel-collector.acme.com:4317` for example.

Depending on the content your want to sent to this OpenTelemetry collector configuration, you may also need to set:

* `OTEL_METRICS_EXPORTER` set to `otlp` - it is set to `none` by default and will not export any metric if not set
* `OTEL_TRACES_EXPORTER` set to `otlp` - it is set to `none` by default and will not export any trace if not set
* `OTEL_LOGS_EXPORTER` set to `otlp` - it is set to `none` by default and will not export any log if not set

Optionally, you may override this following environment variables to fit your environment requirements:

* `OTEL_METRIC_EXPORT_INTERVAL`, those default value is set to `10000`
* `OTEL_EXPORTER_OTLP_TIMEOUT`, those default value is set to `10000` 
* `OTEL_RESOURCE_ATTRIBUTES`, those default value is set to `service.name=microcks,service.namespace=microcks-ns,service.instance.id=my-microcks-dev,service.version=1.13.0`
* `MANAGEMENT_OLTP_TRACING_SAMPLING_PROBABILITY`, those default value is set to `0.2` (meaning 20% of regular API requests are sampled)

The above environment variables may be easily setup via the Microcks Helm Chart or Operator using the `microcks.env` properties.

## Grafana dashboard

You can find a Grafana dashboard for Microcks in the `./dashboards/microcks-overview.json` file. You'll get here detailed performance
view on Microcks mock endpoints, as well as logs, as well as distributed tracing.

## Want to generate some load?

Check our [benchmark](../benchmark/README.md) folder in this GitHub repository.

## Running the observability stack locally

We provide everything so that you can easily test and try the observability features locally!

Open a terminal and use our `start-o11y-dev-stack.sh` shell script like below:

```shell
$ ./start-o11y-dev-stack.sh
--- OUTPUT ---
Setting up dedicated network bridge..
cabc290d6b9f9e23569103f17ec4201407f099695016486cd332663b27b082dc
✅ o11y docker network created !
e88eb45e85894f13cae717c090940dcc17fc65a65271a9fb22a4f7c6790ea0c8
393fa7f135cecba4a1b2d64cb75d0cc319c595a2bffcd8944223db9fdaf9b3fd
ad09df1e2fd225cbbddf0619ef99ab7de9c6501b916f3cc220d297d000d731d9
1bba5d36bc885fc6c89587ac260fb839a294d1f7ee71884392e247bf1a185a65
b464ab2682b2398f56ba84cc9dc559afca794153b6aee45481600b89d76d076f
Check state of the stack !
✅ Prometheus OK
✅ Grafana OK
✅ OpenTelemetryCollector OK
✅ Tempo OK
✅ Loki OK
Finished !
```

This will start a bunch of containers providing the OpenTelemetry services:

```shell
$ docker ps
--- OUTPUT ---
CONTAINER ID   IMAGE                                         COMMAND                  CREATED          STATUS          PORTS                                                               NAMES
b464ab2682b2   grafana/grafana:10.2.3                        "/run.sh"                22 minutes ago   Up 22 minutes   0.0.0.0:3000->3000/tcp                                              o11y_grafana
1bba5d36bc88   otel/opentelemetry-collector-contrib:0.92.0   "/otelcol-contrib --…"   22 minutes ago   Up 22 minutes   0.0.0.0:4317->4317/tcp, 0.0.0.0:13133->13133/tcp, 55678-55679/tcp   o11y_otel
ad09df1e2fd2   grafana/tempo:2.3.0                           "/tempo -config.file…"   22 minutes ago   Up 22 minutes   0.0.0.0:3200->3200/tcp                                              o11y_tempo
393fa7f135ce   grafana/loki:2.9.3                            "/usr/bin/loki -conf…"   22 minutes ago   Up 22 minutes   0.0.0.0:3100->3100/tcp                                              o11y_loki
e88eb45e8589   prom/prometheus:v2.48.1                       "/bin/prometheus --c…"   22 minutes ago   Up 22 minutes   0.0.0.0:9080->9090/tcp
```

You can now open Grafana, loaded with a Microcks dashboard into your browser at `http://localhost:3000`.

> For people running Microcks locally using docker-compose or other, you'll need to add the extra environment variables introduced above
and set `OTEL_EXPORTER_OTLP_ENDPOINT` to `http://host.docker.internal:4317` to access the OpenTelemetry collector from within the docker network.

> For Microcks developers: enabling the OpenTelemetry features is accessible via a Maven profile. Just run `mvn -Pdev-otel spring-boot:run`
and Microcks is started and configured to use this local OpenTelemetry stack.

To stop all the observability-related containers and free resources on your machine, you can simply execute our `teardown-o11y-dev-stack.sh`:

```shell
$ ./teardown-o11y-dev-stack.sh
--- OUTPUT ---
o11y_prom
o11y_loki
o11y_tempo
o11y_grafana
o11y_otel
o11y
```