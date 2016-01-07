package com.sourcemuse.gradle.plugin.flapdoodle.adapters
import de.flapdoodle.embed.mongo.Command
import de.flapdoodle.embed.mongo.config.ArtifactStoreBuilder
import de.flapdoodle.embed.mongo.config.DownloadConfigBuilder
import de.flapdoodle.embed.mongo.config.RuntimeConfigBuilder
import de.flapdoodle.embed.process.distribution.Distribution
import de.flapdoodle.embed.process.distribution.IVersion
import de.flapdoodle.embed.process.runtime.ICommandLinePostProcessor

class CustomFlapdoodleRuntimeConfig extends RuntimeConfigBuilder {
    private final IVersion version
    private final String mongodVerbosity
    private final String downloadUrl

    CustomFlapdoodleRuntimeConfig(IVersion version, String mongodVerbosity, String downloadUrl) {
        this.version = version
        this.mongodVerbosity = mongodVerbosity
        this.downloadUrl = downloadUrl
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
