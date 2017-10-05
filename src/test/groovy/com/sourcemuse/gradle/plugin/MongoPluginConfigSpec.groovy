package com.sourcemuse.gradle.plugin

import de.flapdoodle.embed.mongo.distribution.Version
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.testkit.functional.GradleRunnerFactory
import org.gradle.tooling.BuildException
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.littleshoot.proxy.HttpProxyServer
import org.littleshoot.proxy.impl.DefaultHttpProxyServer
import spock.lang.Issue
import spock.lang.Specification

import java.nio.file.Path

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

    def 'storage engine can be set to WiredTiger'() {
        given:
        generate(buildScript.withMongoVersion(Version.Main.V3_2.asInDownloadPath()).withStorageEngine('wiredTiger'))
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        gradleRunner.run()

        then:
        mongoServerStatus().storageEngine.name == 'wiredTiger'
        noExceptionThrown()
    }

    def 'storage engine can be set to MMAPv1'() {
        given:
        generate(buildScript.withMongoVersion(Version.Main.V3_2.asInDownloadPath()).withStorageEngine('mmapv1'))
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        gradleRunner.run()

        then:
        mongoServerStatus().storageEngine.name == 'mmapv1'
        noExceptionThrown()
    }

    def 'the default storage engine is WiredTiger for versions after 3.2'() {
        given:
        generate(buildScript.withMongoVersion(Version.Main.V3_2.asInDownloadPath()))
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        gradleRunner.run()

        then:
        mongoServerStatus().storageEngine.name == 'wiredTiger'
        noExceptionThrown()
    }

    def 'the default storage engine is MMAPv1 for versions before 3.0'() {
        given:
        generate(buildScript.withMongoVersion(Version.Main.V3_0.asInDownloadPath()))
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        gradleRunner.run()

        then:
        mongoServerStatus().storageEngine.name == 'mmapv1'
        noExceptionThrown()
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

    def 'will fail with non-routable proxy'() {
        given:
        int proxyPort = 9091
        String proxyHost = 'invalidHost'
        String path = File.createTempDir().toString()
        generate(buildScript.withProxy(proxyHost, proxyPort).withArtifactStorePath(path))
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        gradleRunner.run()

        then:
        def e = thrown(Exception)
        def message = e.cause.cause.cause.message
        message.contains("with proxy HTTP @ $proxyHost:$proxyPort")

        cleanup:
        new File(path).deleteDir()
    }

    def 'can use proxy to download and a custom location'() {
        given:
        int proxyPort = 9091
        DefaultHttpProxyServer.bootstrap().withPort(proxyPort).start()
        String path = File.createTempDir().toString()
        generate(buildScript.withProxy('localhost', proxyPort).withArtifactStorePath(path))
        gradleRunner.arguments << TEST_START_MONGO_DB

        when:
        gradleRunner.run()

        then:
        mongoInstanceRunning()
        noExceptionThrown()

        cleanup:
        new File(path).deleteDir()
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
