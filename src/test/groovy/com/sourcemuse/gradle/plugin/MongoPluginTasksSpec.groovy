package com.sourcemuse.gradle.plugin

import static com.sourcemuse.gradle.plugin.BuildScriptBuilder.DEFAULT_MONGOD_PORT
import static com.sourcemuse.gradle.plugin.BuildScriptBuilder.buildScript
import static com.sourcemuse.gradle.plugin.MongoUtils.ensureMongoIsStopped
import static com.sourcemuse.gradle.plugin.MongoUtils.mongoInstanceRunning
import static com.sourcemuse.gradle.plugin.TestPlugin.MONGO_RUNNING_FLAG
import static com.sourcemuse.gradle.plugin.TestPlugin.TEST_START_MANAGED_MONGO_DB
import static com.sourcemuse.gradle.plugin.TestPlugin.TEST_START_MONGO_DB
import static com.sourcemuse.gradle.plugin.TestPlugin.TEST_STOP_MONGO_DB

import org.gradle.testkit.functional.ExecutionResult
import org.gradle.testkit.functional.GradleRunnerFactory

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class MongoPluginTasksSpec extends Specification {

    @Rule TemporaryFolder tmp
    def gradleRunner = GradleRunnerFactory.create()
    def buildScript = new BuildScriptBuilder()
    def randomPort = null

    def 'startManagedMongoDb starts a mongo instance, and then stops once the build has completed'() {
        given:
        generate(buildScript())
        gradleRunner.arguments << TEST_START_MANAGED_MONGO_DB

        when:
        ExecutionResult result = gradleRunner.run()
        def mongoRunningDuringBuild = result.standardOutput.contains(MONGO_RUNNING_FLAG)
        def mongoRunningAfterBuild = mongoInstanceRunning(DEFAULT_MONGOD_PORT)

        then:
        mongoRunningDuringBuild
        !mongoRunningAfterBuild
    }

    def 'startManagedMongoDb starts a mongo instance with a random port, and then stops once the build has completed'() {
        given:
        generate(buildScript.withRandomPort(true).withLogging("'console'"))
        gradleRunner.arguments << TEST_START_MANAGED_MONGO_DB

        when:
        ExecutionResult result = gradleRunner.run()
        def mongoRunningDuringBuild = result.standardOutput.contains(MONGO_RUNNING_FLAG)
        randomPort = TestPlugin.findPortInOutput(result.standardOutput)
        def mongoRunningAfterBuild = mongoInstanceRunning(randomPort)

        then:
        mongoRunningDuringBuild
        randomPort != null
        !mongoRunningAfterBuild
    }

    def 'startMongoDb starts a mongo instance that continues running after the build has completed'() {
        given:
        generate(buildScript())
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        ExecutionResult result = gradleRunner.run()
        def mongoRunningDuringBuild = result.standardOutput.contains(MONGO_RUNNING_FLAG)
        def mongoRunningAfterBuild = mongoInstanceRunning(DEFAULT_MONGOD_PORT)

        then:
        mongoRunningDuringBuild
        mongoRunningAfterBuild
    }

    def 'stopMongoDb stops the mongo instance'() {
        given:
        generate(buildScript())
        gradleRunner.arguments << TEST_STOP_MONGO_DB

        when:
        gradleRunner.run()
        def mongoRunningAfterBuild = mongoInstanceRunning(DEFAULT_MONGOD_PORT)

        then:
        !mongoRunningAfterBuild
    }


    def cleanup() {
        if (buildScript.randomPort) {
            ensureMongoIsStopped((int) randomPort)
            randomPort = null
        } else {
            ensureMongoIsStopped()
        }
    }

    void generate(BuildScriptBuilder buildScriptBuilder) {
        def buildScriptContent = buildScriptBuilder.build()
        tmp.newFile('build.gradle') << buildScriptContent
        gradleRunner.directory = tmp.root
    }
}
