package com.sourcemuse.gradle.plugin

class BuildScriptBuilder {

    static final DEFAULT_MONGOD_PORT = 27017
    static final TEST_STOP_MONGO_DB = 'testStopMongoDb'
    static final TEST_START_MONGO_DB = 'testStartMongoDb'
    static final TEST_START_MANAGED_MONGO_DB = 'testStartManagedMongoDb'
    static final MONGO_RUNNING_FLAG = 'mongo running!'
    static final MONGO_NOT_RUNNING_FLAG = 'mongo not running!'

    Map<String, Object> configProperties = [:]
    
    static BuildScriptBuilder buildScript() {
        new BuildScriptBuilder()
    }

    String build() {
        def mongoConfigBlock = new MongoPluginConfigBlock()

        configProperties.each { name, value ->
            mongoConfigBlock.addPropertyConfig(name, value as String)
        }

        """
            |import static com.sourcemuse.gradle.plugin.flapdoodle.gradle.GradleMongoPlugin.mongoInstanceAlreadyRunning
            |
            |plugins { id 'com.sourcemuse.mongo' }
            |
            |def performMongoCheck = {
            |   if (mongoInstanceAlreadyRunning(mongo.bindIp, mongo.port))
            |       println "${MONGO_RUNNING_FLAG}"
            |   else
            |       println "${MONGO_NOT_RUNNING_FLAG}"
            |}
            |task ${TEST_START_MANAGED_MONGO_DB} (dependsOn: startManagedMongoDb) { doLast performMongoCheck }
            |task ${TEST_START_MONGO_DB} (dependsOn: startMongoDb) { doLast performMongoCheck }
            |task ${TEST_STOP_MONGO_DB} (dependsOn: [startMongoDb, stopMongoDb])
            |
            |${mongoConfigBlock}
        """.stripMargin()
    }

    BuildScriptBuilder withPort(int port) {
        configProperties.port = port
        this
    }

    BuildScriptBuilder withLogging(String logging) {
        configProperties.logging = asStringProperty(logging)
        this
    }

    BuildScriptBuilder withFilePath(String filePath) {
        configProperties.logFilePath = asStringProperty(filePath.replace('\\', '\\\\'))
        this
    }

    BuildScriptBuilder withMongoVersion(String version) {
        configProperties.mongoVersion = asStringProperty(version)
        this
    }

    BuildScriptBuilder withStorageEngine(String storageEngine) {
        configProperties.storageEngine = asStringProperty(storageEngine)
        this
    }

    BuildScriptBuilder withStorageLocation(String storage) {
        configProperties.storageLocation = asStringProperty(storage.replace('\\', '\\\\'))
        this
    }

    BuildScriptBuilder withJournalingEnabled() {
        configProperties.journalingEnabled = true
        this
    }

    BuildScriptBuilder withVerboseLogging() {
        configProperties.mongodVerbosity = asStringProperty('-v')
        this
    }

    Integer getConfiguredPort() {
        configProperties.port as Integer
    }

    BuildScriptBuilder withDownloadURL(String downloadURL) {
        configProperties.downloadURL = asStringProperty(downloadURL)
        this
    }

    BuildScriptBuilder withProxy(String host, int port) {
        configProperties.proxyHost = asStringProperty(host)
        configProperties.proxyPort = port
        this
    }

    BuildScriptBuilder withArtifactStorePath(String artifactStorePath) {
        configProperties.artifactStorePath = asStringProperty(artifactStorePath.replace('\\', '\\\\'))
        this
    }

    BuildScriptBuilder withAuth(boolean auth) {
        configProperties.auth = auth
        this
    }

    private String asStringProperty(String value) {
        "'$value'"
    }

    static class MongoPluginConfigBlock {
        String config = ''

        void addPropertyConfig(String propertyName, String value) {
            if (!config) {
                config += 'mongo {\n'
            }

            config += "\t$propertyName $value\n"
        }

        String toString() {
            if (config) {
                return config + '}'
            } else {
                return config
            }
        }
    }
}
