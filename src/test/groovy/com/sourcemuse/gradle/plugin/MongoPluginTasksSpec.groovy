package com.sourcemuse.gradle.plugin

import com.sourcemuse.gradle.plugin.flapdoodle.gradle.GradleMongoPlugin
import org.gradle.testkit.functional.ExecutionResult
import org.gradle.testkit.functional.GradleRunnerFactory
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static com.sourcemuse.gradle.plugin.BuildScriptBuilder.buildScript
import static com.sourcemuse.gradle.plugin.MongoUtils.ensureMongoIsStopped
import static com.sourcemuse.gradle.plugin.MongoUtils.mongoInstanceRunning
import static com.sourcemuse.gradle.plugin.PluginForTests.*

class MongoPluginTasksSpec extends Specification {

    static final String MONGO_STARTED_MESSAGE = 'Mongod started'
    static final String STOPPING_MONGO_MESSAGE = 'Stopping Mongod'

    @Rule TemporaryFolder tmp
    def gradleRunner = GradleRunnerFactory.create()

    def 'individual tasks can declare a dependency on a running mongo instance'() {
        given:
        buildScript("""
                    apply plugin: $GradleMongoPlugin.name

                    task A {
                        runWithMongoDb = true
                    }
                    """)
        gradleRunner.arguments << 'A'

        when:
        ExecutionResult result = gradleRunner.run()
        def mongoRunningDuringBuild = result.standardOutput.contains(MONGO_STARTED_MESSAGE)

        then:
        mongoRunningDuringBuild
    }

    def 'individual tasks can override their mongo configuration'() {
        given:
        buildScript("""
                    apply plugin: $GradleMongoPlugin.name

                    mongo {
                        port = 23456
                    }

                    task A {
                        runWithMongoDb = true
                        mongo {
                            port = 12345
                        }
                    }
                    """)
        gradleRunner.arguments << 'A'

        when:
        ExecutionResult result = gradleRunner.run()
        def mongoRunningDuringBuild = result.standardOutput.contains(MONGO_STARTED_MESSAGE)
        def mongoRunningOnConfiguredPort = result.standardOutput.contains('12345')

        then:
        mongoRunningDuringBuild
        mongoRunningOnConfiguredPort
    }

    def 'mongo instance is stopped after task completes'() {
        given:
        buildScript("""
                    apply plugin: $GradleMongoPlugin.name

                    task A {
                        runWithMongoDb = true
                    }

                    task B (dependsOn: A) {
                        doFirst {
                            println 'Running task B.'
                        }
                    }
                """)
        gradleRunner.arguments << 'B'

        when:
        ExecutionResult result = gradleRunner.run()

        then:
        mongoStoppedWhenTaskBExecutes(result)
    }

    private static boolean mongoStoppedWhenTaskBExecutes(ExecutionResult result) {
        result.standardOutput.indexOf(STOPPING_MONGO_MESSAGE) < result.standardOutput.indexOf('Running task B')
    }

    def 'mongo does not start when task is skipped during configuration phase'() {
        given:
        buildScript("""
                    apply plugin: $GradleMongoPlugin.name

                    task A {
                        runWithMongoDb = true
                        enabled = false
                    }
                    """)
        gradleRunner.arguments << 'A'

        when:
        ExecutionResult result = gradleRunner.run()
        def mongoRunningDuringBuild = result.standardOutput.contains(MONGO_STARTED_MESSAGE)

        then:
        !mongoRunningDuringBuild
    }

    def 'mongo does not start when task is skipped during execution phase'() {
        given:
        buildScript("""
                    apply plugin: $GradleMongoPlugin.name

                    task A {
                        runWithMongoDb = true
                        onlyIf { false }
                    }
                    """)
        gradleRunner.arguments << 'A'

        when:
        ExecutionResult result = gradleRunner.run()
        def mongoRunningDuringBuild = result.standardOutput.contains(MONGO_STARTED_MESSAGE)

        then:
        !mongoRunningDuringBuild
    }

    def 'a new mongo instance is not launched if an existing instance is already bound to the same port'() {
        given:
        buildScript("""
                    apply plugin: $GradleMongoPlugin.name

                    task A {
                        runWithMongoDb = true
                        doFirst { B.execute() }
                    }

                    task B {
                        runWithMongoDb = true
                    }
                    """)
        gradleRunner.arguments << 'A'

        when:
        ExecutionResult result = gradleRunner.run()
        def ioExceptionDuringBuild = result.standardOutput.contains("IOException")

        then:
        !ioExceptionDuringBuild
    }

    def 'when an existing mongo instance is reused by a task, mongo is not shutdown when the task completes'() {
        given:
        buildScript("""
                    apply plugin: $GradleMongoPlugin.name

                    task A {
                        runWithMongoDb = true
                    }

                    A.dependsOn startMongoDb
                    """)
        gradleRunner.arguments << 'A'

        when:
        gradleRunner.run()
        def mongoRunningAfterBuild = mongoInstanceRunning()

        then:
        mongoRunningAfterBuild
    }

    def 'multiple mongo instances can be started if bound to separate ports'() {
        given:
        buildScript("""
                    apply plugin: $GradleMongoPlugin.name

                    task A {
                        runWithMongoDb = true
                        doFirst { B.execute() }
                        mongo {
                            port = 27017
                        }
                    }

                    task B {
                        runWithMongoDb = true
                        mongo {
                            port = 27018
                        }
                    }
                    """)
        gradleRunner.arguments << 'A'

        when:
        ExecutionResult result = gradleRunner.run()
        def ioExceptionDuringBuild = result.standardOutput.contains("IOException")

        then:
        !ioExceptionDuringBuild

        cleanup:
        ensureMongoIsStopped(27017)
        ensureMongoIsStopped(27018)
    }

    def 'startManagedMongoDb starts a mongo instance, and then stops once the build has completed'() {
        given:
        generate(buildScript())
        gradleRunner.arguments << TEST_START_MANAGED_MONGO_DB

        when:
        ExecutionResult result = gradleRunner.run()
        def mongoRunningDuringBuild = result.standardOutput.contains(MONGO_RUNNING_FLAG)
        def mongoRunningAfterBuild = mongoInstanceRunning()

        then:
        mongoRunningDuringBuild
        !mongoRunningAfterBuild
    }

    def 'startMongoDb starts a mongo instance that continues running after the build has completed'() {
        given:
        generate(buildScript())
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        ExecutionResult result = gradleRunner.run()
        def mongoRunningDuringBuild = result.standardOutput.contains(MONGO_RUNNING_FLAG)
        def mongoRunningAfterBuild = mongoInstanceRunning()

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
        def mongoRunningAfterBuild = mongoInstanceRunning()

        then:
        !mongoRunningAfterBuild
    }

    def cleanup() {
        ensureMongoIsStopped()
    }

    void generate(BuildScriptBuilder buildScriptBuilder) {
        buildScript(buildScriptBuilder.build())
    }

    void buildScript(String buildScriptContent) {
        tmp.newFile('build.gradle') << buildScriptContent
        gradleRunner.directory = tmp.root
    }
}
