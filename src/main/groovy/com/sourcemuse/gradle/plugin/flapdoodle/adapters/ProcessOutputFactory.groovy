package com.sourcemuse.gradle.plugin.flapdoodle.adapters

import com.sourcemuse.gradle.plugin.GradleMongoPluginExtension
import com.sourcemuse.gradle.plugin.LogDestination
import de.flapdoodle.embed.process.io.NamedOutputStreamProcessor
import de.flapdoodle.embed.process.io.ProcessOutput
import de.flapdoodle.reverse.State
import de.flapdoodle.reverse.StateID
import de.flapdoodle.reverse.StateLookup
import de.flapdoodle.reverse.Transition
import groovy.transform.TupleConstructor
import org.gradle.api.GradleScriptException
import org.gradle.api.Project

import static com.sourcemuse.gradle.plugin.LogDestination.CONSOLE
import static com.sourcemuse.gradle.plugin.LogDestination.FILE
import static com.sourcemuse.gradle.plugin.LogDestination.NONE

@TupleConstructor
class ProcessOutputFactory implements Transition<ProcessOutput> {
  Project project
  GradleMongoPluginExtension pluginExtension

  ProcessOutput getProcessOutput() {
    def logDestination = pluginExtension.logging.toUpperCase() as LogDestination

    if (logDestination == CONSOLE) {
      return ProcessOutput.namedConsole("mongod")
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
      return ProcessOutput.silent()
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

  @Override
  StateID<ProcessOutput> destination() {
    return StateID.of(ProcessOutput.class)
  }

  @Override
  Set<StateID<?>> sources() {
    return Set.of()
  }

  @Override
  State<ProcessOutput> result(StateLookup lookup) {
    return State.of(getProcessOutput())
  }
}
