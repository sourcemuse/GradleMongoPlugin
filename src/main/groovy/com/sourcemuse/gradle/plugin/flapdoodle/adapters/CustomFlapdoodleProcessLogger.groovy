package com.sourcemuse.gradle.plugin.flapdoodle.adapters

import de.flapdoodle.embed.process.distribution.Version
import de.flapdoodle.embed.process.io.progress.ProgressListener


class CustomFlapdoodleProcessLogger implements ProgressListener {
    private final Version version

    CustomFlapdoodleProcessLogger(Version version) {
        this.version = version
    }

    @Override
    void progress(String label, int percent) {
    }

    @Override
    void done(String label) {
    }

    @Override
    void start(String label) {
        if (label.contains('Download')) {
            println "Downloading Mongo ${version.asInDownloadPath()} distribution..."
        } else if (label.contains('Extract')) {
            println 'Extracting Mongo binaries...'
        }
    }

    @Override
    void info(String label, String message) {
    }
}
