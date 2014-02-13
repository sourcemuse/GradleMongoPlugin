## Gradle Mongo Plugin ##

The Gradle Mongo Plugin allows you to run a managed instance of Mongo from your gradle build. It is based on the [Embedded MongoDb](https://github.com/flapdoodle-oss/de.flapdoodle.embed.mongo) project by Flapdoodle OSS.

### Usage ###

Enable the plugin in your gradle build by adding the jar to your buildscript dependencies:

```
buildscript {
   repositories {
      mavenCentral()
   }
   dependencies {
      classpath 'com.sourcemuse.gradle.plugin:gradle-mongo-plugin:0.1.0'
   }
}

apply plugin: 'mongo'
```

Hey presto...

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
