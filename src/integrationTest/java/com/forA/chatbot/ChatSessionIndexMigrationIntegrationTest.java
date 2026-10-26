package com.forA.chatbot;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.model.Sorts;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.JavaMigration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.data.mongodb.database=report_session_index_test")
@ActiveProfiles("test")
class ChatSessionIndexMigrationIntegrationTest {
    private static final String INDEX_NAME = "idx_chat_sessions_user_started_at";
    private static final Document INDEX_KEYS = new Document("user_id", 1).append("started_at", 1);

    @Autowired private MongoTemplate mongo;
    @Autowired private List<JavaMigration> migrations;

    @AfterEach
    void cleanDatabase() {
        mongo.getDb().drop();
    }

    @Test
    void flywayAddsSelectiveIndexWithoutChangingDocumentsAndCanRetry() {
        var collection = mongo.getCollection("chat_sessions");
        var inside = Date.from(Instant.parse("2026-10-12T12:00:00Z"));
        var outside = Date.from(Instant.parse("2026-01-01T00:00:00Z"));
        var documents = new ArrayList<Document>();
        // 사용자와 날짜가 같아도 유효하다. 리포트 인덱스는 고유성을 강제하면 안 된다.
        documents.add(session("target-a", 7L, inside));
        documents.add(session("target-b", 7L, inside));
        for (int i = 0; i < 100; i++) {
            documents.add(session("old-" + i, 7L, outside));
        }
        for (int i = 0; i < 1_000; i++) {
            documents.add(session("other-" + i, 8L, inside));
        }
        collection.insertMany(documents);
        var filter = new Document("user_id", 7L).append("started_at", new Document(
                "$gt", Date.from(Instant.parse("2026-10-01T00:00:00Z")))
                .append("$lt", Date.from(Instant.parse("2026-11-01T00:00:00Z"))));
        var before = collection.find(filter).sort(Sorts.ascending("_id")).into(new ArrayList<>());
        assertThat(before).hasSize(2);

        // Flyway 자동 설정처럼 Spring이 탐색한 Java 마이그레이션을 사용한다.
        var flyway = freshHistory();
        flyway.migrate();
        assertReportIndex();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("16");

        var explain = mongo.getDb().runCommand(new Document("explain", new Document(
                "find", "chat_sessions").append("filter", filter)).append("verbosity", "executionStats"));
        var stats = explain.get("executionStats", Document.class);
        assertThat(((Number) stats.get("nReturned")).longValue()).isEqualTo(2);
        assertThat(((Number) stats.get("totalDocsExamined")).longValue()).isEqualTo(2);
        assertThat(((Number) stats.get("totalKeysExamined")).longValue()).isEqualTo(2);

        // SQL 이력 기록 전에 Mongo DDL이 성공할 수 있다. 새 이력에서도 안전하게 실행되어야 한다.
        freshHistory().migrate();
        assertReportIndex();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(collection.find(filter).sort(Sorts.ascending("_id")).into(new ArrayList<>()))
                .containsExactlyElementsOf(before);
        assertThat(collection.find().sort(Sorts.ascending("_id")).into(new ArrayList<>()))
                .containsExactlyElementsOf(documents.stream()
                        .sorted((left, right) -> left.getString("_id").compareTo(right.getString("_id"))).toList());
    }

    private Flyway freshHistory() {
        return Flyway.configure()
                .dataSource("jdbc:h2:mem:session_index_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "")
                .locations(new String[0])
                .javaMigrations(migrations.toArray(JavaMigration[]::new))
                .load();
    }

    private void assertReportIndex() {
        assertThat(mongo.getCollection("chat_sessions").listIndexes().into(new ArrayList<>()))
                .filteredOn(index -> INDEX_KEYS.equals(index.get("key")))
                .singleElement()
                .satisfies(index -> {
                    assertThat(index.getString("name")).isEqualTo(INDEX_NAME);
                    assertThat(index.getBoolean("unique", false)).isFalse();
                });
    }

    private Document session(String id, long userId, Date startedAt) {
        return new Document("_id", id).append("user_id", userId).append("started_at", startedAt)
                .append("temporary_data", new Document("keep", "original"));
    }
}
