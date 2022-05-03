package com.sourcemuse.gradle.plugin.flapdoodle.adapters

import de.flapdoodle.embed.process.io.StreamProcessor

class FileOutputStreamProcessor implements StreamProcessor {
    File logFile

    FileOutputStreamProcessor(String filePath) {
        logFile = new File(filePath)
        if (logFile.parentFile) {
            logFile.parentFile.mkdirs()
        }
        logFile.createNewFile()
    }

    @Override
    void process(String block) {
        logFile.text += block
    }

    @Override
    void onProcessed() {

    }
}
