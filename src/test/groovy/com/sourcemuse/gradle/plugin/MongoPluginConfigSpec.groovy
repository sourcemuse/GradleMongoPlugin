package com.sourcemuse.gradle.plugin

import static com.sourcemuse.gradle.plugin.BuildScriptBuilder.DEFAULT_MONGOD_PORT
import static com.sourcemuse.gradle.plugin.MongoUtils.ensureMongoIsStopped
import static com.sourcemuse.gradle.plugin.MongoUtils.mongoInstanceRunning
import static com.sourcemuse.gradle.plugin.TestPlugin.TEST_START_MONGO_DB

import org.gradle.testkit.functional.GradleRunnerFactory
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class MongoPluginConfigSpec extends Specification {

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
        println gradleRunner.directory

        when:
        def executionResult = gradleRunner.run()

        then:

        !executionResult.standardOutput.contains('[mongod output]')
        tempFile.text.contains('[mongod output]')
    }

    def cleanup() {
        ensureMongoIsStopped(buildScript.port ?: DEFAULT_MONGOD_PORT)
    }

    void generate(BuildScriptBuilder buildScriptBuilder) {
        def buildScriptContent = buildScriptBuilder.build()
        tmp.newFile('build.gradle') << buildScriptContent
        gradleRunner.directory = tmp.root
    }
}
