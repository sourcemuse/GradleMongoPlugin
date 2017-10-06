package com.sourcemuse.gradle.plugin

import static com.sourcemuse.gradle.plugin.BuildScriptBuilder.DEFAULT_MONGOD_PORT
import static com.sourcemuse.gradle.plugin.flapdoodle.gradle.GradleMongoPlugin.PLUGIN_EXTENSION_NAME

import com.mongodb.BasicDBObject
import com.mongodb.DB
import com.mongodb.DBObject
import com.mongodb.MongoClient
import com.mongodb.WriteConcern
import de.flapdoodle.embed.mongo.runtime.Mongod
import org.gradle.api.Project

class MongoUtils {

    private static final String LOOPBACK_ADDRESS = '127.0.0.1'
    private static final String DATABASE_NAME = 'test'

    static void ensureMongoIsStopped(int port = DEFAULT_MONGOD_PORT) {
        Mongod.sendShutdown(InetAddress.getLoopbackAddress(), port)
    }

    static DB mongoDatabase(int port) {
        def mongoClient = new MongoClient(LOOPBACK_ADDRESS, port)
        mongoClient.getDB(DATABASE_NAME)
    }

    static DBObject mongoServerStatus(int port = DEFAULT_MONGOD_PORT) {
        mongoDatabase(port).eval("db.serverStatus()")
    }

    static boolean mongoInstanceRunning(int port = DEFAULT_MONGOD_PORT) {
        try {
            mongoDatabase(port).getStats()
        } catch (Exception e) {
            e.printStackTrace()
            return false
        }
        return true
    }

    static boolean mongoInstanceRunningOnConfiguredPort(Project project) {
        int mongoPort = project[PLUGIN_EXTENSION_NAME].port
        return mongoInstanceRunning(mongoPort)
    }

    static String getMongoVersionRunning(int port) {
        try {
            def mongoClient = new MongoClient(LOOPBACK_ADDRESS, port)
            def result = mongoClient.getDB(DATABASE_NAME).command('buildInfo')
            return result.getString('version')
        } catch (Exception e) {
            return 'none'
        }
    }

    static boolean makeJournaledWrite() {
        try {
            def mongoClient = new MongoClient(LOOPBACK_ADDRESS, DEFAULT_MONGOD_PORT)
            mongoClient.writeConcern = WriteConcern.JOURNALED
            writeSampleObjectToDb(mongoClient)
            return true
        } catch (Exception e) {
            return false
        }
    }

    private static void writeSampleObjectToDb(MongoClient mongoClient) {
        def db = mongoClient.getDB(DATABASE_NAME)
        def collection = db.createCollection('test-collection', new BasicDBObject())
        def basicDbObject = new BasicDBObject()
        basicDbObject.put('key', 'val')
        collection.insert(basicDbObject)
    }
}
