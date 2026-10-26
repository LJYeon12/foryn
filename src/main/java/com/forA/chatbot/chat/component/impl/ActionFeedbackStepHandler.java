package com.forA.chatbot.chat.component.impl;

import com.forA.chatbot.chat.component.ChatStepHandler;
import com.forA.chatbot.chat.domain.ChatSession;
import com.forA.chatbot.chat.domain.enums.ChatStep;
import com.forA.chatbot.chat.domain.enums.MessageType;
import com.forA.chatbot.chat.dto.ChatBotMessage;
import com.forA.chatbot.chat.dto.ChatRequest;
import com.forA.chatbot.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActionFeedbackStepHandler implements ChatStepHandler {

    @Override
    public String getStepName() {
        return ChatStep.ACTION_FEEDBACK.name();
    }

    @Override
    public String handleRequest(ChatSession session, User user, ChatRequest request) {
        String feedbackValue = request.getResponseValue();

        session.setTemporaryData("feedbackValue", feedbackValue);
        session.setTemporaryData("endReason", "FEEDBACK_DONE");

        return ChatStep.CHAT_END.name();
    }

    @Override
    public ChatBotMessage getBotMessage(ChatSession session, User user) {
        return ChatBotMessage.builder()
                .content("지금 마음은 조금 나아지셨는지 궁금해요. 기분을 체크해주세요!")
                .type(MessageType.INPUT)
                .build();
    }
}
