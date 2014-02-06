package org.roberttaylor.gradle.plugin

import de.flapdoodle.embed.mongo.MongodExecutable
import de.flapdoodle.embed.mongo.MongodProcess
import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.IMongodConfig
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder
import de.flapdoodle.embed.mongo.config.Net
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.process.runtime.Network
import org.gradle.api.Plugin
import org.gradle.api.Project

class EmbedMongoDbPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.extensions.create('mongodb', EmbedMongoDbPluginExtension)

        project.task(group: 'MongoDb', description: 'Starts a local MongoDb instance', 'startMongoDb') << {
            IMongodConfig mongodConfig = new MongodConfigBuilder()
                    .version(Version.Main.PRODUCTION)
                    .net(new Net(project.mongodb.port, Network.localhostIsIPv6()))
                    .build();

            MongodStarter runtime = MongodStarter.getDefaultInstance();

            MongodExecutable mongodExecutable = runtime.prepare(mongodConfig);
            def process = mongodExecutable.start();

            boolean stopMongoDbTaskPresent = project.gradle.taskGraph.allTasks.find { it.name == 'stopMongoDb' }

            if (stopMongoDbTaskPresent) {
                project.ext.mongoDbProcess = process
            } else {
                def lastTask = project.gradle.taskGraph.allTasks[-1]
                project.gradle.taskGraph.afterTask { task, taskState ->
                    if (task == lastTask) {
                        process.stop()
                    }
                }
            }
        }
        project.task(group: 'MongoDb', description: 'Stops the local MongoDb instance', 'stopMongoDb') << {
            MongodProcess process = (MongodProcess)project.ext.mongoDbProcess
            process.stop()
        }
    }
}

class EmbedMongoDbPluginExtension {
    def port = 27017
}
