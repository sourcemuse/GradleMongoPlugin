package com.sourcemuse.gradle.plugin

import static com.sourcemuse.gradle.plugin.ManageProcessInstruction.CONTINUE_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS
import static com.sourcemuse.gradle.plugin.ManageProcessInstruction.STOP_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS

import de.flapdoodle.embed.mongo.Command
import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.IMongoCmdOptions
import de.flapdoodle.embed.mongo.config.MongoCmdOptionsBuilder
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder
import de.flapdoodle.embed.mongo.config.Net
import de.flapdoodle.embed.mongo.config.RuntimeConfigBuilder
import de.flapdoodle.embed.mongo.runtime.Mongod
import de.flapdoodle.embed.process.runtime.Network
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.tasks.TaskState

class GradleMongoPlugin implements Plugin<Project> {

    static final String PLUGIN_EXTENSION_NAME = 'mongo'
    static final String TASK_GROUP_NAME = 'Mongo'

    @Override
    void apply(Project project) {
        configureTaskProperties(project)

        addStartManagedMongoDbTask(project)
        addStartMongoDbTask(project)
        addStopMongoDbTask(project)

        extendAllTasksWithMongoDependencyFunction(project)
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
        GradleMongoPluginExtension pluginExtension = project[PLUGIN_EXTENSION_NAME]
        def processOutput = new LoggerFactory(project).getLogger(pluginExtension)
        def version = new VersionFactory().getVersion(pluginExtension)
        def storage = new StorageFactory().getStorage(pluginExtension)

        def mongodConfig = new MongodConfigBuilder()
                .cmdOptions(createMongoCommandOptions(pluginExtension))
                .version(version)
                .replication(storage)
                .net(new Net(pluginExtension.bindIp, pluginExtension.port, Network.localhostIsIPv6()))
                .build();

        def runtimeConfig = new RuntimeConfigBuilder()
                .defaults(Command.MongoD)
                .processOutput(processOutput)
                .daemonProcess(manageProcessInstruction == STOP_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS)
                .build()

        def runtime = MongodStarter.getInstance(runtimeConfig);

        def mongodExecutable = runtime.prepare(mongodConfig);
        mongodExecutable.start();
    }

    private static IMongoCmdOptions createMongoCommandOptions(GradleMongoPluginExtension pluginExtension) {
        new MongoCmdOptionsBuilder().useNoJournal(!pluginExtension.journalingEnabled).build()
    }

    private void addStopMongoDbTask(Project project) {
        project.task(group: TASK_GROUP_NAME, description: 'Stops the local MongoDb instance', 'stopMongoDb') << {
            stopMongoDb(project)
        }
    }

    private void stopMongoDb(Project project) {
        Mongod.sendShutdown(InetAddress.getLoopbackAddress(), project."$PLUGIN_EXTENSION_NAME".port)
    }

    private void extendAllTasksWithMongoDependencyFunction(Project project) {
        project.tasks.each {
            extend(it)
        }

        project.tasks.whenTaskAdded {
            extend(it)
        }
    }

    private void extend(Task specifiedTask) {
        def withMongoDbClosure = {
            def project = specifiedTask.project
            specifiedTask.doFirst { startMongoDb(project, STOP_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS) }
            project.gradle.taskGraph.addTaskExecutionListener(new TaskExecutionListener() {
                @Override
                void beforeExecute(Task task) {
                }

                @Override
                void afterExecute(Task task, TaskState state) {
                    if (task == specifiedTask && state.didWork) {
                        stopMongoDb(project)
                    }
                }
            })
        }
        specifiedTask.extensions.withMongoDbRunning = withMongoDbClosure
    }
}

enum ManageProcessInstruction {
    STOP_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS, CONTINUE_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS
}
