package com.sourcemuse.gradle.plugin

import spock.lang.Specification
import spock.lang.Unroll


class GradleMongoPluginExtensionSpec extends Specification {
    def pluginExtension = new GradleMongoPluginExtension()

    def 'port can be supplied as a number'() {
        given:
        pluginExtension.port = 12345

        when:
        def port = pluginExtension.port

        then:
        port == 12345
    }

    def 'port can be supplied as a String'() {
        given:
        pluginExtension.port = '12345'

        when:
        def port = pluginExtension.port

        then:
        port == 12345
    }

    @Unroll
    def 'port is randomized when supplied as #variant "#randomLabel"'() {
        given:
        pluginExtension.port = randomLabel

        when:
        def port = pluginExtension.port

        then:
        port >= 0 && port <= 65535

        where:
        variant      | randomLabel
        'lowercase'  | 'random'
        'uppercase'  | 'RANDOM'
        'mixed-case' | 'rAnDoM'
    }

    def 'repeated port checks are idempotent when a random port is picked'() {
        given:
        this.pluginExtension.port = 'random'

        when:
        def port = this.pluginExtension.port

        then:
        port == this.pluginExtension.port
    }
}
