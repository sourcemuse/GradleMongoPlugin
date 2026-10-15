package com.sourcemuse.gradle.plugin.flapdoodle.gradle

import com.mongodb.MongoCommandException
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoDatabase
import com.sourcemuse.gradle.plugin.GradleMongoPluginExtension
import com.sourcemuse.gradle.plugin.flapdoodle.adapters.ProcessOutputFactory
import com.sourcemuse.gradle.plugin.flapdoodle.adapters.VersionFactory
import de.flapdoodle.embed.mongo.commands.ImmutableMongodArguments
import de.flapdoodle.embed.mongo.commands.MongodArguments
import de.flapdoodle.embed.mongo.config.Net
import de.flapdoodle.embed.mongo.transitions.ImmutableMongod
import de.flapdoodle.embed.mongo.transitions.Mongod
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess
import de.flapdoodle.embed.mongo.types.DatabaseDir
import de.flapdoodle.embed.mongo.types.DistributionBaseUrl
import de.flapdoodle.embed.process.config.DownloadConfig
import de.flapdoodle.embed.process.io.directories.PersistentDir
import de.flapdoodle.embed.process.runtime.Network
import de.flapdoodle.embed.process.transitions.DownloadPackage
import de.flapdoodle.embed.process.types.ProcessConfig
import de.flapdoodle.net.ProxyFactory
import de.flapdoodle.reverse.Transition
import de.flapdoodle.reverse.transitions.Start
import org.bson.Document
import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.TaskState

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

