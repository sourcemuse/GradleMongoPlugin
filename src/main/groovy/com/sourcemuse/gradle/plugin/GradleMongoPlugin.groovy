package com.sourcemuse.gradle.plugin

import de.flapdoodle.embed.mongo.Command
import de.flapdoodle.embed.mongo.MongodExecutable
import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.*
import de.flapdoodle.embed.mongo.distribution.IFeatureAwareVersion
import de.flapdoodle.embed.mongo.runtime.Mongod
import de.flapdoodle.embed.process.config.io.ProcessOutput
import de.flapdoodle.embed.process.runtime.Network
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.slf4j.Logger

import static com.sourcemuse.gradle.plugin.ManageProcessInstruction.CONTINUE_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS
import static com.sourcemuse.gradle.plugin.ManageProcessInstruction.STOP_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS

class GradleMongoPlugin implements Plugin<Project> {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(GradleMongoPlugin.class)

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
        ProcessOutput processOutput = new LoggerFactory(project).getLogger(pluginExtension)
        IFeatureAwareVersion version = new VersionFactory().getVersion(pluginExtension)
        Storage storage = new StorageFactory().getStorage(pluginExtension)

        if (pluginExtension.randomPort) {
            pluginExtension.port = PortUtils.allocateRandomPort()
            log.info('Selected random MongoDB port: {}', pluginExtension.port)
        }

        IMongodConfig mongodConfig = new MongodConfigBuilder()
                .version(version)
                .replication(storage)
                .net(new Net(pluginExtension.bindIp, pluginExtension.port, Network.localhostIsIPv6()))
                .build();


        def runtimeConfig = new RuntimeConfigBuilder()
                .defaults(Command.MongoD)
                .processOutput(processOutput)
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
}

enum ManageProcessInstruction {
    STOP_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS, CONTINUE_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS
}
