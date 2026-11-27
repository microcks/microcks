From a certain size of MongoDB, it can be interesting to add some indexes to your database. 

Here are below the indexes of interest with the commands on how to create them in your database:

```shell 
db.dailyStatistic.createIndex( {day: -1, serviceName: 1, serviceVersion: -1}, {name: "day-1_serviceName1_serviceVersion-1"} );
db.eventMessage.createIndex( {operationId: -1}, {name: "operationId-1"} );
db.eventMessage.createIndex( {testCaseId: -1}, {name: "testCaseId-1"} );
db.request.createIndex( {operationId: -1}, {name: "operationId-1"} );
db.request.createIndex( {testCaseId: -1}, {name: "testCaseId-1"} );
db.response.createIndex( {operationId: -1}, {name: "operationId-1"} );
db.response.createIndex( {testCaseId: -1}, {name: "testCaseId-1"} );
db.testResult.createIndex( {serviceId: -1}, {name: "serviceId-1"} );
db.testConformanceMetric.createIndex( {serviceId: -1}, {name: "serviceId-1"} );
```

And how to drop them if you find they are not efficient enough:

```shell
db.dailyStatistic.dropIndex("day-1_serviceName1_serviceVersion-1");
db.eventMessage.dropIndex("operationId-1");
db.eventMessage.dropIndex("testCaseId-1");
db.request.dropIndex("operationId-1");
db.request.dropIndex("testCaseId-1");
db.response.dropIndex("operationId-1");
db.response.dropIndex("testCaseId-1");
db.testResult.dropIndex("serviceId-1");
db.testConformanceMetric.dropIndex("serviceId-1");
```