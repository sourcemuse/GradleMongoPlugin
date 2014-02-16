package com.sourcemuse.gradle.plugin

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

    @Rule TemporaryFolder tmp
    def gradleRunner = GradleRunnerFactory.create()

    def setup() {
        tmp.newFile('build.gradle') << """
            apply plugin: ${GradleMongoPlugin.name}
            apply plugin: ${TestPlugin.name}
        """
        gradleRunner.directory = tmp.root
    }

    def 'startManagedMongoDb starts a mongo instance, and then stops once the build has completed'() {
        given:
        gradleRunner.arguments << 'testStartManagedMongoDb'

        when:
        ExecutionResult result = gradleRunner.run()
        def mongoRunningDuringBuild = result.standardOutput.contains('mongo running!')
        def mongoRunningAfterBuild = mongoInstanceRunning()

        then:
        mongoRunningDuringBuild
        !mongoRunningAfterBuild
    }

    def 'startMongoDb starts a mongo instance that continues running after the build has completed'() {
        given:
        gradleRunner.arguments << 'testStartMongoDb'

        when:
        ExecutionResult result = gradleRunner.run()
        def mongoRunningDuringBuild = result.standardOutput.contains('mongo running!')
        def mongoRunningAfterBuild = mongoInstanceRunning()

        then:
        mongoRunningDuringBuild
        mongoRunningAfterBuild
    }

    def 'stopMongoDb stops the mongo instance'() {
        given:
        gradleRunner.arguments << 'testStopMongoDb'

        when:
        ExecutionResult result = gradleRunner.run()
        def mongoRunningAfterBuild = mongoInstanceRunning()

        then:
        !mongoRunningAfterBuild
    }

    def cleanup() {
        ensureMongoIsStopped()
    }

    def ensureMongoIsStopped() {
        Mongod.sendShutdown(InetAddress.getLoopbackAddress(), 27017)
    }

    static boolean mongoInstanceRunning() {
        try {
            MongoClient mongoClient = new MongoClient('localhost', 27017)
            mongoClient.getDB('test').getStats()
        } catch (Exception e) {
            return false
        }
        true
    }
}

class TestPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.task(dependsOn: 'startManagedMongoDb', 'testStartManagedMongoDb') << {
            if (mongoInstanceRunning())
                println 'mongo running!'
        }
        project.task(dependsOn: 'startMongoDb', 'testStartMongoDb') << {
            if (mongoInstanceRunning())
                println 'mongo running!'
        }
        project.task(dependsOn: ['startMongoDb', 'stopMongoDb'], 'testStopMongoDb') << {
        }
    }
}

