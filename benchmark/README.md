# K6 load testing on Microcks instance

This benchmark tool provides you an easy way of validating/sizing/evaluating changes on your Microcks instance.
It allows you to simulate Virtual Users on different usage scenarios and gather performance metrics of your instance.

1. Start you Microck instance 
2. Import MicrocksIO Samples APIs / Pastry API - 2.0
3. Import MicrocksIO Samples APIs / Movie Graph API
4. Import MicrocksIO Samples APIs / HelloService Mock
5. Import any other mock if you want to have more APIs to browse

## Settings that may impact your Microcks instance performance

Please carefully consider those settings that may have a direct impact on your Microcks instance:

* Instance sizing - when deployed on Kubernetes, check the `requests.cpu`, `limits.cpu`, `requests.memory` and `requests.memory` settings,
* Enabling/Disabling invocation statistics - by default, Microcks has a `mocks.enable-invocation-stats` configuration property that is set to `true`.
This can cause an overhead even if the processing is asynchronous,
* MongoDB database indexes - Microcks comes with no index by default, but we put some recommendations in [mongodb-indexes](./mongodb-indexes.md),
* Logging verbosity - moving from `DEBUG` to `INFO` or above levels may have a significant impact.

## Required environment variables

```sh
export MICROCKS_BASE_URL=http://172.31.243.54:8080
export K6_VERSION=0.48.0
export PROMETHEUS_RW_URL=http://172.31.243.54:9080/api/v1/write    # Optional     
```

If you're running Microcks locally (via docker-compose or other), you may use these ones:

```sh
export MICROCKS_BASE_URL=http://host.docker.internal:8080
export K6_VERSION=0.48.0
```

## Simple script execution

The command below launches a test script that last between 1 and 2 minutes depending on your instance:

```sh
docker run --rm -i \
    -e BASE_URL=${MICROCKS_BASE_URL} \
    grafana/k6:${K6_VERSION} run \
    - < bench-microcks.js
```

Behind the scenes, it executes 4 different scenarios that simulates different activities:
* `browse` simulates Virtual Users that browse the Microcks API repository,
* `invokeRESTMocks` simulates VU/apps that invoke a bunch of mock REST endpoint of our **Pastry API - 2.0** sample,
* `invokeGraphQLMocks` simulates VU/apps that invoke a bunch of mock GraphQL endpoint of our **Movie Graph API** sample,
* `invokeSOAPMocks` simulates VU/apps that invoke a bunch of mock SOAP endpoint of our **HelloService Mock** sample.


## Override wait time & scenarios

The `browse` scenario has a `WAIT_TIME` configuration that simulates pause time between user interaction.
The default value is `0.5` but you can customize it to suit your needs:

```sh
docker run --rm -i \
    -e BASE_URL=${MICROCKS_BASE_URL} \
    -e WAIT_TIME=0.2 \
    grafana/k6:${K6_VERSION} run \
    - < bench-microcks.js
```

More over you can configure the number of Virtual Users and iteration for each scenario. That way, you can
use this benchmark to be truly representative to your API patrimony and expected load. Just edit the `bench-microcks.js`
and check the `scenarios` section:

```json
[...]
  scenarios: {
    browse: {
      [...]
    },
    invokeRESTMocks: {
      executor: 'per-vu-iterations',
      exec: 'invokeRESTMocks',
      vus: 40,
      iterations: 200,
      startTime: '5s',
      maxDuration: '2m',
    },
    invokeGraphQLMocks: {
      [...]
    },
    invokeSOAPMocks: {
      [...]
    },
  }
[...]
```

## Export metrics to prometheus endpoint

The K6 scripts results may be exported to a running Prometheus instance like this:

```sh
docker run --rm -i \
    -e K6_PROMETHEUS_RW_SERVER_URL=${PROMETHEUS_RW_URL} \
    -e K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99),min,max" \
    -e BASE_URL=${MICROCKS_BASE_URL} \
    grafana/k6:${K6_VERSION} run  \
    --tag testid=$(date -u +"%Y-%m-%dT%H:%M:%SZ") \
    -o experimental-prometheus-rw \
    - < bench-microcks.js
```
