package com.forA.chatbot.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.ChatHandler;
import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.chat.component.ChatStepHandler;
import com.forA.chatbot.chat.component.ChatStepHandlerProvider;
import com.forA.chatbot.chat.converter.ChatConverter;
import com.forA.chatbot.chat.domain.ChatSession;
import com.forA.chatbot.chat.dto.ChatBotMessage;
import com.forA.chatbot.chat.dto.ChatRequest;
import com.forA.chatbot.chat.repository.ChatMessageRepository;
import com.forA.chatbot.chat.repository.ChatSessionRepository;
import com.forA.chatbot.user.domain.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class ChatTransactionContractTest {
    @Mock ChatSessionRepository sessions;
    @Mock ChatMessageRepository messages;
    @Mock UserRepository users;
    @Mock ChatStepHandlerProvider handlers;
    @Mock ChatSaveHelper saves;
    @Mock ChatOperationService operations;
    @Mock ChatAccountDeletion deletion;
    @Mock ChatStepHandler current;
    @Mock ChatStepHandler next;
    @Spy ChatConverter converter = new ChatConverter(new ObjectMapper());
    @InjectMocks ChatService service;

    @BeforeEach void operation() {
        org.mockito.Mockito.lenient().when(operations.begin(any(), any(), any()))
            .thenReturn(new com.forA.chatbot.chat.domain.ChatOperationToken("op", 1L, "own", 1));
    }

    @Test
    void foreignSessionIsRejectedBeforePreparingOrWriting() {
        when(sessions.findById("foreign")).thenReturn(Optional.of(ChatSession.builder()
            .id("foreign").userId(2L).currentStep("SITUATION_INPUT").build()));
        org.mockito.Mockito.lenient().when(users.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        assertThatThrownBy(() -> service.handleUserResponse(1L, "foreign", new ChatRequest()))
            .isInstanceOf(ChatHandler.class).hasFieldOrPropertyWithValue("code", ErrorStatus.SESSION_NOT_FOUND);
    }

    @Test
    void handlerAndAiPreparationRunOutsideDatabaseTransactions() {
        when(sessions.findById("own")).thenReturn(Optional.of(ChatSession.builder()
            .id("own").userId(1L).currentStep("SITUATION_INPUT").build()));
        when(users.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        when(handlers.getHandler("SITUATION_INPUT")).thenReturn(current);
        when(handlers.getHandler("EMOTION_SELECT")).thenReturn(next);
        when(current.handleRequest(any(), any(), any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                .as("AI_PREPARATION_MUST_NOT_HOLD_JPA_TRANSACTION").isFalse();
            return "EMOTION_SELECT";
        });
        when(next.getBotMessage(any(), any())).thenReturn(ChatBotMessage.builder().build());
        ProxyFactory proxy = new ProxyFactory(service);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new LocalTransactionManager(), new AnnotationTransactionAttributeSource()));
        ((ChatService) proxy.getProxy()).handleUserResponse(1L, "own", new ChatRequest());
    }

    private static class LocalTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }
}
