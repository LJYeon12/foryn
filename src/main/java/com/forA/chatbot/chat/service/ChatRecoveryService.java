package com.forA.chatbot.chat.service;

import com.forA.chatbot.chat.domain.ChatOperation;
import com.forA.chatbot.chat.domain.ChatOperationToken;
import com.forA.chatbot.chat.repository.ChatOperationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ChatRecoveryService {
    public enum Result { COMPLETED, CANCELLED, PENDING }
    private final ChatOperationRepository operations;
    private final MongoChatWriter mongo;
    private final MeterRegistry metrics;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong oldestCreatedAt = new AtomicLong();
    private final AtomicLong manual = new AtomicLong();

    public ChatRecoveryService(ChatOperationRepository operations, MongoChatWriter mongo, MeterRegistry metrics) {
        this.operations = operations;
        this.mongo = mongo;
        this.metrics = metrics;
        metrics.gauge("chat.recovery.pending", pending);
        metrics.gauge("chat.recovery.oldest.age.ms", oldestCreatedAt,
            oldest -> oldest.get() == 0 ? 0 : Math.max(0, System.currentTimeMillis() - oldest.get()));
        metrics.gauge("chat.recovery.manual", manual);
    }

    public Result recover(ChatOperationToken token) { return recover(token.id(), token.fence()); }

    public Result recover(String id, Long expectedFence) {
        Timer.Sample sample = Timer.start(metrics);
        ChatOperation claimed = null;
        try {
            ChatOperation current = operations.find(id);
            if (current == null) return Result.PENDING;
            if (current.terminal()) return current.status().equals("COMPLETED") ? Result.COMPLETED : Result.CANCELLED;
            claimed = operations.reclaim(id, expectedFence);
            if (claimed == null) return Result.PENDING;
            ChatOperation work = claimed;
            Result result = operations.owned(work.token(), () -> {
                String outcome = mongo.fenceAndInspect(work.token());
                if ("COMMITTED".equals(outcome)) return Result.COMPLETED;
                if (work.sqlCommitted()) {
                    if (!"STAGED".equals(outcome)) throw new IllegalStateException("Missing committed profile's staged turn");
                    mongo.publish(work.token());
                    return Result.COMPLETED;
                }
                mongo.cancel(work.token());
                return Result.CANCELLED;
            });
            operations.finish(work.token(), result == Result.COMPLETED);
            metrics.counter("chat.recovery.results", "result", result.name()).increment();
            return result;
        } catch (RuntimeException failure) {
            metrics.counter("chat.recovery.results", "result", "PENDING").increment();
            if (claimed != null) {
                try {
                    operations.defer(claimed.token(), failure.getClass().getSimpleName());
                } catch (RuntimeException unavailable) {
                    // 저장된 작업권은 다음 복구 실행까지 유지된다.
                }
            }
            log.warn("Chat recovery deferred; failureType={}", failure.getClass().getSimpleName());
            return Result.PENDING;
        } finally {
            sample.stop(metrics.timer("chat.recovery.duration"));
        }
    }

    public void recoverDue() {
        for (String id : operations.due(20)) recover(id, null);
        var stats = operations.pendingStats();
        pending.set(((Number) stats.get("pending_count")).longValue());
        manual.set(((Number) stats.get("manual_count")).longValue());
        Object oldest = stats.get("oldest");
        oldestCreatedAt.set(oldest == null ? 0 : ((Number) oldest).longValue());
    }
}
