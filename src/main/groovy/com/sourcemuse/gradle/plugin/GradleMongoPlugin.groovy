package com.sourcemuse.gradle.plugin

import static com.google.common.base.Charsets.UTF_8
import static com.sourcemuse.gradle.plugin.LogDestination.CONSOLE
import static com.sourcemuse.gradle.plugin.LogDestination.FILE
import static com.sourcemuse.gradle.plugin.LogDestination.NONE
import static com.sourcemuse.gradle.plugin.ManageProcessInstruction.CONTINUE_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS
import static com.sourcemuse.gradle.plugin.ManageProcessInstruction.STOP_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS

import de.flapdoodle.embed.mongo.Command
import de.flapdoodle.embed.mongo.MongodExecutable
import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.IMongodConfig
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder
import de.flapdoodle.embed.mongo.config.MongodProcessOutputConfig
import de.flapdoodle.embed.mongo.config.Net
import de.flapdoodle.embed.mongo.config.RuntimeConfigBuilder
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.runtime.Mongod
import de.flapdoodle.embed.process.config.io.ProcessOutput
import de.flapdoodle.embed.process.io.IStreamProcessor
import de.flapdoodle.embed.process.io.NamedOutputStreamProcessor
import de.flapdoodle.embed.process.io.NullProcessor
import de.flapdoodle.embed.process.runtime.Network
import org.gradle.api.GradleScriptException
import org.gradle.api.Plugin
import org.gradle.api.Project

class GradleMongoPlugin implements Plugin<Project> {

    static final String PLUGIN_EXTENSION_NAME = 'mongo'
    static final String TASK_GROUP_NAME = 'Mongo'

    @Override
    void apply(Project project) {
        configureTaskProperties(project)

        addStartManagedMongoDbTask(project)
        addStartMongoDbTask(project)
        addStopMongoDbTask(project)
    }

    private void configureTaskProperties(Project project) {
        project.extensions.create(PLUGIN_EXTENSION_NAME, GradleMongoPluginExtension)
    }

    private void addStartManagedMongoDbTask(Project project) {
        project.task(group: TASK_GROUP_NAME, description: 'Starts a local MongoDb instance which will stop when the build process completes', 'startManagedMongoDb') << {
            startMongoDb(project, STOP_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS)

            addStopMongoDbTaskIfNotPresent(project)
        }
    }

    private void addStartMongoDbTask(Project project) {
        project.task(group: TASK_GROUP_NAME, description: 'Starts a local MongoDb instance', 'startMongoDb') << {
            startMongoDb(project, CONTINUE_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS)
        }
    }

    private void addStopMongoDbTaskIfNotPresent(Project project) {
        boolean stopMongoDbTaskPresent = project.gradle.taskGraph.allTasks.find { it.name == 'stopMongoDb' }

        if (!stopMongoDbTaskPresent) {
            def lastTask = project.gradle.taskGraph.allTasks[-1]
            project.gradle.taskGraph.afterTask { task, taskState ->
                if (task == lastTask) {
                    stopMongoDb(project)
                }
            }
        }
    }

    private void startMongoDb(Project project, ManageProcessInstruction manageProcessInstruction) {
        GradleMongoPluginExtension pluginExtension = project."$PLUGIN_EXTENSION_NAME"

        IMongodConfig mongodConfig = new MongodConfigBuilder()
                .version(Version.Main.PRODUCTION)
                .net(new Net(pluginExtension.bindIp, pluginExtension.port, Network.localhostIsIPv6()))
                .build();


        def runtimeConfig = new RuntimeConfigBuilder()
                .defaults(Command.MongoD)
                .processOutput(getLogger(pluginExtension))
                .daemonProcess(manageProcessInstruction == STOP_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS)
                .build()

        MongodStarter runtime = MongodStarter.getInstance(runtimeConfig);

        MongodExecutable mongodExecutable = runtime.prepare(mongodConfig);
        mongodExecutable.start();
    }

    private void addStopMongoDbTask(Project project) {
        project.task(group: TASK_GROUP_NAME, description: 'Stops the local MongoDb instance', 'stopMongoDb') << {
            stopMongoDb(project)
        }
    }

    private void stopMongoDb(Project project) {
        Mongod.sendShutdown(InetAddress.getLoopbackAddress(), project."$PLUGIN_EXTENSION_NAME".port)
    }

    private ProcessOutput getLogger(GradleMongoPluginExtension pluginExtension) {
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

enum ManageProcessInstruction {
    STOP_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS, CONTINUE_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS
}

class GradleMongoPluginExtension {
    int port = 27017
    String bindIp = '127.0.0.1'
    String logging = CONSOLE as String
    String logFilePath = 'embedded-mongo.log'
}

enum LogDestination {
    CONSOLE, FILE, NONE
}

class FileOutputStreamProcessor implements IStreamProcessor {

    File logFile

    FileOutputStreamProcessor(String filePath) {
        logFile = new File(filePath)
        logFile.mkdirs()
        logFile.createNewFile()
    }

    @Override
    void process(String block) {
        logFile.text += block
    }

    @Override
    void onProcessed() {

    }
}