import static com.sourcemuse.gradle.plugin.flapdoodle.gradle.ManageProcessInstruction.CONTINUE_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS
import static com.sourcemuse.gradle.plugin.flapdoodle.gradle.ManageProcessInstruction.STOP_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS

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
        project.getRootProject().extensions.extraProperties.set("mongoPortToProcessMap", new HashMap<Integer, RunningMongodProcess>())
        project.getRootProject().extensions.extraProperties.set("mongoPortToTempStorage", new HashMap<Integer, Path>())
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

			project.gradle.addBuildListener(new BuildListener() {
				@Override
				void buildFinished(BuildResult buildResult) {
					buildResult.gradle.rootProject.tasks.each {
						if (it == lastTask) {
							stopMongoDb(project)
						}
					}
				}

				@Override
				void projectsEvaluated(Gradle gradle) {}

				@Override
				void projectsLoaded(Gradle gradle) {}

				@Override
				void settingsEvaluated(Settings gradle) {}
			})
        }
    }

    private static boolean startMongoDb(Project project, ManageProcessInstruction manageProcessInstruction) {
        def pluginExtension = project[PLUGIN_EXTENSION_NAME] as GradleMongoPluginExtension
        return startMongoDb(pluginExtension, project, manageProcessInstruction)
    }

    private static synchronized boolean startMongoDb(GradleMongoPluginExtension pluginExtension, Project project, ManageProcessInstruction manageProcessInstruction) {
        if (mongoInstanceAlreadyRunning(pluginExtension.bindIp, pluginExtension.port)) {
            println "Mongo instance already running at ${pluginExtension.bindIp}:${pluginExtension.port}. Reusing."
            return false
        }

        def version = new VersionFactory().getVersion(pluginExtension)

        ImmutableMongod.Builder builder = Mongod.builder()
        builder.processOutput(new ProcessOutputFactory(project, pluginExtension))
        builder.net(
          Start.to(Net.class)
            .initializedWith(Net.of(pluginExtension.bindIp, pluginExtension.port, Network.localhostIsIPv6()))
        )

        builder.mongodArguments(createMongoCommandOptions(pluginExtension))

        builder.processConfig(
          Start.to(ProcessConfig.class)
            .initializedWith(ProcessConfig.defaults().withDaemonProcess(manageProcessInstruction == STOP_MONGO_PROCESS_WHEN_BUILD_PROCESS_STOPS))
        )

        Path storageLocation
        if (pluginExtension.storageLocation == GradleMongoPluginExtension.EPHEMERAL_TEMPORARY_FOLDER) {
            storageLocation = Files.createTempDirectory("$PLUGIN_EXTENSION_NAME-")
            storageLocation.toFile().deleteOnExit()
            project.rootProject.mongoPortToTempStorage.put(pluginExtension.port, storageLocation)
        } else {
            storageLocation = Path.of(pluginExtension.storageLocation)
        }
        builder.databaseDir(
          Start.to(DatabaseDir.class)
            .initializedWith(DatabaseDir.of(storageLocation))
        )

        if (pluginExtension.artifactStorePath) {
            builder.persistentBaseDir(
              Start.to(PersistentDir.class)
                .initializedWith(PersistentDir.of(Path.of(pluginExtension.artifactStorePath)))
            )
        }

        if (pluginExtension.downloadUrl) {
            builder.distributionBaseUrl(
              Start.to(DistributionBaseUrl.class)
                .initializedWith(DistributionBaseUrl.of(pluginExtension.downloadUrl))
            )
        }

        if (pluginExtension.proxyHost) {
            builder.downloadPackage(
              DownloadPackage.withDefaults()
              .withDownloadConfig(
                DownloadConfig.defaults()
                  .withProxyFactory(ProxyFactory.of(pluginExtension.proxyHost, pluginExtension.proxyPort))
              )
            )
        }

        println "Starting Mongod ${version.asInDownloadPath()} on port ${pluginExtension.port}..."
        RunningMongodProcess running = builder.build().start(version).current()
        println 'Mongod started.'
        project.rootProject.mongoPortToProcessMap.put(pluginExtension.port, running)

        if (pluginExtension.replicaSet != null) {
            println 'Initializing replica set...'
            setupReplicaSet(pluginExtension.bindIp, pluginExtension.port)
            println 'Mongod is writable'
        }

        return true
    }

    private static boolean mongoInstanceAlreadyRunning(String bindIp, int port) {
        if (isPortAvailable(bindIp, port)) {
            return false
        }

        try {
            MongoClients.create("mongodb://${bindIp}:${port}").withCloseable {
                it.getDatabase('test').runCommand(new Document(buildinfo: 1))
            }
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

    private static void setupReplicaSet(String host, int port) {
        MongoClients.create("mongodb://${host}:${port}").withCloseable {
            MongoDatabase adminDB = it.getDatabase("admin")

            try {
                adminDB.runCommand(new Document("replSetInitiate", new Document()))
            } catch (MongoCommandException e) {
                if (e.getErrorCode() != 23) { // ignore AlreadyInitialized errors
                    throw e
                }
            }

            // wait until replica set (this node) is writable
            Instant stop = Instant.now().plusSeconds(10)
            while (!adminDB.runCommand(new Document("isMaster", 1)).getBoolean("ismaster") && Instant.now().isBefore(stop)) {
                try {
                    //noinspection BusyWait
                    Thread.sleep(100)
                } catch (InterruptedException e) {
                    throw new RuntimeException(e)
                }
            }

            if (Instant.now().isAfter(stop)) {
                throw new RuntimeException("Current node is not master/primary after 10s")
            }
        }
    }

    private static Transition<MongodArguments> createMongoCommandOptions(GradleMongoPluginExtension pluginExtension) {
        ImmutableMongodArguments.Builder builder = MongodArguments.builder()

        if (pluginExtension.mongodVerbosity) {
            // is already sanitized by GradleMongoPluginExtension#parseMongodVerbosity
            builder.putArgs(pluginExtension.mongodVerbosity, "")
        }

        builder.useNoJournal(!pluginExtension.journalingEnabled)
        builder.storageEngine(pluginExtension.storageEngine)
        builder.auth(pluginExtension.auth)

        if (pluginExtension.syncDelay != null){
            builder.syncDelay(pluginExtension.syncDelay)
        } else {
            builder.useDefaultSyncDelay(true)
        }

        if (pluginExtension.replicaSet != null) {
            builder.putArgs("--replSet", pluginExtension.replicaSet)
        }

        if (pluginExtension.args != null && pluginExtension.args.size() > 0) {
            pluginExtension.args.each {k, v ->
                // automatically add - or -- to stay compatible with old plugin versions
                if (!k.startsWith("-")) {
                    if (k.length() == 1) {
                        k = "-" + k
                    } else {
                        k = "--" + k
                    }
                }
                builder.putArgs(k, v)
            }
        }
        if (pluginExtension.params != null && pluginExtension.params.size() > 0) {
            builder.putAllParams(pluginExtension.params)
        }

        return Start.to(MongodArguments.class).initializedWith(builder.build())
    }

    private static void addStopMongoDbTask(Project project) {
        project.task(group: TASK_GROUP_NAME, description: 'Stops the local MongoDb instance', 'stopMongoDb').doFirst {
            stopMongoDb(project)
        }
    }

    private static void stopMongoDb(Project project) {
        def port = project."$PLUGIN_EXTENSION_NAME".port as Integer
        def proc = project.rootProject.mongoPortToProcessMap.remove(port) as RunningMongodProcess
        def tempStorage = project.rootProject.mongoPortToTempStorage.remove(port) as Path
        stopMongoDb(port, proc, tempStorage)
    }

    private static void stopMongoDb(int port, RunningMongodProcess proc, Path tempStorage) {
        println "Shutting-down Mongod on port ${port}."
        def force = (proc == null)

        try {
            proc?.stop()
        } catch (ignored) {
            force = true
        }

        if (force && !de.flapdoodle.embed.mongo.runtime.Mongod.sendShutdown(InetAddress.getLoopbackAddress(), port)) {
            println "Could not shut down mongo, is access control enabled?"
        }

        if (tempStorage != null) {
            tempStorage.deleteDir()
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

    private static void configureTasksRequiringMongoDb(Project project) {
        project.tasks.each {
            def rootProject = project.getRootProject()
            def task = it
            if (task.runWithMongoDb) {
                synchronized (rootProject) {
                    ensureMongoTaskTrackingPropertiesAreSet(rootProject)
                    rootProject.tasksRequiringMongo.add(task.getPath())
                }

                task.doFirst {
                    def mergedPluginExtension = getTaskSpecificMongoConfiguration(task, project)
                    def port = mergedPluginExtension.port
                    synchronized (rootProject) {
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
            void buildFinished(BuildResult buildResult) {
                buildResult.gradle.getTaskGraph().getAllTasks().each {
                    def rootProject = it.project.getRootProject()
                    TaskState state = it.state

                    synchronized (rootProject) {
                        ensureMongoTaskTrackingPropertiesAreSet(rootProject)
                        if (rootProject.tasksRequiringMongo.contains(it.getPath()) && state.didWork) {
                            def mergedPluginExtension = getTaskSpecificMongoConfiguration(it, project)
                            def port = mergedPluginExtension.port
                            def mongoDependencyCount = rootProject.mongoTaskDependenciesCountByPort.get(port).decrementAndGet()
                            if (mongoDependencyCount == 0 && rootProject.mongoInstancesStartedDuringBuild.get(port)) {
                                stopMongoDb(port, rootProject.mongoPortToProcessMap.remove(port), rootProject.mongoPortToTempStorage.remove(port))
                            }
                        }
                    }
				        }
			      }

            @Override
            void projectsEvaluated(Gradle gradle) {}

            @Override
            void projectsLoaded(Gradle gradle) {}

			      @Override
			      void settingsEvaluated(Settings gradle) {}
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
            rootProject.extensions.extraProperties.set("tasksRequiringMongo", new HashSet())
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
