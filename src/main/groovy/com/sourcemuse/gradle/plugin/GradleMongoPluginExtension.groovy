package com.sourcemuse.gradle.plugin

import static LogDestination.FILE
import static java.lang.Integer.parseInt

class GradleMongoPluginExtension {

    static final EPHEMERAL_TEMPORARY_FOLDER = null

    private int port = 27017
    String bindIp = '127.0.0.1'
    boolean journalingEnabled = false
    String logging = FILE as String
    String logFilePath = 'embedded-mongo.log'
    String mongoVersion = 'PRODUCTION'
    String storageLocation = EPHEMERAL_TEMPORARY_FOLDER

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
}
