package com.sourcemuse.gradle.plugin.flapdoodle.gradle

import static com.sourcemuse.gradle.plugin.flapdoodle.gradle.ManageProcessInstruction.CONTINUE_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS
import static com.sourcemuse.gradle.plugin.flapdoodle.gradle.ManageProcessInstruction.STOP_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS

import java.util.concurrent.atomic.AtomicInteger

import org.bson.Document
import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.TaskState

import com.mongodb.MongoClient
import com.sourcemuse.gradle.plugin.GradleMongoPluginExtension
import com.sourcemuse.gradle.plugin.flapdoodle.adapters.CustomFlapdoodleRuntimeConfig
import com.sourcemuse.gradle.plugin.flapdoodle.adapters.ProcessOutputFactory
import com.sourcemuse.gradle.plugin.flapdoodle.adapters.StorageFactory
import com.sourcemuse.gradle.plugin.flapdoodle.adapters.VersionFactory

import de.flapdoodle.embed.mongo.MongodProcess
import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.ImmutableMongoCmdOptions
import de.flapdoodle.embed.mongo.config.ImmutableMongodConfig;
import de.flapdoodle.embed.mongo.config.Net
import de.flapdoodle.embed.mongo.packageresolver.Command
import de.flapdoodle.embed.mongo.runtime.Mongod
import de.flapdoodle.embed.process.runtime.Network

class GradleMongoPlugin implements Plugin<Project> {

    static final String PLUGIN_EXTENSION_NAME = 'mongo'
    static final String TASK_GROUP_NAME = 'Mongo'

    @Override
    void apply(Project project) {
        configureTaskProperties(project)

        addStartManagedMongoDbTask(project)
        addStartMongoDbTask(project)
        addStopMongoDbTask(project)

        extendAllTasksWithMongoOptions(project)

        project.afterEvaluate {
            configureTasksRequiringMongoDb(project)
        }
    }

    private static void configureTaskProperties(Project project) {
        project.extensions.create(PLUGIN_EXTENSION_NAME, GradleMongoPluginExtension)
        project.getRootProject().extensions.extraProperties.set("mongoPortToProcessMap", new HashMap<Integer, MongodProcess>())
    }

    private static void addStartManagedMongoDbTask(Project project) {
        project.task(group: TASK_GROUP_NAME, description: 'Starts a local MongoDb instance which will stop when the build process completes', 'startManagedMongoDb').doFirst {
            def mongoStartedByTask = startMongoDb(project, STOP_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS)
			
            if (mongoStartedByTask) {
                ensureMongoDbStopsEvenIfGradleDaemonIsRunning(project)
            }
        }
    }

    private static void addStartMongoDbTask(Project project) {
        project.task(group: TASK_GROUP_NAME, description: 'Starts a local MongoDb instance', 'startMongoDb').doFirst {
            startMongoDb(project, CONTINUE_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS)
        }
    }

