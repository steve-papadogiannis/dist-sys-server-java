# Directions Map Reduce Server # 
[![Build Status](https://travis-ci.com/steve-papadogiannis/dist-sys-server-java.svg?branch=master)](https://travis-ci.com/steve-papadogiannis/dist-sys-server-java)
[![Known Vulnerabilities](https://snyk.io/test/github/steve-papadogiannis/dist-sys-server-java/badge.svg?targetFile=pom.xml)](https://snyk.io/test/github/steve-papadogiannis/dist-sys-server-java?targetFile=pom.xml)

A small project that takes directions queries and produces directions results in polynomial representation.

## Versions ##

* JDK: 1.8.0_251
* Maven: 3.6.3
* Mongo: 4.4.4
* google-maps-services SDK: 0.1.20 (modified)

## Sequence Diagram ##

![Lifecycle Sequence Diagram](./images/lifecycle.svg)

## Build ##

Below commands should be issued in project directory:

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

Run Mapper:

```
java -classpath "target/dist-sys-server-java-1.0.0-shaded.jar" gr.papadogiannis.stefanos.mappers.impl.MapWorkerImpl 5555 localhost 5559
```

Run Server:

```
java -classpath "target/dist-sys-server-java-1.0.0-shaded.jar" gr.papadogiannis.stefanos.servers.Server 8080 localhost 5555 localhost 5559
```