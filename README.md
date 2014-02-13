## Gradle Mongo Plugin ##

The Gradle Mongo Plugin allows you to run a managed instance of Mongo from your gradle build. It is based on the [Embedded MongoDb](https://github.com/flapdoodle-oss/de.flapdoodle.embed.mongo) project by Flapdoodle OSS.

Plugin is available on [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22gradle-mongo-plugin%22). Nightly builds are available from the [Sonatype OSS Repo](https://oss.sonatype.org/content/repositories/snapshots/com/sourcemuse/gradle/plugin/gradle-mongo-plugin/).

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
