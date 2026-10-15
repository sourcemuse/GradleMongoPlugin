package com.sourcemuse.gradle.plugin

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.MongoCredential
import com.mongodb.MongoException
import com.mongodb.WriteConcern
import com.mongodb.client.ClientSession
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
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

    static Document mongoServerStatus(int port = DEFAULT_MONGOD_PORT) {
        MongoClients.create("mongodb://${LOOPBACK_ADDRESS}:${port}").withCloseable {
            return it.getDatabase(DATABASE_NAME).runCommand(new Document('serverStatus', 1))
        }
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
        MongoClients.create("mongodb://${LOOPBACK_ADDRESS}:${port}").withCloseable {
            return it.getDatabase(DATABASE_NAME).runCommand(new Document('buildInfo', 1)).version
        }
    }

    static boolean makeJournaledWrite() {
        def settings = MongoClientSettings.builder()
          .writeConcern(WriteConcern.JOURNALED)
          .applyConnectionString(new ConnectionString("mongodb://${LOOPBACK_ADDRESS}:${DEFAULT_MONGOD_PORT}"))
          .build()
        MongoClients.create(settings).withCloseable {
            writeSampleObjectToDb(it)
            return true
        }
    }

    private static void writeSampleObjectToDb(MongoClient mongoClient) {
        def db = mongoClient.getDatabase(DATABASE_NAME)
        db.createCollection('test-collection')
        def document = new Document('key', 'val')
        db.getCollection('test-collection').insertOne(document)
    }

    static boolean runMongoCommand(MongoCredential credential, Document cmd) {

        def settings = MongoClientSettings.builder()
          .applyConnectionString(new ConnectionString("mongodb://${LOOPBACK_ADDRESS}:${DEFAULT_MONGOD_PORT}"))
        if (credential) {
            settings.credential(credential)
        }

        def mongoClient = MongoClients.create(settings.build())

        try {
            mongoClient.getDatabase('admin').runCommand(cmd)
        }
        catch (MongoException ignored) {
            return false
        }
        finally {
            mongoClient.close()
        }
        return true
    }

    static boolean makeWriteWithTransaction(int port) {
        MongoClients.create("mongodb://${LOOPBACK_ADDRESS}:${port}").withCloseable {
            try (ClientSession session = it.startSession()) {
                session.startTransaction()

                it.getDatabase(DATABASE_NAME)
                  .getCollection("test")
                  .insertOne(session, new Document("foo", "bar"))

                session.commitTransaction()
            }
        }
        return true
    }
}