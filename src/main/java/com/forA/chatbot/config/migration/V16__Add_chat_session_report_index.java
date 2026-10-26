package com.forA.chatbot.config.migration;

import com.mongodb.WriteConcern;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/** Mongo DDL은 Flyway의 MySQL 이력 기록과 함께 롤백할 수 없으므로 멱등하게 처리한다. */
@Component
public class V16__Add_chat_session_report_index extends BaseJavaMigration {
    private final MongoTemplate mongo;

    public V16__Add_chat_session_report_index(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public Integer getChecksum() {
        return 1;
    }

    @Override
    public void migrate(Context context) {
        mongo.getDb().withWriteConcern(WriteConcern.MAJORITY)
                .getCollection("chat_sessions")
                .createIndex(Indexes.ascending("user_id", "started_at"),
                        new IndexOptions().name("idx_chat_sessions_user_started_at"));
    }
}
