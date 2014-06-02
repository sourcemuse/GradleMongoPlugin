package com.sourcemuse.gradle.plugin

import static com.sourcemuse.gradle.plugin.BuildScriptBuilder.DEFAULT_MONGOD_PORT
import static com.sourcemuse.gradle.plugin.MongoUtils.mongoInstanceRunning

import org.gradle.api.Plugin
import org.gradle.api.Project

class TestPlugin implements Plugin<Project> {

    static final TEST_STOP_MONGO_DB = 'testStopMongoDb'
    static final TEST_START_MONGO_DB = 'testStartMongoDb'
    static final TEST_START_MANAGED_MONGO_DB = 'testStartManagedMongoDb'
    static final MONGO_RUNNING_FLAG = 'mongo running!'

    @Override
    void apply(Project project) {
        project.task(dependsOn: 'startManagedMongoDb', TEST_START_MANAGED_MONGO_DB) << {
            if (mongoInstanceRunning(DEFAULT_MONGOD_PORT))
                println MONGO_RUNNING_FLAG
        }
        project.task(dependsOn: 'startMongoDb', TEST_START_MONGO_DB) << {
            if (mongoInstanceRunning(DEFAULT_MONGOD_PORT))
                println MONGO_RUNNING_FLAG
        }
        project.task(dependsOn: ['startMongoDb', 'stopMongoDb'], TEST_STOP_MONGO_DB)
    }
}
