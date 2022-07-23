 package com.sourcemuse.gradle.plugin

class BuildScriptBuilder {

    static final DEFAULT_MONGOD_PORT = 27017
    static final STOP_MONGO_DB_FOR_TEST = 'stopMongoDbForTest'
    static final START_MONGO_DB_FOR_TEST = 'startMongoDbForTest'
    static final START_MANAGED_MONGO_DB_FOR_TEST = 'startManagedMongoDbForTest'
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
            |task ${START_MANAGED_MONGO_DB_FOR_TEST} (dependsOn: startManagedMongoDb) { doLast performMongoCheck }
            |task ${START_MONGO_DB_FOR_TEST} (dependsOn: startMongoDb) { doLast performMongoCheck }
            |task ${STOP_MONGO_DB_FOR_TEST} (dependsOn: [startMongoDb, stopMongoDb])
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

    BuildScriptBuilder withAuth() {
        configProperties.auth = true
        this
    }

    BuildScriptBuilder withParams(Map<String, String> params) {
        configProperties.params = params.inspect()
        this
    }

    BuildScriptBuilder withArgs(Map<String, String> args) {
        configProperties.args = args.inspect()
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

            config += "\t$propertyName = $value\n"
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
