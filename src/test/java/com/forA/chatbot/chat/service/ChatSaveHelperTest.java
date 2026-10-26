package com.forA.chatbot.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.ChatHandler;
import com.forA.chatbot.chat.converter.ChatConverter;
import com.forA.chatbot.chat.domain.ChatOperationToken;
import com.forA.chatbot.chat.domain.ChatSession;
import com.forA.chatbot.chat.domain.enums.MessageType;
import com.forA.chatbot.chat.dto.ChatBotMessage;
import com.forA.chatbot.chat.dto.ChatRequest;
import com.forA.chatbot.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatSaveHelperTest {
    @Mock ChatOperationService operations;
    @Mock ChatAfterCommitActions afterCommit;
    ChatSaveHelper helper;
    final ChatOperationToken token = new ChatOperationToken("op", 1L, "session", 1);

    @BeforeEach void setup() {
        helper = new ChatSaveHelper(new ChatConverter(new ObjectMapper()), operations, afterCommit);
    }

    @Test void failedStorageDoesNotRunNotificationEffects() {
        doThrow(new ChatHandler(ErrorStatus.CHAT_RECOVERY_PENDING)).when(operations).save(any(), any());
        assertThatThrownBy(() -> save("SKILL_CONFIRM"))
            .isInstanceOf(ChatHandler.class).hasFieldOrPropertyWithValue("code", ErrorStatus.CHAT_RECOVERY_PENDING);
        verify(afterCommit, never()).run(any());
    }

    @Test void successfulTurnRetainsMessageStepsAndRunsEffectsAfterStorage() {
        assertThat(save("CHAT_END")).isTrue();
        var captured = ArgumentCaptor.forClass(PreparedChatTurn.class);
        var order = org.mockito.Mockito.inOrder(operations, afterCommit);
        order.verify(operations).save(org.mockito.ArgumentMatchers.eq(token), captured.capture());
        order.verify(afterCommit).run(captured.getValue());
        assertThat(captured.getValue().userMessage().getChatStep()).isEqualTo("SKILL_SELECT");
        assertThat(captured.getValue().botMessage().getChatStep()).isEqualTo("CHAT_END");
        assertThat(captured.getValue().session().getEndedAt()).isNotNull();
        assertThat(captured.getValue().profileDelta().changed()).isFalse();
    }

    @Test void conversionFailureReleasesTheClaimBeforeReturningAnInputError() {
        ChatConverter failing = org.mockito.Mockito.mock(ChatConverter.class);
        org.mockito.Mockito.when(failing.toBotMessageEntity(any(), any(), any()))
            .thenThrow(new ChatHandler(ErrorStatus.JSON_PROCESSING_ERROR));
        helper = new ChatSaveHelper(failing, operations, afterCommit);
        assertThatThrownBy(() -> save("CHAT_END")).isInstanceOf(ChatHandler.class);
        verify(operations).cancelPreparation(token);
        verify(operations, never()).save(any(), any());
        verify(afterCommit, never()).run(any());
    }

    private boolean save(String next) {
        ChatRequest request = new ChatRequest();
        request.setResponseValue("synthetic");
        User user = User.builder().id(1L).build();
        return helper.saveChatResult(token,
            ChatSession.builder().id("session").userId(1L).currentStep("SKILL_SELECT").build(),
            user, UserProfileDelta.workingCopy(user), request, next,
            ChatBotMessage.builder().type(MessageType.TEXT).content("synthetic").build());
    }
}
