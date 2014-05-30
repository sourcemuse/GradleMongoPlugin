package com.sourcemuse.gradle.plugin

import static com.sourcemuse.gradle.plugin.GradleMongoPluginSpec.BuildScriptBuilder.buildScript
import static com.sourcemuse.gradle.plugin.GradleMongoPluginSpec.DEFAULT_MONGOD_PORT
import static com.sourcemuse.gradle.plugin.TestPlugin.TEST_START_MANAGED_MONGO_DB
import static com.sourcemuse.gradle.plugin.TestPlugin.TEST_START_MONGO_DB
import static com.sourcemuse.gradle.plugin.TestPlugin.TEST_STOP_MONGO_DB

import com.mongodb.MongoClient
import de.flapdoodle.embed.mongo.runtime.Mongod
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.testkit.functional.ExecutionResult
import org.gradle.testkit.functional.GradleRunnerFactory
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static com.sourcemuse.gradle.plugin.GradleMongoPluginSpec.mongoInstanceRunning

class GradleMongoPluginSpec extends Specification {

    static final DEFAULT_MONGOD_PORT = 27017

    @Rule TemporaryFolder tmp
    def gradleRunner = GradleRunnerFactory.create()
    def buildScript = new BuildScriptBuilder()

    def 'startManagedMongoDb starts a mongo instance, and then stops once the build has completed'() {
        given:
        generate(buildScript)
        gradleRunner.arguments << TEST_START_MANAGED_MONGO_DB

        when:
        ExecutionResult result = gradleRunner.run()
        def mongoRunningDuringBuild = result.standardOutput.contains('mongo running!')
        def mongoRunningAfterBuild = mongoInstanceRunning(DEFAULT_MONGOD_PORT)

        then:
        mongoRunningDuringBuild
        !mongoRunningAfterBuild
    }

    def 'startMongoDb starts a mongo instance that continues running after the build has completed'() {
        given:
        generate(buildScript)
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        ExecutionResult result = gradleRunner.run()
        def mongoRunningDuringBuild = result.standardOutput.contains('mongo running!')
        def mongoRunningAfterBuild = mongoInstanceRunning(DEFAULT_MONGOD_PORT)

        then:
        mongoRunningDuringBuild
        mongoRunningAfterBuild
    }

    def 'stopMongoDb stops the mongo instance'() {
        given:
        generate(buildScript)
        gradleRunner.arguments << TEST_STOP_MONGO_DB

        when:
        gradleRunner.run()
        def mongoRunningAfterBuild = mongoInstanceRunning(DEFAULT_MONGOD_PORT)

        then:
        !mongoRunningAfterBuild
    }

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

    def cleanup() {
        ensureMongoIsStopped()
    }

    void ensureMongoIsStopped() {
        Mongod.sendShutdown(InetAddress.getLoopbackAddress(), buildScript.port ?: DEFAULT_MONGOD_PORT)
    }

    static boolean mongoInstanceRunning(int port) {
        try {
            MongoClient mongoClient = new MongoClient('localhost', port)
            mongoClient.getDB('test').getStats()
        } catch (Exception e) {
            return false
        }
        true
    }

    void generate(BuildScriptBuilder buildScriptBuilder) {
        def buildScriptContent = buildScriptBuilder.build()
        tmp.newFile('build.gradle') << buildScriptContent
        gradleRunner.directory = tmp.root
    }

    static class BuildScriptBuilder {
        int port
        String logging
        String filePath

        String build() {
            def mongoConfigBlock = new MongoPluginConfigBlock()

            if (port) {
                mongoConfigBlock.addPropertyConfig('port', "$port")
            }

            if (logging) {
                mongoConfigBlock.addPropertyConfig('logging', logging)
            }

            if (filePath) {
                mongoConfigBlock.addPropertyConfig('logFilePath', filePath)
            }

            """
            |apply plugin: ${GradleMongoPlugin.name}
            |apply plugin: ${TestPlugin.name}
            |
            |${mongoConfigBlock}
            """.stripMargin()
        }

        static BuildScriptBuilder buildScript() {
            new BuildScriptBuilder()
        }

        BuildScriptBuilder withPort(int port) {
            this.port = port
            this
        }

        BuildScriptBuilder withLogging(String logging) {
            this.logging = logging
            this
        }

        BuildScriptBuilder withFilePath(String filePath) {
            this.filePath = filePath
            this
        }
    }

    static class MongoPluginConfigBlock {
        String config = ''

        void addPropertyConfig(String propertyName, String value) {
            if (!config) {
                config += 'mongo {\n'
            }

            config += "\t$propertyName=$value\n"
        }

        String toString() {
            if (config) {
                config += '}'
            }
        }
    }
}


class TestPlugin implements Plugin<Project> {

    static final TEST_STOP_MONGO_DB = 'testStopMongoDb'
    static final TEST_START_MONGO_DB = 'testStartMongoDb'
    static final TEST_START_MANAGED_MONGO_DB = 'testStartManagedMongoDb'

    @Override
    void apply(Project project) {
        project.task(dependsOn: 'startManagedMongoDb', TEST_START_MANAGED_MONGO_DB) << {
            if (mongoInstanceRunning(DEFAULT_MONGOD_PORT))
                println 'mongo running!'
        }
        project.task(dependsOn: 'startMongoDb', TEST_START_MONGO_DB) << {
            if (mongoInstanceRunning(DEFAULT_MONGOD_PORT))
                println 'mongo running!'
        }
        project.task(dependsOn: ['startMongoDb', 'stopMongoDb'], TEST_STOP_MONGO_DB)
    }
}

