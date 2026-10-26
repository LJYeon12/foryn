package com.forA.chatbot.chat.service;

import com.forA.chatbot.chat.converter.ChatConverter;
import com.forA.chatbot.chat.domain.ChatSession;
import com.forA.chatbot.chat.domain.ChatOperationToken;
import com.forA.chatbot.chat.domain.enums.ChatStep;
import com.forA.chatbot.chat.dto.ChatBotMessage;
import com.forA.chatbot.chat.dto.ChatRequest;
import com.forA.chatbot.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ChatSaveHelper {
    private final ChatConverter chatConverter;
    private final ChatOperationService operations;
    private final ChatAfterCommitActions afterCommit;

    public boolean saveChatResult(ChatOperationToken token, ChatSession session, User before, User working,
            ChatRequest request, String nextStepName, ChatBotMessage botMessage) {
        PreparedChatTurn prepared;
        try {
            prepared = prepare(session, before, working, request, nextStepName, botMessage);
        } catch (RuntimeException failure) {
            operations.cancelPreparation(token);
            throw failure;
        }
        operations.save(token, prepared);
        afterCommit.run(prepared);
        return ChatStep.CHAT_END.name().equals(nextStepName);
    }

    private PreparedChatTurn prepare(ChatSession session, User before, User working,
            ChatRequest request, String nextStepName, ChatBotMessage botMessage) {
        String currentStepName = session.getCurrentStep();
        var userMessage = chatConverter.toUserMessageEntity(session.getId(), currentStepName, request.getResponseValue());
        var bot = chatConverter.toBotMessageEntity(session.getId(), nextStepName, botMessage);
        // ChatSession 업데이트
        session.setCurrentStep(nextStepName);
        session.setLastInteractionAt(LocalDateTime.now());

        boolean isChatEnd = ChatStep.CHAT_END.name().equals(nextStepName);
        if (isChatEnd) {
            session.setEndedAt(LocalDateTime.now());
            session.clearTemporaryData();
        }
        return new PreparedChatTurn(session, userMessage, bot, UserProfileDelta.between(before, working));
    }
}
