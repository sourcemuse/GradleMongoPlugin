package com.sourcemuse.gradle.plugin.flapdoodle.adapters
import de.flapdoodle.embed.mongo.Command
import de.flapdoodle.embed.mongo.config.ArtifactStoreBuilder
import de.flapdoodle.embed.mongo.config.DownloadConfigBuilder
import de.flapdoodle.embed.mongo.config.RuntimeConfigBuilder
import de.flapdoodle.embed.process.config.store.HttpProxyFactory
import de.flapdoodle.embed.process.distribution.Distribution
import de.flapdoodle.embed.process.distribution.IVersion
import de.flapdoodle.embed.process.io.directories.FixedPath
import de.flapdoodle.embed.process.runtime.ICommandLinePostProcessor

class CustomFlapdoodleRuntimeConfig extends RuntimeConfigBuilder {
    private final IVersion version
    private final String mongodVerbosity
    private final String downloadUrl
    private final String proxyHost
    private final int proxyPort
    private final String artifactStorePath

    CustomFlapdoodleRuntimeConfig(IVersion version,
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

    @Override
    RuntimeConfigBuilder defaults(Command command) {
        super.defaults(command)

        DownloadConfigBuilder downloadConfigBuilder = new DownloadConfigBuilder()
        downloadConfigBuilder.defaultsForCommand(command)
                             .progressListener(new CustomFlapdoodleProcessLogger(version))

        if (downloadUrl) {
            downloadConfigBuilder.downloadPath(downloadUrl)
        }

        if (proxyHost) {
          downloadConfigBuilder.proxyFactory(new HttpProxyFactory(proxyHost, proxyPort))
        }

        if (artifactStorePath) {
          downloadConfigBuilder.artifactStorePath(new FixedPath(artifactStorePath))
        }

        commandLinePostProcessor(new ICommandLinePostProcessor() {
            @Override
            List<String> process(Distribution distribution, List<String> args) {
                if (mongodVerbosity) args.add(mongodVerbosity)
                return args
            }
        })

        artifactStore().overwriteDefault(new ArtifactStoreBuilder().defaults(command).download(downloadConfigBuilder).build())

        this
    }
}
