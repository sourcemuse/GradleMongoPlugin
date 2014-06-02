package com.sourcemuse.gradle.plugin

import de.flapdoodle.embed.process.io.IStreamProcessor

class FileOutputStreamProcessor implements IStreamProcessor {
    File logFile

    FileOutputStreamProcessor(String filePath) {
        logFile = new File(filePath)
        logFile.mkdirs()
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
