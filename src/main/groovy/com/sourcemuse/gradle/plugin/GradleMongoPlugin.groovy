package com.sourcemuse.gradle.plugin

import static com.sourcemuse.gradle.plugin.ManageProcessInstruction.*

import org.gradle.api.Plugin
import org.gradle.api.Project

import de.flapdoodle.embed.mongo.Command
import de.flapdoodle.embed.mongo.MongodExecutable
import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.IMongodConfig
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder
import de.flapdoodle.embed.mongo.config.Net
import de.flapdoodle.embed.mongo.config.RuntimeConfigBuilder
import de.flapdoodle.embed.mongo.distribution.IFeatureAwareVersion
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.runtime.Mongod
import de.flapdoodle.embed.process.config.io.ProcessOutput
import de.flapdoodle.embed.process.runtime.Network

class GradleMongoPlugin implements Plugin<Project> {

    static final String PLUGIN_EXTENSION_NAME = 'mongo'
    static final String TASK_GROUP_NAME = 'Mongo'
    static final String LATEST_VERSION = '-LATEST'

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
        
        def version = getVersion(pluginExtension)
        IMongodConfig mongodConfig = new MongodConfigBuilder()
                .version(version)
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
    
    private IFeatureAwareVersion getVersion(GradleMongoPluginExtension pluginExtension) {
        def mongoVersion = pluginExtension.mongoVersion
        try {
            // Start by just trying the value as-is with Main (to pick up DEV, PROD, etc...)
            Version.Main.valueOf(mongoVersion)
        } catch (IllegalArgumentException e) {
            // At this point we must have a dotted version, add 'V' and switch to '_'
            mongoVersion = 'V' + mongoVersion
            mongoVersion = mongoVersion.replace('.', '_')
            
            // Do we want to latest version or not
            if (mongoVersion.endsWith(LATEST_VERSION)) {
                mongoVersion = 
                    mongoVersion.substring(0, mongoVersion.length() - LATEST_VERSION.length())
                Version.Main.valueOf(mongoVersion)
                
            } else {
                Version.valueOf(mongoVersion)
            }
        }
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
