package com.sourcemuse.gradle.plugin.flapdoodle.adapters

import com.sourcemuse.gradle.plugin.GradleMongoPluginExtension
import de.flapdoodle.embed.mongo.config.Storage

class StorageFactory {
    Storage getStorage(GradleMongoPluginExtension extension) {
        if(extension.storageLocation){
            new Storage(extension.storageLocation, null, 0)
        } else {
            new Storage()
        }
    }
}