    private static void ensureMongoDbStopsEvenIfGradleDaemonIsRunning(Project project) {
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

    private static boolean startMongoDb(Project project, ManageProcessInstruction manageProcessInstruction) {
        def pluginExtension = project[PLUGIN_EXTENSION_NAME] as GradleMongoPluginExtension
        return startMongoDb(pluginExtension, project, manageProcessInstruction)
    }

    private
    static boolean startMongoDb(GradleMongoPluginExtension pluginExtension, Project project, ManageProcessInstruction manageProcessInstruction) {
        if (mongoInstanceAlreadyRunning(pluginExtension.bindIp, pluginExtension.port)) {
            println "Mongo instance already running at ${pluginExtension.bindIp}:${pluginExtension.port}. Reusing."
            return false
        }

        def processOutput = new ProcessOutputFactory(project).getProcessOutput(pluginExtension)
        def version = new VersionFactory().getVersion(pluginExtension)
        def storage = new StorageFactory().getStorage(pluginExtension)

        def configBuilder = ImmutableMongodConfig.builder()
                .cmdOptions(createMongoCommandOptions(pluginExtension))
                .version(version)
                .replication(storage)
                .net(new Net(pluginExtension.bindIp, pluginExtension.port, Network.localhostIsIPv6()))

        pluginExtension.args.each { k, v ->
            if (!v)
                configBuilder.putArgs("--${k}", null)
            else
                configBuilder.putArgs("--${k}", v)
        }

        pluginExtension.params.each { k, v -> configBuilder.putParams(k, v) }

        def mongodConfig = configBuilder.build()

        def runtimeConfig = new CustomFlapdoodleRuntimeConfig(version,
                pluginExtension.mongodVerbosity,
                pluginExtension.downloadUrl,
                pluginExtension.proxyHost,
                pluginExtension.proxyPort,
                pluginExtension.artifactStorePath)
                .defaults(Command.MongoD)
                .processOutput(processOutput)
                .isDaemonProcess(manageProcessInstruction == STOP_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS)
                .build()

        def runtime = MongodStarter.getInstance(runtimeConfig)

        def mongodExecutable = runtime.prepare(mongodConfig)
        println "Starting Mongod ${version.asInDownloadPath()} on port ${pluginExtension.port}..."
        def mongoProc = mongodExecutable.start()
        println 'Mongod started.'
        project.rootProject.mongoPortToProcessMap.put(pluginExtension.port, mongoProc)
        return true
    }

    private static boolean mongoInstanceAlreadyRunning(String bindIp, int port) {
        if (isPortAvailable(bindIp, port)) {
            return false
        }

        try {
            def mongoClient = new MongoClient(bindIp, port)
            mongoClient.getDatabase('test').runCommand(new Document(buildinfo: 1))
        } catch (Throwable ignored) {
            return false
        }
        return true
    }

    private static boolean isPortAvailable(String host, int port) {
        Socket socket = null
        try {
            socket = new Socket(host, port)
            return false
        } catch (IOException ignored) {
            return true
        } finally {
            try {
                socket.close()
            } catch (Throwable ignored) {
            }
        }
    }

    private static ImmutableMongoCmdOptions createMongoCommandOptions(GradleMongoPluginExtension pluginExtension) {
        def mongoCommandOptionsBuilder = ImmutableMongoCmdOptions.builder()
                .useNoJournal(!pluginExtension.journalingEnabled)
                .storageEngine(pluginExtension.storageEngine)
                .auth(pluginExtension.auth)

        if (pluginExtension.syncDelay != null){
            mongoCommandOptionsBuilder.syncDelay(pluginExtension.syncDelay)
        } else {
            mongoCommandOptionsBuilder.useDefaultSyncDelay(true)
        }

        mongoCommandOptionsBuilder.build()
    }

    private static void addStopMongoDbTask(Project project) {
        project.task(group: TASK_GROUP_NAME, description: 'Stops the local MongoDb instance', 'stopMongoDb').doFirst {
            stopMongoDb(project)
        }
    }

    private static void stopMongoDb(Project project) {
        def port = project."$PLUGIN_EXTENSION_NAME".port as Integer
        def proc = project.rootProject.mongoPortToProcessMap.remove(port) as MongodProcess
        stopMongoDb(port, proc)
    }

    private static void stopMongoDb(int port, MongodProcess proc) {
        println "Shutting-down Mongod on port ${port}."
        def force = (proc == null)

        try {
            proc?.stop()
        } catch (ignored) {
            force = true
        }

        if (force && !Mongod.sendShutdown(InetAddress.getLoopbackAddress(), port)) {
            println "Could not shut down mongo, is access control enabled?"
        }
    }

    private static void extendAllTasksWithMongoOptions(Project project) {
        project.tasks.each {
            extend(it)
        }

        project.tasks.whenTaskAdded {
            extend(it)
        }
    }

    private static void extend(Task task) {
        task.ext.runWithMongoDb = false
        task.extensions.add(PLUGIN_EXTENSION_NAME, GradleMongoPluginExtension)
    }

    private static Iterable<Task> configureTasksRequiringMongoDb(Project project) {
        project.tasks.each {
            def task = it
            if (task.runWithMongoDb) {
                def rootProject = project.getRootProject()
                def mergedPluginExtension = getTaskSpecificMongoConfiguration(task, project)
                def port = mergedPluginExtension.port

                task.doFirst {
                    synchronized (rootProject) {
                        ensureMongoTaskTrackingPropertiesAreSet(rootProject)
                        def mongoDependencyCount = rootProject.mongoTaskDependenciesCountByPort.get(port).getAndIncrement()
                        def mongoStartedByTask = startMongoDb(mergedPluginExtension, project, STOP_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS)
                        if (mongoDependencyCount == 0) {
                           rootProject.mongoInstancesStartedDuringBuild.put(port, mongoStartedByTask)
                        }
                    }
                }
            }
        }
		
		project.gradle.addBuildListener(new BuildListener() {
		
			@Override
			public void buildFinished(BuildResult buildResult) {
				buildResult.gradle.rootProject.tasks.each {
					TaskState state = it.state
					if (it.runWithMongoDb && state.didWork) {
						def rootProject = it.project
						def mergedPluginExtension = getTaskSpecificMongoConfiguration(it, project)
						def port = mergedPluginExtension.port
						synchronized (rootProject) {
							def mongoDependencyCount = rootProject.mongoTaskDependenciesCountByPort.get(port).decrementAndGet()
							if (mongoDependencyCount == 0 && rootProject.mongoInstancesStartedDuringBuild.get(port)) {
								stopMongoDb(port, rootProject.mongoPortToProcessMap.remove(port))
							}
						}
					}
				}
			}
		
			@Override
			public void projectsEvaluated(Gradle gradle) {}
		
			@Override
			public void projectsLoaded(Gradle gradle) {}
		
			@Override
			public void settingsEvaluated(Settings gradle) {}
							
		})
    }

    private static void ensureMongoTaskTrackingPropertiesAreSet(Project rootProject) {
        if (!rootProject.extensions.extraProperties.has("mongoTaskDependenciesCountByPort")) {
            rootProject.extensions.extraProperties.set("mongoTaskDependenciesCountByPort", new HashMap<Integer, AtomicInteger>().withDefault {
                new AtomicInteger()
            })
            rootProject.extensions.extraProperties.set("mongoInstancesStartedDuringBuild", new HashMap<>().withDefault {
                false
            })
        }
    }

    private static GradleMongoPluginExtension getTaskSpecificMongoConfiguration(Task task, Project project) {
        def projectPluginExtension = project[PLUGIN_EXTENSION_NAME] as GradleMongoPluginExtension
        def taskPluginExtension = task.extensions.findByType(GradleMongoPluginExtension)
        projectPluginExtension.overrideWith(taskPluginExtension)
    }
}

enum ManageProcessInstruction {
    STOP_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS, CONTINUE_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS
}
