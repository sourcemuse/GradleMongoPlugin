package com.sourcemuse.gradle.plugin

import java.util.regex.Pattern

import static LogDestination.FILE
import static java.lang.Integer.parseInt

class GradleMongoPluginExtension {

    static final EPHEMERAL_TEMPORARY_FOLDER = null
    static final Pattern VALID_MONGOD_VERBOSITY_FORMAT = ~/(?i)-?v+|-{0,2}verbose/

    private int port = 27017
    String bindIp = '127.0.0.1'
    boolean journalingEnabled = false
    String logging = FILE as String
    String logFilePath = 'embedded-mongo.log'
    String mongoVersion = 'PRODUCTION'
    String storageLocation = EPHEMERAL_TEMPORARY_FOLDER
    String mongodVerbosity = ''
    String downloadURL = ''
    ProxyDetails proxyDetails = initProxyDetails()

    void setDownloadURL(String url) {
        try {
            this.downloadURL = new URL(url).toString()
        } catch (ignored) {
            throw new IllegalArgumentException("DownloadURL ${url} is not a valid URL.")
        }
    }

    int getPort() {
        port
    }

    void setPort(Object port) {
        if (port instanceof String) {
            this.port = parsePortAsString(port)
        } else {
            this.port = port as Integer
        }
    }

    void setMongodVerbosity(String mongodVerbosity) {
        this.mongodVerbosity = parseMongodVerbosity(mongodVerbosity)
    }

    private Serializable parseMongodVerbosity(String mongodVerbosity) {
        if (!(mongodVerbosity ==~ VALID_MONGOD_VERBOSITY_FORMAT))
            throw new IllegalArgumentException("MongodVerbosity should be defined as either '-verbose' or '-v(vvvv)'. " +
                "Do not configure this property if you don't wish to have verbose output.")

        def lowerCaseValue = mongodVerbosity.toLowerCase()

        if (lowerCaseValue.endsWith('verbose')) return '-v'
        if (lowerCaseValue.startsWith('v')) return "-$lowerCaseValue"
        return lowerCaseValue
    }

    private static int parsePortAsString(String port) {
        if (port.toLowerCase() == 'random') {
            return randomAvailablePort()
        }

        return parseInt(port)
    }

    private static int randomAvailablePort() {
        try {
            ServerSocket server = new ServerSocket()
            server.setReuseAddress(true)
            server.bind(new InetSocketAddress(0))
            int port = server.getLocalPort()
            server.close()
            return port
        } catch (IOException e) {
            throw new IOException('Failed to find random free port', e)
        }
    }

    private static ProxyDetails initProxyDetails() {
        def portString = System.getProperty('http.proxyPort')
        int port = portString ? parseInt(portString) : 80
        def user = System.getProperty('http.proxyUser') ?: ''
        def password = System.getProperty('http.proxyPassword') ?: ''

        new ProxyDetails(System.getProperty('http.proxyHost'), port, user, password)
    }
}
