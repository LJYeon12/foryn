package com.forA.chatbot.chat.repository;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.ChatHandler;
import com.forA.chatbot.chat.domain.ChatOperation;
import com.forA.chatbot.chat.domain.ChatOperationToken;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 작업 일지에는 메타데이터만 둔다. 잠금 조회에는 복제본 대신 쓰기용 DataSource를 사용한다. */
@Repository
public class ChatOperationRepository {
    private final JdbcTemplate sql;
    private final TransactionTemplate transaction;
    private final long leaseMillis;

    public ChatOperationRepository(DataSource source,
            @Qualifier("transactionManager") PlatformTransactionManager manager,
            @Value("${chat.recovery.lease-seconds:300}") long leaseSeconds) {
        this.sql = new JdbcTemplate(source);
        this.sql.setQueryTimeout(15);
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transaction.setTimeout(30);
        this.leaseMillis = Math.max(1, leaseSeconds) * 1000;
    }

    public ChatOperationToken begin(Long userId, String sessionId, String kind) {
        return transaction.execute(status -> {
            try {
                sql.update("INSERT INTO chat_operation_guards(user_id, fence) VALUES (?, 0)", userId);
            } catch (DuplicateKeyException alreadyExists) {
                // 이 INSERT만 실패했으며 MySQL 트랜잭션은 계속 사용할 수 있다.
            }
            Map<String, Object> guard = lockGuard(userId);
            if (guard.get("operation_id") != null) throw new ChatHandler(ErrorStatus.CHAT_OPERATION_BUSY);
            long fence = ((Number) guard.get("fence")).longValue() + 1;
            String id = UUID.randomUUID().toString();
            long now = System.currentTimeMillis();
            sql.update("INSERT INTO chat_operations(operation_id,user_id,session_id,kind,status,fence,"
                + "lease_until,next_attempt_at,created_at,updated_at) VALUES (?,?,?,?,'PREPARING',?,?,?,?,?)",
                id, userId, sessionId, kind, fence, now + leaseMillis, now, now, now);
            sql.update("UPDATE chat_operation_guards SET operation_id=?, fence=? WHERE user_id=?", id, fence, userId);
            return new ChatOperationToken(id, userId, sessionId, fence);
        });
    }

    public <T> T owned(ChatOperationToken token, Supplier<T> action) {
        return transaction.execute(status -> {
            lockInCaller(token);
            return action.get();
        });
    }

    /** 상위 계정 초기화·삭제 트랜잭션이 커밋될 때까지 보호 잠금을 유지할 때도 사용한다. */
    public void lockInCaller(ChatOperationToken token) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("SQL transaction required");
        Map<String, Object> guard = lockGuard(token.userId());
        ChatOperation operation = select(token.id(), true);
        if (!token.id().equals(guard.get("operation_id"))
                || token.fence() != ((Number) guard.get("fence")).longValue()
                || operation == null || operation.terminal() || operation.status().equals("MANUAL")) {
            throw new ChatHandler(ErrorStatus.CHAT_OPERATION_BUSY);
        }
    }

    public void markSqlCommitted(ChatOperationToken token) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("SQL transaction required");
        if (sql.update("UPDATE chat_operations SET sql_committed=TRUE, updated_at=? WHERE operation_id=? AND fence=?",
                System.currentTimeMillis(), token.id(), token.fence()) != 1) throw new IllegalStateException("Lost operation");
    }

    public void finish(ChatOperationToken token, boolean completed) {
        owned(token, () -> {
            ChatOperation operation = find(token.id());
            if (!completed && operation.sqlCommitted()) throw new IllegalStateException("Committed profile cannot be cancelled");
            sql.update("UPDATE chat_operations SET status=?, updated_at=?, last_error=NULL WHERE operation_id=?",
                completed ? "COMPLETED" : "CANCELLED", System.currentTimeMillis(), token.id());
            sql.update("UPDATE chat_operation_guards SET operation_id=NULL WHERE user_id=?", token.userId());
            return null;
        });
    }

    public ChatOperation find(String id) {
        return select(id, false);
    }

    private ChatOperation select(String id, boolean lock) {
        List<ChatOperation> rows = sql.query("SELECT * FROM chat_operations WHERE operation_id=?" + (lock ? " FOR UPDATE" : ""),
            (row, index) ->
            new ChatOperation(new ChatOperationToken(row.getString("operation_id"), row.getLong("user_id"),
                row.getString("session_id"), row.getLong("fence")), row.getString("kind"), row.getString("status"),
                row.getBoolean("sql_committed"), row.getInt("attempts"), row.getLong("lease_until"),
                row.getLong("next_attempt_at")), id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** 보호 잠금은 원래 프로필 트랜잭션의 커밋 또는 롤백을 기다린다. */
    public ChatOperation reclaim(String id, Long expectedFence) {
        return transaction.execute(status -> {
            ChatOperation initial = find(id);
            if (initial == null) return null;
            Map<String, Object> guard = lockGuard(initial.token().userId());
            ChatOperation current = select(id, true);
            long now = System.currentTimeMillis();
            if (current.terminal() || current.status().equals("MANUAL") || !id.equals(guard.get("operation_id"))) return null;
            if (expectedFence != null && current.token().fence() != expectedFence) return null;
            if (expectedFence == null && (current.leaseUntil() > now || current.nextAttemptAt() > now)) return null;
            long fence = ((Number) guard.get("fence")).longValue() + 1;
            sql.update("UPDATE chat_operation_guards SET fence=? WHERE user_id=?", fence, current.token().userId());
            sql.update("UPDATE chat_operations SET fence=?,status='RECOVERING',lease_until=?,updated_at=? WHERE operation_id=?",
                fence, now + leaseMillis, now, id);
            return select(id, true);
        });
    }

    public void defer(ChatOperationToken token, String errorCode) {
        owned(token, () -> {
            int attempts = find(token.id()).attempts() + 1;
            long now = System.currentTimeMillis();
            long next = now + Math.min(60_000L, 1000L << Math.min(attempts, 6));
            sql.update("UPDATE chat_operations SET status=?,attempts=?,lease_until=?,next_attempt_at=?,"
                    + "updated_at=?,last_error=? WHERE operation_id=?", attempts >= 8 ? "MANUAL" : "RETRY",
                attempts, next, next, now, errorCode, token.id());
            return null;
        });
    }

    public List<String> due(int limit) {
        long now = System.currentTimeMillis();
        return sql.queryForList("SELECT operation_id FROM chat_operations WHERE status IN ('PREPARING','RECOVERING','RETRY')"
            + " AND lease_until<=? AND next_attempt_at<=? ORDER BY created_at LIMIT ?", String.class, now, now, limit);
    }

    public Map<String, Object> pendingStats() {
        return sql.queryForMap("SELECT COUNT(*) AS pending_count, MIN(created_at) AS oldest,"
            + " COALESCE(SUM(CASE WHEN status='MANUAL' THEN 1 ELSE 0 END),0) AS manual_count"
            + " FROM chat_operations WHERE status NOT IN ('COMPLETED','CANCELLED')");
    }

    private Map<String, Object> lockGuard(Long userId) {
        return sql.queryForMap("SELECT operation_id,fence FROM chat_operation_guards WHERE user_id=? FOR UPDATE", userId);
    }
}
