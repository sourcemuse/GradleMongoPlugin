package com.sourcemuse.gradle.plugin

import static com.sourcemuse.gradle.plugin.ManageProcessInstruction.CONTINUE_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS
import static com.sourcemuse.gradle.plugin.ManageProcessInstruction.STOP_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS

import de.flapdoodle.embed.mongo.Command
import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.ArtifactStoreBuilder
import de.flapdoodle.embed.mongo.config.DownloadConfigBuilder
import de.flapdoodle.embed.mongo.config.IMongoCmdOptions
import de.flapdoodle.embed.mongo.config.MongoCmdOptionsBuilder
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder
import de.flapdoodle.embed.mongo.config.Net
import de.flapdoodle.embed.mongo.config.RuntimeConfigBuilder
import de.flapdoodle.embed.mongo.runtime.Mongod
import de.flapdoodle.embed.process.config.store.IDownloadConfig
import de.flapdoodle.embed.process.distribution.IVersion
import de.flapdoodle.embed.process.io.progress.IProgressListener
import de.flapdoodle.embed.process.runtime.Network
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

    private static void configureTaskProperties(Project project) {
        project.extensions.create(PLUGIN_EXTENSION_NAME, GradleMongoPluginExtension)
    }

    private static void addStartManagedMongoDbTask(Project project) {
        project.task(group: TASK_GROUP_NAME, description: 'Starts a local MongoDb instance which will stop when the build process completes', 'startManagedMongoDb') << {
            startMongoDb(project, STOP_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS)

            addStopMongoDbTaskIfNotPresent(project)
        }
    }

    private static void addStartMongoDbTask(Project project) {
        project.task(group: TASK_GROUP_NAME, description: 'Starts a local MongoDb instance', 'startMongoDb') << {
            startMongoDb(project, CONTINUE_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS)
        }
    }

    private static void addStopMongoDbTaskIfNotPresent(Project project) {
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

    private static void startMongoDb(Project project, ManageProcessInstruction manageProcessInstruction) {
        GradleMongoPluginExtension pluginExtension = project[PLUGIN_EXTENSION_NAME] as GradleMongoPluginExtension
        def processOutput = new LoggerFactory(project).getLogger(pluginExtension)
        def version = new VersionFactory().getVersion(pluginExtension)
        def storage = new StorageFactory().getStorage(pluginExtension)

        def mongodConfig = new MongodConfigBuilder()
                .cmdOptions(createMongoCommandOptions(pluginExtension))
                .version(version)
                .replication(storage)
                .net(new Net(pluginExtension.bindIp, pluginExtension.port, Network.localhostIsIPv6()))
                .build()

        def runtimeConfig = new FlapdoodleRuntimeConfig(version)
                .defaults(Command.MongoD)
                .processOutput(processOutput)
                .daemonProcess(manageProcessInstruction == STOP_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS)
                .build()

        def runtime = MongodStarter.getInstance(runtimeConfig)

        def mongodExecutable = runtime.prepare(mongodConfig)
        println "Starting Mongod ${version.asInDownloadPath()} on port ${pluginExtension.port}..."
        mongodExecutable.start()
        println 'Mongod started.'
    }

    private static class FlapdoodleRuntimeConfig extends RuntimeConfigBuilder {
        private final IVersion version

        FlapdoodleRuntimeConfig(IVersion version) {
            this.version = version
        }

        @Override
        RuntimeConfigBuilder defaults(Command command) {
            super.defaults(command)
            IDownloadConfig downloadConfig = new DownloadConfigBuilder()
                    .defaultsForCommand(command)
                    .progressListener(new CustomFlapdoodleProcessLogger(version))
                    .build()

            artifactStore().overwriteDefault(new ArtifactStoreBuilder().defaults(command).download(downloadConfig).build())
            this
        }
    }

    private static class CustomFlapdoodleProcessLogger implements IProgressListener {
        private final IVersion version

        CustomFlapdoodleProcessLogger(IVersion version) {
            this.version = version
        }

        @Override
        void progress(String label, int percent) {
        }

        @Override
        void done(String label) {
        }

        @Override
        void start(String label) {
            if (label.contains('Download')) {
                println "Downloading Mongo ${version.asInDownloadPath()} distribution..."
            } else if (label.contains('Extract')) {
                println 'Extracting Mongo binaries...'
            }
        }

        @Override
        void info(String label, String message) {
        }
    }

    private static IMongoCmdOptions createMongoCommandOptions(GradleMongoPluginExtension pluginExtension) {
        new MongoCmdOptionsBuilder().useNoJournal(!pluginExtension.journalingEnabled).build()
    }

    private static void addStopMongoDbTask(Project project) {
        project.task(group: TASK_GROUP_NAME, description: 'Stops the local MongoDb instance', 'stopMongoDb') << {
            stopMongoDb(project)
        }
    }

    private static void stopMongoDb(Project project) {
        Mongod.sendShutdown(InetAddress.getLoopbackAddress(), project."$PLUGIN_EXTENSION_NAME".port as Integer)
    }
}

enum ManageProcessInstruction {
    STOP_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS, CONTINUE_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS
}
