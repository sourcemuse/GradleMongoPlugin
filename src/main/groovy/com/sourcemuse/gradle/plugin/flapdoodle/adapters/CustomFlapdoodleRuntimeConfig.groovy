package com.sourcemuse.gradle.plugin.flapdoodle.adapters
import de.flapdoodle.embed.mongo.packageresolver.Command
import de.flapdoodle.embed.mongo.config.ArtifactStores
import de.flapdoodle.embed.mongo.config.Defaults.DownloadConfigDefaults
import de.flapdoodle.embed.mongo.config.Defaults.RuntimeConfigDefaults
import de.flapdoodle.embed.process.store.ArtifactStore
import de.flapdoodle.embed.process.config.ImmutableRuntimeConfig
import de.flapdoodle.embed.process.config.store.HttpProxyFactory
import de.flapdoodle.embed.process.config.store.ImmutableDownloadConfig
import de.flapdoodle.embed.process.distribution.Distribution
import de.flapdoodle.embed.process.distribution.Version
import de.flapdoodle.embed.process.io.directories.FixedPath
import de.flapdoodle.embed.process.runtime.CommandLinePostProcessor

class CustomFlapdoodleRuntimeConfig extends RuntimeConfigDefaults {
    private final Version version
    private final String mongodVerbosity
    private final String downloadUrl
    private final String proxyHost
    private final int proxyPort
    private final String artifactStorePath

    CustomFlapdoodleRuntimeConfig(Version version,
                                  String mongodVerbosity,
                                  String downloadUrl,
                                  String proxyHost,
                                  int proxyPort,
                                  String artifactStorePath) {
        this.version = version
        this.mongodVerbosity = mongodVerbosity
        this.downloadUrl = downloadUrl
        this.proxyHost = proxyHost
        this.proxyPort = proxyPort
        this.artifactStorePath = artifactStorePath
    }

    ImmutableRuntimeConfig.Builder defaults(Command command) {
        ImmutableRuntimeConfig.Builder runtimeConfigBuilder = super.defaults(command)

        ImmutableDownloadConfig.Builder downloadConfigBuilder = new DownloadConfigDefaults().defaultsForCommand(command)
        downloadConfigBuilder.progressListener(new CustomFlapdoodleProcessLogger(version))

        if (downloadUrl) {
            downloadConfigBuilder.downloadPath(downloadUrl)
        }

        if (proxyHost) {
          downloadConfigBuilder.proxyFactory(new HttpProxyFactory(proxyHost, proxyPort))
        }

        if (artifactStorePath) {
          downloadConfigBuilder.artifactStorePath(new FixedPath(artifactStorePath))
        }

        runtimeConfigBuilder.commandLinePostProcessor(new CommandLinePostProcessor() {
            @Override
            List<String> process(Distribution distribution, List<String> args) {
                if (mongodVerbosity) args.add(mongodVerbosity)
                return args
            }
        })

        runtimeConfigBuilder.artifactStore(ArtifactStore.builder().downloadConfig(downloadConfigBuilder.build()).build())

        runtimeConfigBuilder
    }
}
