package com.sourcemuse.gradle.plugin

import de.flapdoodle.embed.mongo.distribution.Version
import org.gradle.testkit.functional.GradleRunnerFactory
import org.gradle.tooling.BuildException
import org.junit.Rule
import org.junit.rules.TemporaryFolder

import spock.lang.Issue;
import spock.lang.Specification

import static PluginForTests.TEST_START_MONGO_DB
import static com.sourcemuse.gradle.plugin.BuildScriptBuilder.DEFAULT_MONGOD_PORT
import static com.sourcemuse.gradle.plugin.MongoUtils.*

class MongoPluginConfigSpec extends Specification {

    def static final VERBOSE_LOGGING_SAMPLE = 'isMaster'

    @Rule TemporaryFolder tmp
    def gradleRunner = GradleRunnerFactory.create()
    def buildScript = new BuildScriptBuilder()

    def 'port is configurable'() {
        given:
        generate(buildScript.withPort(12345))
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        gradleRunner.run()
        def mongoRunningOnPort = mongoInstanceRunning(12345)

        then:
        mongoRunningOnPort
    }

    def 'logging can route to the console'() {
        given:
        generate(buildScript.withLogging('console'))
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        def executionResult = gradleRunner.run()

        then:
        executionResult.standardOutput.contains('[mongod output]')
    }

    def 'logging can be switched off'() {
        given:
        generate(buildScript.withLogging('none'))
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        def executionResult = gradleRunner.run()

        then:
        !executionResult.standardOutput.contains('[mongod output]')
    }

    def 'logging can be routed to a file'() {
        given:
        def tempFile = tmp.newFile()
        generate(buildScript.withLogging('file').withFilePath(tempFile.absolutePath))
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        def executionResult = gradleRunner.run()

        then:
        !executionResult.standardOutput.contains('[mongod output]')
        tempFile.text.contains('[mongod output]')
    }

    def 'general version is configurable'() {
        given:
        generate(buildScript.withMongoVersion('DEVELOPMENT'))
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        gradleRunner.run()
        def mongoVersion = getMongoVersionRunning(DEFAULT_MONGOD_PORT)

        then:
        Version.Main.DEVELOPMENT.asInDownloadPath().equalsIgnoreCase(mongoVersion)
    }

    @SuppressWarnings('GrDeprecatedAPIUsage')
    def 'specific version is configurable'() {
        given:
        generate(buildScript.withMongoVersion('2.5.4'))
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        gradleRunner.run()
        def mongoVersion = getMongoVersionRunning(DEFAULT_MONGOD_PORT)

        then:
        Version.V2_5_4.asInDownloadPath().equalsIgnoreCase(mongoVersion)
    }

    def 'latest branch version is configurable'() {
        given:
        generate(buildScript.withMongoVersion('2.4-LATEST'))
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        gradleRunner.run()
        def mongoVersion = getMongoVersionRunning(DEFAULT_MONGOD_PORT)

        then:
        Version.Main.V2_4.asInDownloadPath().equalsIgnoreCase(mongoVersion)
    }

    @Issue('https://github.com/sourcemuse/GradleMongoPlugin/issues/15')
    def 'unrecognized version is configurable'() {
        given:
        def version = '3.2.0'
        generate(buildScript.withMongoVersion(version))
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        gradleRunner.run()
        def mongoVersion = getMongoVersionRunning(DEFAULT_MONGOD_PORT)

        then:
        mongoVersion == version
    }

    def 'replication storage location is configurable'() {
        given:
        def storageDir = tmp.newFolder()
        generate(buildScript.withStorageLocation(storageDir.toString()))
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        gradleRunner.run()

        then:
        storageDir.listFiles().size() > 0
    }

    def 'journaling can be enabled'() {
        given:
        // From 2.6 onwards, journaled writes onto a non-journaled mongo db throw exceptions
        generate(buildScript.withJournalingEnabled().withMongoVersion('2.6.1'))
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        gradleRunner.run()
        makeJournaledWrite()

        then:
        noExceptionThrown()
    }

    def 'logging can be made verbose'() {
        given:
        generate(buildScript.withVerboseLogging().withLogging('console'))
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        def executionResult = gradleRunner.run()

        then:
        executionResult.standardOutput.contains(VERBOSE_LOGGING_SAMPLE)
        println executionResult.standardOutput
    }

    def 'by default logging is not verbose'() {
        given:
        generate(buildScript.withLogging('console'))
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        def executionResult = gradleRunner.run()

        then:
        !executionResult.standardOutput.contains(VERBOSE_LOGGING_SAMPLE)
    }

    def 'a URL that does not resolve to a mongo binary will fail'() {
        given:
        generate(buildScript.withDownloadURL('http://www.google.com').withMongoVersion('1.6.5'))
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        gradleRunner.run()

        then:
        thrown(BuildException)
    }

    def cleanup() {
        ensureMongoIsStopped(buildScript.configuredPort ?: DEFAULT_MONGOD_PORT)
    }

    void generate(BuildScriptBuilder buildScriptBuilder) {
        def buildScriptContent = buildScriptBuilder.build()
        tmp.newFile('build.gradle') << buildScriptContent
        gradleRunner.directory = tmp.root
    }
}
