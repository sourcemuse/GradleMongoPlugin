package com.sourcemuse.gradle.plugin

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static com.sourcemuse.gradle.plugin.BuildScriptBuilder.*
import static com.sourcemuse.gradle.plugin.MongoUtils.mongoInstanceRunning
import static com.sourcemuse.gradle.plugin.MongoUtils.ensureMongoIsStopped

class MongoPluginTasksSpec extends Specification {

    static final String MONGO_STARTED_MESSAGE = 'Mongod started'
    static final String STOPPING_MONGO_MESSAGE = 'Stopping Mongod'

    @Rule
    TemporaryFolder tmp

    def 'individual tasks can declare a dependency on a running mongo instance'() {
        given:
        buildScript("""
                    plugins { id 'com.sourcemuse.mongo' }

                    task A {
                        runWithMongoDb = true
                    }
                    """)
        def args = 'A'

        when:
        BuildResult result = runGradle(args)
        def mongoRunningDuringBuild = result.getOutput().contains(MONGO_STARTED_MESSAGE)

        then:
        mongoRunningDuringBuild
    }

    def 'individual tasks can override their mongo configuration'() {
        given:
        buildScript("""
                    plugins { id 'com.sourcemuse.mongo' }

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
        def args = 'A'

        when:
        BuildResult result = runGradle(args)
        def mongoRunningDuringBuild = result.getOutput().contains(MONGO_STARTED_MESSAGE)
        def mongoRunningOnConfiguredPort = result.getOutput().contains('12345')

        then:
        mongoRunningDuringBuild
        mongoRunningOnConfiguredPort
    }

    def 'mongo instance is stopped after task completes'() {
        given:
        buildScript("""
                    plugins { id 'com.sourcemuse.mongo' }

                    task A {
                        runWithMongoDb = true
                    }

                    task B (dependsOn: A) {
                        doFirst {
                            println 'Running task B.'
                        }
                    }
                """)
        def args = 'B'

        when:
        BuildResult result = runGradle(args)

        then:
        mongoStoppedWhenTaskBExecutes(result)
    }

    private static boolean mongoStoppedWhenTaskBExecutes(BuildResult result) {
        result.getOutput().indexOf(STOPPING_MONGO_MESSAGE) < result.getOutput().indexOf('Running task B')
    }

    def 'mongo does not start when task is skipped during configuration phase'() {
        given:
        buildScript("""
                    plugins { id 'com.sourcemuse.mongo' }

                    task A {
                        runWithMongoDb = true
                        enabled = false
                    }
                    """)
        def args = 'A'

        when:
        BuildResult result = runGradle(args)
        def mongoRunningDuringBuild = result.getOutput().contains(MONGO_STARTED_MESSAGE)

        then:
        !mongoRunningDuringBuild
    }

    def 'mongo does not start when task is skipped during execution phase'() {
        given:
        buildScript("""
                    plugins { id 'com.sourcemuse.mongo' }

                    task A {
                        runWithMongoDb = true
                        onlyIf { false }
                    }
                    """)
        def args = 'A'

        when:
        BuildResult result = runGradle(args)
        def mongoRunningDuringBuild = result.getOutput().contains(MONGO_STARTED_MESSAGE)

        then:
        !mongoRunningDuringBuild
    }

    def 'a new mongo instance is not launched if an existing instance is already bound to the same port'() {
        given:
        buildScript("""
                    plugins { id 'com.sourcemuse.mongo' }

                    task B {
                        runWithMongoDb = true
                    }

                    task A(dependsOn: B) {
                        runWithMongoDb = true
                    }
                    """)
        def args = 'A'

        when:
        BuildResult result = runGradle(args)
        def ioExceptionDuringBuild = result.getOutput().contains("IOException")

        then:
        !ioExceptionDuringBuild

        cleanup:
        ensureMongoIsStopped()
    }

    def 'when an existing mongo instance is reused by a task, mongo is not shutdown when the task completes'() {
        given:
        buildScript("""
                    plugins { id 'com.sourcemuse.mongo' }

                    task A {
                        runWithMongoDb = true
                    }

                    A.dependsOn startMongoDb
                    """)
        def args = 'A'

        when:
        runGradle(args)
        def mongoRunningAfterBuild = mongoInstanceRunning()

        then:
        mongoRunningAfterBuild

        cleanup:
        ensureMongoIsStopped()
    }

    def 'multiple mongo instances can be started if bound to separate ports'() {
        given:
        buildScript("""
                    plugins { id 'com.sourcemuse.mongo' }

                    task B {
                        runWithMongoDb = true
                        mongo {
                            port = 27018
                        }
                    }

                    task A(dependsOn: B) {
                        runWithMongoDb = true
                        mongo {
                            port = 27017
                        }
                    }
                    """)
        def args = 'A'

        when:
        BuildResult result = runGradle(args)
        def ioExceptionDuringBuild = result.getOutput().contains("IOException")

        then:
        !ioExceptionDuringBuild

        cleanup:
        ensureMongoIsStopped(27018)
        assert !mongoInstanceRunning(27017)
        assert !mongoInstanceRunning(27018)
    }

    def 'startManagedMongoDb starts a mongo instance, and then stops once the build has completed'() {
        given:
        generate(buildScript())
        def args = START_MANAGED_MONGO_DB_FOR_TEST

        when:
        BuildResult result = runGradle(args)
        def mongoRunningDuringBuild = result.getOutput().contains(MONGO_RUNNING_FLAG)
        def mongoRunningAfterBuild = mongoInstanceRunning()

        then:
        mongoRunningDuringBuild
        !mongoRunningAfterBuild
    }

    def 'startMongoDb starts a mongo instance that continues running after the build has completed'() {
        given:
        generate(buildScript())
        def args = START_MONGO_DB_FOR_TEST

        when:
        BuildResult result = runGradle(args)
        def mongoRunningDuringBuild = result.getOutput().contains(MONGO_RUNNING_FLAG)
        def mongoRunningAfterBuild = mongoInstanceRunning()

        then:
        mongoRunningDuringBuild
        mongoRunningAfterBuild

        cleanup:
        ensureMongoIsStopped()
    }

    def 'if startManagedMongoDb reuses a mongo instance, then it does not stop that instance when the build completes'() {
        given:
        generate(buildScript())

        when:
        runGradle("startMongoDb")

        and:
        runGradle("startManagedMongoDb")

        then:
        mongoInstanceRunning()

        cleanup:
        ensureMongoIsStopped()
    }

    def 'stopMongoDb stops the mongo instance'() {
        given:
        generate(buildScript())
        def args = STOP_MONGO_DB_FOR_TEST

        when:
        runGradle(args)
        def mongoRunningAfterBuild = mongoInstanceRunning()

        then:
        !mongoRunningAfterBuild
    }

    def cleanup() {
        assert !mongoInstanceRunning()
    }

    BuildResult runGradle(String args) {
        return GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(tmp.root)
            .withArguments(args)
            .build()
    }

    void generate(BuildScriptBuilder buildScriptBuilder) {
        buildScript(buildScriptBuilder.build())
    }

    void buildScript(String buildScriptContent) {
        tmp.newFile('build.gradle') << buildScriptContent
    }
}
