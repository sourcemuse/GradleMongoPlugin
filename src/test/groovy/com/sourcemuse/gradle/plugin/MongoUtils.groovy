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
        Mongod.sendShutdown(InetAddress.getLoopbackAddress(), port)
    }

    static MongoDatabase mongoDatabase(int port) {
        def mongoClient = new MongoClient(LOOPBACK_ADDRESS, port)
        mongoClient.getDatabase(DATABASE_NAME)
    }

    static Document mongoServerStatus(int port = DEFAULT_MONGOD_PORT) {
        mongoDatabase(port).runCommand(new Document('serverStatus', 1))
    }

    static boolean mongoInstanceRunning(int port = DEFAULT_MONGOD_PORT) {
        try {
            mongoDatabase(port).runCommand(new Document('dbStats', 1))
        } catch (Exception e) {
            e.printStackTrace()
            return false
        }
        return true
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

    static void shutdownAuth(int port = DEFAULT_MONGOD_PORT) {
        def mongoClient = new MongoClient(LOOPBACK_ADDRESS, port)
        def cmdArgs = new Document('createUser', 'admin')
        cmdArgs.put('pwd', 'qwert123')
        cmdArgs.put('roles', ['root'])
        mongoClient.getDatabase('admin').runCommand(cmdArgs)

        def adminClient = new MongoClient(new ServerAddress("${LOOPBACK_ADDRESS}:${port}"),
            MongoCredential.createCredential('admin', 'admin', 'qwert123'.toCharArray()),
            MongoClientOptions.builder().build())

        try {
            adminClient.getDatabase('admin').runCommand(new Document('shutdown', 1))
        }
        catch (MongoSocketReadException e) { /* Expected at shutdown */
        }
    }
}