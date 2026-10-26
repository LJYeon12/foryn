package com.forA.chatbot.chat.service;

import com.forA.chatbot.chat.domain.ChatOperationToken;
import com.forA.chatbot.chat.domain.ChatSession;
import com.forA.chatbot.chat.repository.ChatMessageRepository;
import com.forA.chatbot.chat.repository.ChatOperationRepository;
import com.forA.chatbot.chat.repository.ChatSessionRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 상위 계정 트랜잭션이 완료될 때까지 SQL 보호 잠금을 유지한다. */
@Component
public class ChatAccountDeletion {
    private final ChatOperationService coordinator;
    private final ChatOperationRepository operations;
    private final MongoChatWriter mongo;
    private final ChatRecoveryService recovery;
    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final TransactionTemplate transaction;

    public ChatAccountDeletion(ChatOperationService coordinator, ChatOperationRepository operations, MongoChatWriter mongo,
            ChatRecoveryService recovery, ChatSessionRepository sessions, ChatMessageRepository messages,
            @Qualifier("transactionManager") PlatformTransactionManager manager) {
        this.coordinator = coordinator;
        this.operations = operations;
        this.mongo = mongo;
        this.recovery = recovery;
        this.sessions = sessions;
        this.messages = messages;
        this.transaction = new TransactionTemplate(manager);
    }

    public void delete(Long userId) {
        ChatOperationToken token = coordinator.begin(userId, null, "DELETE");
        transaction.executeWithoutResult(status -> {
            operations.lockInCaller(token);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int completionStatus) {
                    recovery.recover(token);
                }
            });
            mongo.lifecycle(token, () -> {
                var ids = sessions.findByUserId(userId).stream().map(ChatSession::getId).toList();
                if (!ids.isEmpty()) messages.deleteBySessionIdIn(ids);
                sessions.deleteByUserId(userId);
                return null;
            });
        });
    }
}
