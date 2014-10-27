package com.sourcemuse.gradle.plugin


class BuildScriptBuilder {

    static final DEFAULT_MONGOD_PORT = 27017

    int port
    String logging
    String logFilePath
    String mongoVersion
    String storageLocation
    boolean randomPort

    static BuildScriptBuilder buildScript() {
        new BuildScriptBuilder()
    }

    String build() {
        def mongoConfigBlock = new MongoPluginConfigBlock()

        this.properties.each { name, value ->
            if (value && value in [port, logging, logFilePath, mongoVersion, storageLocation, randomPort])
                mongoConfigBlock.addPropertyConfig(name, "$value")
        }

        """
            |apply plugin: ${GradleMongoPlugin.name}
            |apply plugin: ${TestPlugin.name}
            |
            |${mongoConfigBlock}
            """.stripMargin()
    }

    BuildScriptBuilder withPort(int port) {
        this.port = port
        this
    }

    BuildScriptBuilder withLogging(String logging) {
        this.logging = logging
        this
    }

    BuildScriptBuilder withFilePath(String filePath) {
        this.logFilePath = filePath.replace('\\', '\\\\')
        this
    }
    
    BuildScriptBuilder withMongoVersion(String version) {
        this.mongoVersion = version
        this
    }

    BuildScriptBuilder withStorageLocation(String storage) {
        this.storageLocation = storage
        this
    }

    BuildScriptBuilder withRandomPort(boolean random) {
        this.randomPort = random
        this
    }

    static class MongoPluginConfigBlock {
        String config = ''

        void addPropertyConfig(String propertyName, String value) {
            if (!config) {
                config += 'mongo {\n'
            }

            config += "\t$propertyName=$value\n"
        }

        String toString() {
            if (config) {
                config += '}'
            }
        }
    }
}
