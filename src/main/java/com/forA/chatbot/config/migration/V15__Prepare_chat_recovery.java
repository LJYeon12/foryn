package com.forA.chatbot.config.migration;

import com.mongodb.WriteConcern;
import com.mongodb.client.model.Indexes;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/** Mongo DDL은 Flyway의 MySQL 이력 기록과 함께 롤백할 수 없으므로 멱등하게 처리한다. */
@Component
public class V15__Prepare_chat_recovery extends BaseJavaMigration {
    private final MongoTemplate mongo;

    public V15__Prepare_chat_recovery(MongoTemplate mongo) { this.mongo = mongo; }

    @Override public Integer getChecksum() { return 1; }

    @Override public void migrate(Context context) {
        var database = mongo.getDb().withWriteConcern(WriteConcern.MAJORITY);
        for (String collection : new String[] {"chat_write_guards", "chat_sessions", "chat_messages"}) {
            if (!mongo.collectionExists(collection)) database.createCollection(collection);
        }
        database.getCollection("chat_messages").createIndex(Indexes.ascending("operation_id", "write_status"));
        database.getCollection("chat_messages").createIndex(Indexes.ascending("session_id", "sent_at"));
    }
}
