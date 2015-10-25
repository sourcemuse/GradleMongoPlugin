package com.sourcemuse.gradle.plugin

import groovy.transform.Immutable

@Immutable
class ProxyDetails {
    final String host
    final int port
    final String user
    final String password

    boolean provided() {
        host
    }

    SocketAddress getSocketAddress() {
        host ? new InetSocketAddress(host, port) : null
    }

    boolean authDetailsProvided() {
        user && password
    }
}
