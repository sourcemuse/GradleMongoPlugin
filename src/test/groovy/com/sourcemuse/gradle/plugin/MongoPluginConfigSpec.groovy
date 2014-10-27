package com.sourcemuse.gradle.plugin

import static com.sourcemuse.gradle.plugin.BuildScriptBuilder.DEFAULT_MONGOD_PORT
import static com.sourcemuse.gradle.plugin.MongoUtils.ensureMongoIsStopped
import static com.sourcemuse.gradle.plugin.MongoUtils.mongoInstanceRunning
import static com.sourcemuse.gradle.plugin.MongoUtils.mongoVersionRunning
import static com.sourcemuse.gradle.plugin.TestPlugin.TEST_START_MONGO_DB

import org.gradle.testkit.functional.GradleRunnerFactory
import org.junit.Rule
import org.junit.rules.TemporaryFolder

import de.flapdoodle.embed.mongo.distribution.Version;
import spock.lang.Specification

class MongoPluginConfigSpec extends Specification {

    @Rule
    TemporaryFolder tmp
    def gradleRunner = GradleRunnerFactory.create()
    def buildScript = new BuildScriptBuilder()
    def randomPort = null

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

    def 'port is randomizable'() {
        given:
        generate(buildScript.withRandomPort(true))
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        def executionResult = gradleRunner.run()
        randomPort = TestPlugin.findPortInOutput(executionResult.standardOutput)
        def mongoRunningOnPort = mongoInstanceRunning(randomPort)

        then:
        randomPort != null
        mongoRunningOnPort
    }

    def 'logging can route to the console'() {
        given:
        generate(buildScript.withLogging("'console'"))
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        def executionResult = gradleRunner.run()

        then:
        executionResult.standardOutput.contains('[mongod output]')
    }

    def 'logging can be switched off'() {
        given:
        generate(buildScript.withLogging("'none'"))
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        def executionResult = gradleRunner.run()

        then:
        !executionResult.standardOutput.contains('[mongod output]')
    }

    def 'logging can be routed to a file'() {
        given:
        def tempFile = tmp.newFile()
        generate(buildScript.withLogging("'file'").withFilePath("'${tempFile.absolutePath}'"))
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        def executionResult = gradleRunner.run()

        then:

        !executionResult.standardOutput.contains('[mongod output]')
        tempFile.text.contains('[mongod output]')
    }

    def 'general version is configurable'() {
        given:
        generate(buildScript.withMongoVersion("'DEVELOPMENT'"))
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        gradleRunner.run()
        def mongoVersion = mongoVersionRunning(DEFAULT_MONGOD_PORT)

        then:
        Version.Main.DEVELOPMENT.asInDownloadPath().equalsIgnoreCase(mongoVersion)
    }

    def 'specific version is configurable'() {
        given:
        generate(buildScript.withMongoVersion("'2.5.4'"))
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        gradleRunner.run()
        def mongoVersion = mongoVersionRunning(DEFAULT_MONGOD_PORT)

        then:
        Version.V2_5_4.asInDownloadPath().equalsIgnoreCase(mongoVersion)
    }

    def 'latest branch version is configurable'() {
        given:
        generate(buildScript.withMongoVersion("'2.4-LATEST'"))
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        gradleRunner.run()
        def mongoVersion = mongoVersionRunning(DEFAULT_MONGOD_PORT)

        then:
        Version.Main.V2_4.asInDownloadPath().equalsIgnoreCase(mongoVersion)
    }

    def 'replication storage location is configurable'() {
        given:
        def storageDir = tmp.newFolder()
        generate(buildScript.withStorageLocation("'$storageDir'"))
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        gradleRunner.run()

        then:
        storageDir.listFiles().size() > 0
    }

    def cleanup() {
        if (buildScript.randomPort) {
            ensureMongoIsStopped((int) randomPort)
            randomPort = null
        } else {
            ensureMongoIsStopped(buildScript.port ?: DEFAULT_MONGOD_PORT)
        }
    }

    void generate(BuildScriptBuilder buildScriptBuilder) {
        def buildScriptContent = buildScriptBuilder.build()
        tmp.newFile('build.gradle') << buildScriptContent
        gradleRunner.directory = tmp.root
    }
}
