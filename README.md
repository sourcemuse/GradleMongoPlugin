## Gradle Mongo Plugin ##

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.sourcemuse.gradle.plugin/gradle-mongo-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.sourcemuse.gradle.plugin/gradle-mongo-plugin)
[![Build Status](https://travis-ci.org/sourcemuse/GradleMongoPlugin.svg?branch=master)](https://travis-ci.org/sourcemuse/GradleMongoPlugin)

The Gradle Mongo Plugin allows you to run a managed instance of Mongo from your gradle build. 

It's available on both [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22gradle-mongo-plugin%22) and the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/com.sourcemuse.mongo).

### Usage ###

Enable the plugin in your gradle build:

```groovy
plugins {
  id 'com.sourcemuse.mongo' version '1.0.6'
}
```

Hey presto, you can now declare a dependency on a running mongo instance from any of your tasks:

```groovy
task integrationTest(type: Test) {
    runWithMongoDb = true
}

```

### Configuration ###

Configure your Mongo instances inside a ```mongo``` block:

```
mongo {
    port 12345
    logging 'console'
    ...
}
```

The `mongo` configuration block can be declared at either the project or the task level. Task-level configuration inherits from any project-level configuration provided.

The following properties are configurable:

* ```args```: A <String,String> map of mongod command-line options (value can be empty for arguments without values) (defaults to **[:]**)
* ```artifactStorePath``` The location where Mongo will be downloaded to
* ```auth```: Enables [access control](https://docs.mongodb.com/manual/tutorial/enable-authentication/) (defaults to **false**)
* ```bindIp```: The ip address Mongo binds itself to (defaults to **'127.0.0.1'**)
* ```downloadUrl```: The URL from where Mongo will be downloaded
* ```journalingEnabled```: Toggles journaling (defaults to **false**)
* ```logFilePath```: The desired log file path (defaults to **'embedded-mongo.log'**)
* ```logging```: The type of logging to be produced: **'console'**, **'file'** or **'none'** (defaults to **'file'**)
* ```mongodVerbosity```: The verbosity level of the mongod process. Supported options are as per the [mongod configuration documentation](http://docs.mongodb.org/manual/reference/program/mongod/#cmdoption-verbose) (default level is non-verbose)
* ```mongoVersion```: The version of Mongo to run. Can be **'DEVELOPMENT'** or **'PRODUCTION'** for the latest versions of the respective branch, or take the form **'1.2-LATEST'** or **'1.2.3'** for specific versions
* ```params```: A <String,String> map of [MongoDB Server Parameters](https://docs.mongodb.com/manual/reference/parameters/) (defaults to **[:]**)
* ```port```: The port Mongo will listen on (defaults to **27017**). For random port assignment, set this value to **'RANDOM'** (the actual port value used will be available during the build through the **project.mongo.port** property)
* ```proxyHost```: The proxy host name for Mongo downloads
* ```proxyPort```: The proxy port for Mongo downloads
* ```storageEngine```: The name of the storage engine to use. Can be **'wiredTiger'** or **'mmapv1'** for MongoDB Community Edition (default is **'wiredTiger'** for Mongo 3.2 and later; otherwise it is **'mmapv1'**). Alternative distributions might support additional engines
* ```storageLocation```: The directory location from where embedded Mongo will run, such as ```/tmp/storage``` (defaults to a java temp directory)
* ```syncDelay```: The interval in seconds between fsync operations where mongod flushes its working memory to disk. See [syncdelay parameter](https://docs.mongodb.com/manual/reference/parameters/#param.syncdelay) for more information

### Tasks ###

For your convenience the plugin also adds the following tasks to your buildscript:

```
$ gradle tasks
...
Mongo tasks
-----------
startManagedMongoDb - Starts a local MongoDb instance which will stop when the build process completes
startMongoDb - Starts a local MongoDb instance
stopMongoDb - Stops the local MongoDb instance
...
```
