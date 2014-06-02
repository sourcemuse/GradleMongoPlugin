package com.sourcemuse.gradle.plugin

import static com.sourcemuse.gradle.plugin.LogDestination.CONSOLE
import static com.sourcemuse.gradle.plugin.LogDestination.FILE
import static com.sourcemuse.gradle.plugin.LogDestination.NONE

import de.flapdoodle.embed.mongo.Command
import de.flapdoodle.embed.mongo.config.MongodProcessOutputConfig
import de.flapdoodle.embed.process.config.io.ProcessOutput
import de.flapdoodle.embed.process.io.NamedOutputStreamProcessor
import de.flapdoodle.embed.process.io.NullProcessor
import org.gradle.api.GradleScriptException

class LoggerFactory {
    ProcessOutput getLogger(GradleMongoPluginExtension pluginExtension) {
        def logDestination = pluginExtension.logging.toUpperCase() as LogDestination

        if (logDestination == CONSOLE) {
            return MongodProcessOutputConfig.getDefaultInstance(Command.MongoD)
        }

        if (logDestination == FILE) {
            def fileOutputStreamProcessor = new FileOutputStreamProcessor(pluginExtension.logFilePath)

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
}
