package com.sourcemuse.gradle.plugin

import static com.sourcemuse.gradle.plugin.MongoUtils.mongoInstanceRunningOnConfiguredPort

import org.gradle.api.Plugin
import org.gradle.api.Project

class PluginForTests implements Plugin<Project> {

    static final TEST_STOP_MONGO_DB = 'testStopMongoDb'
    static final TEST_START_MONGO_DB = 'testStartMongoDb'
    static final TEST_START_MANAGED_MONGO_DB = 'testStartManagedMongoDb'
    static final MONGO_RUNNING_FLAG = 'mongo running!'
    static final MONGO_NOT_RUNNING_FLAG = 'mongo not running!'

    @Override
    void apply(Project project) {
        def performMongoCheck = {
            println(mongoInstanceRunningOnConfiguredPort(project) ? MONGO_RUNNING_FLAG : MONGO_NOT_RUNNING_FLAG)
        }
        project.task(dependsOn: 'startManagedMongoDb', TEST_START_MANAGED_MONGO_DB) << performMongoCheck
        project.task(dependsOn: 'startMongoDb', TEST_START_MONGO_DB) << performMongoCheck
        project.task(dependsOn: ['startMongoDb', 'stopMongoDb'], TEST_STOP_MONGO_DB)
    }
}
