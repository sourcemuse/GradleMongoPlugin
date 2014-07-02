package com.sourcemuse.gradle.plugin

import static com.sourcemuse.gradle.plugin.BuildScriptBuilder.DEFAULT_MONGOD_PORT

import com.mongodb.MongoClient
import de.flapdoodle.embed.mongo.runtime.Mongod


class MongoUtils {

    static void ensureMongoIsStopped(int port = DEFAULT_MONGOD_PORT) {
        Mongod.sendShutdown(InetAddress.getLoopbackAddress(), port)
    }

    static boolean mongoInstanceRunning(int port) {
        try {
            MongoClient mongoClient = new MongoClient('127.0.0.1', port)
            mongoClient.getDB('test').getStats()
        } catch (Exception e) {
            return false
        }
        true
    }
    
    static String mongoVersionRunning(int port) {
        try {
            MongoClient mongoClient = new MongoClient('127.0.0.1', port)
            def result = mongoClient.getDB('test').command("buildInfo");
            return result.getString('version')
        } catch (Exception e) {
            return "none"
        }
    }
}
