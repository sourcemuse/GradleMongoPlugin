package com.sourcemuse.gradle.plugin.flapdoodle.adapters

import static com.sourcemuse.gradle.plugin.LogDestination.CONSOLE
import static com.sourcemuse.gradle.plugin.LogDestination.FILE
import static com.sourcemuse.gradle.plugin.LogDestination.NONE

import com.sourcemuse.gradle.plugin.GradleMongoPluginExtension
import com.sourcemuse.gradle.plugin.LogDestination
import de.flapdoodle.embed.mongo.packageresolver.Command
import de.flapdoodle.embed.mongo.config.MongodProcessOutputConfig
import de.flapdoodle.embed.process.config.process.ProcessOutput
import de.flapdoodle.embed.process.io.NamedOutputStreamProcessor
import de.flapdoodle.embed.process.io.NullProcessor
import de.flapdoodle.embed.process.io.Processors
import de.flapdoodle.embed.process.io.Slf4jLevel
import groovy.transform.TupleConstructor
import org.gradle.api.GradleScriptException
import org.gradle.api.Project

@TupleConstructor
class ProcessOutputFactory {
    Project project

    ProcessOutput getProcessOutput(GradleMongoPluginExtension pluginExtension) {
        def logDestination = pluginExtension.logging.toUpperCase() as LogDestination

        if (logDestination == CONSOLE) {
            return MongodProcessOutputConfig.getDefaultInstance(Command.MongoD)
        }

        if (logDestination == FILE) {
            def logFile = new File(pluginExtension.logFilePath)
            def logFilePath = logFile.isAbsolute() ? logFile.absolutePath :
                    createRelativeFilePathFromBuildDir(logFile)

            def fileOutputStreamProcessor = new FileOutputStreamProcessor(logFilePath)

            return ProcessOutput.builder()
				.output(new NamedOutputStreamProcessor('[mongod output]', fileOutputStreamProcessor))
                .error(new NamedOutputStreamProcessor('[mongod error]', fileOutputStreamProcessor))
                .commands(new NamedOutputStreamProcessor('[mongod commands]', fileOutputStreamProcessor))
				.build()
        }

        if (logDestination == NONE) {
            def nullProcessor = new NullProcessor()
            return ProcessOutput.builder()
				.output(nullProcessor)
				.error(nullProcessor)
				.commands(nullProcessor)
				.build()
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
