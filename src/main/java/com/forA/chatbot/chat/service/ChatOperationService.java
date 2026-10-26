package com.forA.chatbot.chat.service;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.ChatHandler;
import com.forA.chatbot.chat.domain.ChatOperationToken;
import com.forA.chatbot.chat.domain.ChatSession;
import com.forA.chatbot.chat.repository.ChatOperationRepository;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/** 단일 진입점에서 여러 저장소의 작업 완료를 담당한다. 핸들러는 보상 처리를 하지 않는다. */
@Service
@RequiredArgsConstructor
public class ChatOperationService {
    private final ChatOperationRepository operations;
    private final MongoChatWriter mongo;
    private final ChatProfileWriter profiles;
    private final ChatRecoveryService recovery;

    public ChatOperationToken begin(Long userId, String sessionId, String kind) {
        ChatOperationToken token = operations.begin(userId, sessionId, kind);
        try {
            mongo.reserve(token);
            return token;
        } catch (RuntimeException failure) {
            throw storageFailure(recovery.recover(token));
        }
    }

    public void save(ChatOperationToken token, PreparedChatTurn turn) {
        try {
            operations.owned(token, () -> {
                if (turn.profileDelta().changed()) {
                    profiles.apply(token.userId(), turn.profileDelta());
                    mongo.stage(token, turn);
                    operations.markSqlCommitted(token);
                } else {
                    mongo.commitTurn(token, turn);
                }
                return null;
            });
            if (turn.profileDelta().changed()) mongo.publish(token);
            operations.finish(token, true);
        } catch (RuntimeException failure) {
            ChatRecoveryService.Result result = recovery.recover(token);
            if (result != ChatRecoveryService.Result.COMPLETED) throw storageFailure(result);
        }
    }

    public <T> T lifecycle(ChatOperationToken token, Supplier<T> action) {
        AtomicReference<T> result = new AtomicReference<>();
        try {
            operations.owned(token, () -> mongo.lifecycle(token, () -> {
                T value = action.get();
                result.set(value);
                return value;
            }));
            operations.finish(token, true);
            return result.get();
        } catch (RuntimeException failure) {
            ChatRecoveryService.Result resolved = recovery.recover(token);
            if (resolved == ChatRecoveryService.Result.COMPLETED) return result.get();
            if (resolved == ChatRecoveryService.Result.PENDING) throw storageFailure(resolved);
            throw failure;
        }
    }

    public void repair(ChatOperationToken token, ChatSession session) {
        operations.owned(token, () -> {
            mongo.repair(token, session);
            return null;
        });
        operations.finish(token, true);
    }

    public void cancelPreparation(ChatOperationToken token) {
        if (recovery.recover(token) == ChatRecoveryService.Result.PENDING) throw storageFailure(ChatRecoveryService.Result.PENDING);
    }

    private ChatHandler storageFailure(ChatRecoveryService.Result result) {
        return new ChatHandler(result == ChatRecoveryService.Result.PENDING
            ? ErrorStatus.CHAT_RECOVERY_PENDING : ErrorStatus.CHAT_PROCESSING_ERROR);
    }
}
