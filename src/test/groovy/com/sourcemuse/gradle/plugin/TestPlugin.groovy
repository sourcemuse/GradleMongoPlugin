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
            def port = project.mongo.port ?: DEFAULT_MONGOD_PORT
            if (mongoInstanceRunning(port))
                println MONGO_RUNNING_FLAG + port
        }
        project.task(dependsOn: 'startMongoDb', TEST_START_MONGO_DB) << {
            def port = project.mongo.port ?: DEFAULT_MONGOD_PORT
            if (mongoInstanceRunning(port))
                println MONGO_RUNNING_FLAG + port
        }
        project.task(dependsOn: ['startMongoDb', 'stopMongoDb'], TEST_STOP_MONGO_DB)
    }

    static Integer findPortInOutput(String buildStandardOut) {
        def portMatcher = buildStandardOut =~ "(?i)${MONGO_RUNNING_FLAG}([0-9]+)"
        def foundPort = portMatcher.find()
        return foundPort ? Integer.valueOf(portMatcher.group(1)) : null
    }
}
