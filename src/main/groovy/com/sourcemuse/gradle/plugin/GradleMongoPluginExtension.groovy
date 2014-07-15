package com.sourcemuse.gradle.plugin

import static com.sourcemuse.gradle.plugin.LogDestination.FILE

class GradleMongoPluginExtension {

    static final EPHEMERAL_TEMPORARY_FOLDER = null

    int port = 27017
    String bindIp = '127.0.0.1'
    String logging = FILE as String
    String logFilePath = 'embedded-mongo.log'
    String mongoVersion = 'PRODUCTION'
    String storageLocation = EPHEMERAL_TEMPORARY_FOLDER
}
