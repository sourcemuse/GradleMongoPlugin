package org.roberttaylor.gradle.plugin

import de.flapdoodle.embed.mongo.Command
import de.flapdoodle.embed.mongo.MongodExecutable
import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.IMongodConfig
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder
import de.flapdoodle.embed.mongo.config.Net
import de.flapdoodle.embed.mongo.config.RuntimeConfigBuilder
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.runtime.Mongod
import de.flapdoodle.embed.process.runtime.Network
import org.gradle.api.Plugin
import org.gradle.api.Project

import static org.roberttaylor.gradle.plugin.ManageProcessInstruction.CONTINUE_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS
import static org.roberttaylor.gradle.plugin.ManageProcessInstruction.STOP_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS

class EmbedMongoDbPlugin implements Plugin<Project> {

    static final String PLUGIN_EXTENSION_NAME = 'mongodb'
    static final String TASK_GROUP_NAME = 'MongoDb'

    @Override
    void apply(Project project) {
        configureTaskProperties(project)

        addStartUnmanagedMongoDbTask(project)
        addStartMongoDbTask(project)
        addStopMongoDbTask(project)
    }

    private void configureTaskProperties(Project project) {
        project.extensions.create(PLUGIN_EXTENSION_NAME, EmbedMongoDbPluginExtension)
    }

    private void addStartUnmanagedMongoDbTask(Project project) {
        project.task(group: TASK_GROUP_NAME, description: 'Starts a local MongoDb instance', 'startMongoDb') << {
            startMongoDb(CONTINUE_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS)
        }
    }

    private void startMongoDb(ManageProcessInstruction manageProcessInstruction) {
        IMongodConfig mongodConfig = new MongodConfigBuilder()
                .version(Version.Main.PRODUCTION)
                .net(new Net(project.(PLUGIN_EXTENSION_NAME).port, Network.localhostIsIPv6()))
                .build();

        MongodStarter runtime = MongodStarter.getInstance(new RuntimeConfigBuilder().defaults(Command.MongoD).daemonProcess(manageProcessInstruction == STOP_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS));

        MongodExecutable mongodExecutable = runtime.prepare(mongodConfig);
        mongodExecutable.start();
    }

    private void addStartMongoDbTask(Project project) {
        project.task(group: TASK_GROUP_NAME, description: 'Starts a local MongoDb instance which will stop when the build process completes', 'startManagedMongoDb') << {
            startMongoDb(STOP_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS)

            addStopMongoDbTaskIfNotPresent(project)
        }
    }

    private void addStopMongoDbTaskIfNotPresent(Project project) {
        boolean stopMongoDbTaskPresent = project.gradle.taskGraph.allTasks.find { it.name == 'stopMongoDb' }

        if (!stopMongoDbTaskPresent) {
            def lastTask = project.gradle.taskGraph.allTasks[-1]
            project.gradle.taskGraph.afterTask { task, taskState ->
                if (task == lastTask) {
                    stopMongoDb()
                }
            }
        }
    }

    private void addStopMongoDbTask(Project project) {
        project.task(group: TASK_GROUP_NAME, description: 'Stops the local MongoDb instance', 'stopMongoDb') << {
            stopMongoDb()
        }
    }

    private void stopMongoDb() {
        Mongod.sendShutdown(InetAddress.localHost, project.(PLUGIN_EXTENSION_NAME).port)
    }
}

enum ManageProcessInstruction {
    STOP_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS, CONTINUE_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS
}

class EmbedMongoDbPluginExtension {
    def port = 27017
}
