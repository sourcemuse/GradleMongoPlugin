package com.sourcemuse.gradle.plugin.flapdoodle.adapters

import static de.flapdoodle.embed.mongo.distribution.Version.Main.DEVELOPMENT
import static de.flapdoodle.embed.mongo.distribution.Version.Main.PRODUCTION

import com.sourcemuse.gradle.plugin.GradleMongoPluginExtension
import de.flapdoodle.embed.mongo.distribution.IFeatureAwareVersion
import de.flapdoodle.embed.process.distribution.GenericVersion
import de.flapdoodle.embed.mongo.distribution.Feature
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.distribution.Versions


class VersionFactory {

    static final String LATEST_VERSION = '-LATEST'

    IFeatureAwareVersion getVersion(GradleMongoPluginExtension pluginExtension) {
        def suppliedVersion = pluginExtension.mongoVersion

        if (versionIsDevOrProd(suppliedVersion)) {
            return suppliedVersion as Version.Main
        }

        return parseVersionNumber(suppliedVersion)
    }

    private static boolean versionIsDevOrProd(String suppliedVersion) {
        try {
            (suppliedVersion as Version.Main) in [DEVELOPMENT, PRODUCTION]
        } catch (ignored) {}
    }

    private static IFeatureAwareVersion parseVersionNumber(String suppliedVersion) {
        String mongoVersion = convertToFlapdoodleVersion(suppliedVersion)

        if (mongoVersion.endsWith(LATEST_VERSION)) {
            mongoVersion = mongoVersion.substring(0, mongoVersion.length() - LATEST_VERSION.length())

            if (versionMatchesMainBranchVersion(mongoVersion)) {
                return mongoVersion as Version.Main
            }
        } else if (versionMatchesSpecificVersion(mongoVersion)) {
            return mongoVersion as Version
        } else {
            // we'll just assume that the version is newer and supports all features
            return Versions.withFeatures(new GenericVersion(suppliedVersion), Feature.values())
        }
    }

    private static boolean versionMatchesMainBranchVersion(String mongoVersion) {
        try {
            mongoVersion in Version.Main.values().collect { it.toString() }
        } catch (ignored) {}
    }

    private static String convertToFlapdoodleVersion(String suppliedVersion) {
        def mongoVersion = 'V' + suppliedVersion
        mongoVersion = mongoVersion.replace('.', '_')
        mongoVersion
    }

    private static Version versionMatchesSpecificVersion(String mongoVersion) {
        try {
            mongoVersion as Version
        } catch (ignored) {}
    }
}
