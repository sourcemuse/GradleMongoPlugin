package com.sourcemuse.gradle.plugin.flapdoodle.adapters

import com.sourcemuse.gradle.plugin.GradleMongoPluginExtension
import de.flapdoodle.embed.mongo.distribution.IFeatureAwareVersion
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.distribution.Versions
import de.flapdoodle.embed.process.distribution.ImmutableGenericVersion

import static de.flapdoodle.embed.mongo.distribution.Version.Main.DEVELOPMENT
import static de.flapdoodle.embed.mongo.distribution.Version.Main.PRODUCTION

class VersionFactory {

  static final String LATEST_VERSION = '-LATEST'

    static IFeatureAwareVersion getVersion(GradleMongoPluginExtension pluginExtension) {
        def suppliedVersion = pluginExtension.mongoVersion

        if (suppliedVersion == "DEVELOPMENT") {
            return DEVELOPMENT
        } else if (suppliedVersion == "PRODUCTION") {
            return PRODUCTION
        } else if (suppliedVersion == "latest") {
          return Version.LATEST_NIGHTLY
        }

        if (suppliedVersion.endsWith(LATEST_VERSION)) {
          def mainVersion = "V" + suppliedVersion.substring(0, suppliedVersion.length() - LATEST_VERSION.length()).replace(".", "_")
          for (def v in Version.Main.values()) {
            if (v.name() == mainVersion) {
              return v
            }
          }
        }

        // for some reason with gradle 8.8 and jdk 17 groovy is unable to use the static interface method Version.of
        return new Versions.GenericFeatureAwareVersion(new ImmutableGenericVersion(suppliedVersion))
    }
}
