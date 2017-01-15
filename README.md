## Gradle Mongo Plugin ##

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.sourcemuse.gradle.plugin/gradle-mongo-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.sourcemuse.gradle.plugin/gradle-mongo-plugin)

The Gradle Mongo Plugin allows you to run a managed instance of Mongo from your gradle build. It is based on the [Embedded MongoDb](https://github.com/flapdoodle-oss/de.flapdoodle.embed.mongo) project by Flapdoodle OSS.

Plugin is available on both [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22gradle-mongo-plugin%22) and the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/com.sourcemuse.mongo). Nightly builds are available from the [Sonatype OSS Repo](https://oss.sonatype.org/content/repositories/snapshots/com/sourcemuse/gradle/plugin/gradle-mongo-plugin/).

### Usage ###

Enable the plugin in your gradle build:

```groovy
plugins {
  id "com.sourcemuse.mongo" version "1.0.0"
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

* ```artifactStorePath``` The location where Mongo will be downloaded to
* ```bindIp```: The ip address Mongo binds itself to (defaults to **'127.0.0.1'**)
* ```downloadUrl```: The URL from where Mongo will be downloaded
* ```journalingEnabled```: Toggles journaling (defaults to **false**)
* ```logFilePath```: The desired log file path (defaults to **'embedded-mongo.log'**)
* ```logging```: The type of logging to be produced: **'console'**, **'file'** or **'none'** (defaults to **'file'**)
* ```mongodVerbosity```: The verbosity level of the mongod process. Supported options are as per the [mongod configuration documentation](http://docs.mongodb.org/manual/reference/program/mongod/#cmdoption--verbose) (default level is non-verbose)
* ```mongoVersion```: The version of Mongo to run. Can be **'DEVELOPMENT'** or **'PRODUCTION'** for the latest versions of the respective branch, or take the form **'1.2-LATEST'** or **'1.2.3'** for specific versions
* ```port```: The port Mongo will listen on (defaults to **27017**). For random port assignment, set this value to **'RANDOM'** (the actual port value used will be available during the build through the **project.mongo.port** property)
* ```proxyHost```: The proxy host name for Mongo downloads
* ```proxyPort```: The proxy port for Mongo downloads
* ```storageLocation```: The directory location from where embedded Mongo will run, such as ```/tmp/storage``` (defaults to a java temp directory)

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
