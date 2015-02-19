package com.sourcemuse.gradle.plugin.flapdoodle.adapters

import de.flapdoodle.embed.mongo.Command
import de.flapdoodle.embed.mongo.config.ArtifactStoreBuilder
import de.flapdoodle.embed.mongo.config.DownloadConfigBuilder
import de.flapdoodle.embed.mongo.config.RuntimeConfigBuilder
import de.flapdoodle.embed.process.config.store.IDownloadConfig
import de.flapdoodle.embed.process.distribution.IVersion


class CustomFlapdoodleRuntimeConfig extends RuntimeConfigBuilder {
    private final IVersion version

    CustomFlapdoodleRuntimeConfig(IVersion version) {
        this.version = version
    }

    @Override
    RuntimeConfigBuilder defaults(Command command) {
        super.defaults(command)
        IDownloadConfig downloadConfig = new DownloadConfigBuilder()
                .defaultsForCommand(command)
                .progressListener(new CustomFlapdoodleProcessLogger(version))
                .build()

        artifactStore().overwriteDefault(new ArtifactStoreBuilder().defaults(command).download(downloadConfig).build())
        this
    }
}
