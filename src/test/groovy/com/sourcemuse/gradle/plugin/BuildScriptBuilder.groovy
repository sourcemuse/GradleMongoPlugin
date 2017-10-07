package com.sourcemuse.gradle.plugin

import com.sourcemuse.gradle.plugin.flapdoodle.gradle.GradleMongoPlugin


class BuildScriptBuilder {

    static final DEFAULT_MONGOD_PORT = 27017

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
            |apply plugin: ${GradleMongoPlugin.name}
            |apply plugin: ${PluginForTests.name}
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
        configProperties.storageLocation = asStringProperty(storage)
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
        configProperties.artifactStorePath = asStringProperty(artifactStorePath)
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
