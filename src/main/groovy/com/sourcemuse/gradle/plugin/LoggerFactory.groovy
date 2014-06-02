package com.sourcemuse.gradle.plugin

import static com.sourcemuse.gradle.plugin.LogDestination.CONSOLE
import static com.sourcemuse.gradle.plugin.LogDestination.FILE
import static com.sourcemuse.gradle.plugin.LogDestination.NONE

import de.flapdoodle.embed.mongo.Command
import de.flapdoodle.embed.mongo.config.MongodProcessOutputConfig
import de.flapdoodle.embed.process.config.io.ProcessOutput
import de.flapdoodle.embed.process.io.NamedOutputStreamProcessor
import de.flapdoodle.embed.process.io.NullProcessor
import groovy.transform.TupleConstructor
import org.gradle.api.GradleScriptException
import org.gradle.api.Project

@TupleConstructor
class LoggerFactory {
    Project project

    ProcessOutput getLogger(GradleMongoPluginExtension pluginExtension) {
        def logDestination = pluginExtension.logging.toUpperCase() as LogDestination

        if (logDestination == CONSOLE) {
            return MongodProcessOutputConfig.getDefaultInstance(Command.MongoD)
        }

        if (logDestination == FILE) {
            def logFile = new File(pluginExtension.logFilePath)
            def logFilePath = logFile.isAbsolute() ? logFile.absolutePath :
                    createRelativeFilePathFromBuildDir(logFile)

            def fileOutputStreamProcessor = new FileOutputStreamProcessor(logFilePath)

            return new ProcessOutput(
                    new NamedOutputStreamProcessor('[mongod output]', fileOutputStreamProcessor),
                    new NamedOutputStreamProcessor('[mongod error]', fileOutputStreamProcessor),
                    new NamedOutputStreamProcessor('[mongod commands]', fileOutputStreamProcessor));
        }

        if (logDestination == NONE) {
            def nullProcessor = new NullProcessor()
            return new ProcessOutput(nullProcessor, nullProcessor, nullProcessor)
        }

        throw new GradleScriptException(
                "Unrecognized 'logging' option: ${pluginExtension.logging}. " +
                        "Choose one of ${LogDestination.values().collect { it.toString().toLowerCase() }.join(', ')}.",
                new IllegalArgumentException()
        )
    }

    private String createRelativeFilePathFromBuildDir(File logFile) {
        project.buildDir.absolutePath + File.separatorChar + logFile.path
    }
}
