package com.forA.chatbot.chat.component.impl;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.ChatHandler;
import com.forA.chatbot.apiPayload.exception.handler.UserHandler;
import com.forA.chatbot.chat.component.ChatStepHandler;
import com.forA.chatbot.chat.domain.ChatSession;
import com.forA.chatbot.chat.domain.enums.ChatStep;
import com.forA.chatbot.chat.domain.enums.MessageType;
import com.forA.chatbot.chat.dto.ChatBotMessage;
import com.forA.chatbot.chat.dto.ChatRequest;
import com.forA.chatbot.enums.Gender;
import com.forA.chatbot.user.domain.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Year;

@Component
@Slf4j
public class BirthYearStepHandler implements ChatStepHandler {
    @Override
    public void validateState(ChatSession session, User user){
        if (user.getGender().equals(Gender.UNKNOWN)) {
            log.warn("데이터 불일치 감지(Atomic Gap). 세션을 초기 단계로 복구합니다. UserId: {}", user.getId());
            session.setCurrentStep(ChatStep.GENDER.name());
            throw new ChatHandler(ErrorStatus.INCONSISTENT_CHAT_STATE);
        }
    }
    @Override
    public String getStepName() {
        return ChatStep.BIRTH_YEAR.name();
    }

    @Override
    public String handleRequest(ChatSession session, User user, ChatRequest request) {
        String responseValue = request.getResponseValue();

        int birthYear;
        try {
            birthYear = Integer.parseInt(responseValue);
        } catch (NumberFormatException e) {
            throw new UserHandler(ErrorStatus.INVALID_YEAR_OF_BIRTH);
        }
        int currentYear = Year.now().getValue();
        if(birthYear < 1900 || birthYear > currentYear) {
            throw new UserHandler(ErrorStatus.INVALID_YEAR_OF_BIRTH);
        }
        user.updateBirthYear(birthYear);
        return ChatStep.JOB_TYPE.name();
    }

    @Override
    public ChatBotMessage getBotMessage(ChatSession session, User user) {
        return ChatBotMessage.builder()
                .content("알맞은 도움을 드리기 위해 연령대가 중요한 기준이 됩니다. 태어난 연도를 4자리 숫자를 알려주세요!")
                .type(MessageType.INPUT)
                .options(null)
                .build();
    }
}