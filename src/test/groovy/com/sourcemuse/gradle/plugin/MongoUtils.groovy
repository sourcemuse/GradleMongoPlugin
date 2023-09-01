package com.sourcemuse.gradle.plugin

import com.mongodb.*
import com.mongodb.client.MongoDatabase
import de.flapdoodle.embed.mongo.runtime.Mongod
import org.bson.Document

import static com.sourcemuse.gradle.plugin.BuildScriptBuilder.DEFAULT_MONGOD_PORT

class MongoUtils {

    private static final String LOOPBACK_ADDRESS = '127.0.0.1'
    private static final String DATABASE_NAME = 'test'

    static void ensureMongoIsStopped(int port = DEFAULT_MONGOD_PORT) {
        if (mongoInstanceRunning(port)) {
            // try shutdown command for older versions first
            Mongod.sendShutdownLegacy(InetAddress.getLoopbackAddress(), port)
              || Mongod.sendShutdown(InetAddress.getLoopbackAddress(), port)
        }
    }

    static MongoDatabase mongoDatabase(int port) {
        def mongoClient = new MongoClient(LOOPBACK_ADDRESS, port)
        mongoClient.getDatabase(DATABASE_NAME)
    }

    static Document mongoServerStatus(int port = DEFAULT_MONGOD_PORT) {
        mongoDatabase(port).runCommand(new Document('serverStatus', 1))
    }

    static boolean mongoInstanceRunning(int port = DEFAULT_MONGOD_PORT) {
        if (isPortAvailable(LOOPBACK_ADDRESS, port)) {
            return false
        }
        try {
            getMongoVersionRunning(port)
        } catch (Throwable ignored) {
            return false
        }
        return true
    }

    private static boolean isPortAvailable(String host, int port) {
        Socket socket = null
        try {
            socket = new Socket(host, port)
            return false
        } catch (IOException ignored) {
            return true
        } finally {
            try {
                socket.close()
            } catch (Throwable ignored) {
            }
        }
    }

    static String getMongoVersionRunning(int port) {
        try {
            def mongoClient = new MongoClient(LOOPBACK_ADDRESS, port)
            def result = mongoClient.getDatabase(DATABASE_NAME).runCommand(new Document('buildInfo', 1))
            return result.version
        } catch (Exception e) {
            return 'none'
        }
    }

    static boolean makeJournaledWrite() {
        try {
            def options = MongoClientOptions.builder().writeConcern(WriteConcern.JOURNALED).build()
            def mongoClient = new MongoClient("${LOOPBACK_ADDRESS}:${DEFAULT_MONGOD_PORT}", options)
            writeSampleObjectToDb(mongoClient)
            return true
        } catch (Exception e) {
            return false
        }
    }

    private static void writeSampleObjectToDb(MongoClient mongoClient) {
        def db = mongoClient.getDatabase(DATABASE_NAME)
        db.createCollection('test-collection')
        def document = new Document('key', 'val')
        db.getCollection('test-collection').insertOne(document)
    }

    static boolean runMongoCommand(MongoCredential credential, Document cmd) {
        ServerAddress addr = new ServerAddress("${LOOPBACK_ADDRESS}:${DEFAULT_MONGOD_PORT}")

        def mongoClient = credential ?
            new MongoClient(addr, credential, MongoClientOptions.builder().build()) :
            new MongoClient(addr, MongoClientOptions.builder().build())

        try {
            mongoClient.getDatabase('admin').runCommand(cmd)
        }
        catch (MongoException e) {
            return false
        }
        finally {
            mongoClient.close()
        }
        return true
    }
}