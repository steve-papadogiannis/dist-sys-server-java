# Directions Map Reduce Server # 
[![Build Status](https://travis-ci.com/steve-papadogiannis/dist-sys-server-java.svg?branch=master)](https://travis-ci.com/steve-papadogiannis/dist-sys-server-java)
[![Known Vulnerabilities](https://snyk.io/test/github/steve-papadogiannis/dist-sys-server-java/badge.svg?targetFile=pom.xml)](https://snyk.io/test/github/steve-papadogiannis/dist-sys-server-java?targetFile=pom.xml)

A small project that takes directions queries and produces directions results in polynomial representation.

A deployment setup would be one `server`, four `mappers` and one `reducer`.
`Server` handles the incoming queries and delegates them to internal `master` worker.
`Master` can handle the query in three `layers`. If the queried directions have a pair
of `GeoPoints` as start and end that are already known to the system, the result is fetched
by the `L1` internal cache. Otherwise, if the cache misses then the query is delegated to
the workers in order to produce a result from other results already known to the system, 
stored in a `Mongo` database, using `map-reduce` strategy. That is the `L2` part. Finally,
if aforementioned layers cannot produce the desired outcome, query is delegated to`L3` which
performs the query to `Google Maps Services API` in order to produce the result. With the 
result of the `API` both `L1` and `L2` are updated.

## Versions ##

* JDK: 1.8.0_251
* Maven: 3.6.3
* Mongo: 4.4.4
* google-maps-services SDK: 0.1.20 (modified)

## Sequence Diagram ##

![Lifecycle Sequence Diagram](./images/lifecycle.svg)

## Build ##

Below commands should be issued in project directory:

_api.key_ = The production Directions API key produced at Google Developers Console.

_test.api.key_ = The test Directions API key produced at Google Developers Console.

Clean:

```
 mvn clean -Dapi.key=api.key -Dtest.api.key=test.api.key -B -V
```

Validate:

```
mvn validate -Dapi.key=api.key -Dtest.api.key=test.api.key -B -V
```

Install:

```
mvn install -Dapi.key=api.key -Dtest.api.key=test.api.key -DskipTests=true -Dmaven.javadoc.skip=true -B -V
```

## Run ##

Below commands should be issued in project directory:

Run Reducer:

```
 java -classpath "target/dist-sys-server-java-1.0.0-shaded.jar" gr.papadogiannis.stefanos.reducers.impl.ReduceWorkerImpl 5559
```

Run Mapper #1:

```
java -classpath "target/dist-sys-server-java-1.0.0-shaded.jar" gr.papadogiannis.stefanos.mappers.impl.MapWorkerImpl 5555 localhost 5559
```

Run Mapper #2:

```
java -classpath "target/dist-sys-server-java-1.0.0-shaded.jar" gr.papadogiannis.stefanos.mappers.impl.MapWorkerImpl 5556 localhost 5559
```

Run Mapper #3:

```
java -classpath "target/dist-sys-server-java-1.0.0-shaded.jar" gr.papadogiannis.stefanos.mappers.impl.MapWorkerImpl 5557 localhost 5559
```

Run Mapper #4:

```
java -classpath "target/dist-sys-server-java-1.0.0-shaded.jar" gr.papadogiannis.stefanos.mappers.impl.MapWorkerImpl 5558 localhost 5559
```

Run Server:

```
java -classpath "target/dist-sys-server-java-1.0.0-shaded.jar" gr.papadogiannis.stefanos.servers.Server 8080 localhost 5555 localhost 5556 localhost 5557 localhost 5558 localhost 5559
```