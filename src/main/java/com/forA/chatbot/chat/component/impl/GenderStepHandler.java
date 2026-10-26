package com.forA.chatbot.chat.component.impl;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.ChatHandler;
import com.forA.chatbot.chat.component.ChatStepHandler;
import com.forA.chatbot.chat.domain.ChatSession;
import com.forA.chatbot.chat.domain.enums.ChatStep;
import com.forA.chatbot.chat.domain.enums.MessageType;
import com.forA.chatbot.chat.dto.ButtonOption;
import com.forA.chatbot.chat.dto.ChatBotMessage;
import com.forA.chatbot.chat.dto.ChatRequest;
import com.forA.chatbot.enums.Gender;
import com.forA.chatbot.user.domain.User;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GenderStepHandler implements ChatStepHandler {
    @Override
    public String getStepName() {
        return ChatStep.GENDER.name();
    }

    @Override
    public String handleRequest(ChatSession session, User user, ChatRequest request) {
        String responseValue = request.getResponseValue();
        Gender gender = mapToGender(responseValue);
        user.updateGender(gender);
        return ChatStep.BIRTH_YEAR.name();
    }

    @Override
    public ChatBotMessage getBotMessage(ChatSession session, User user) {
        return ChatBotMessage.builder()
                .content("안녕하세요," + user.getNickname()
                        + "님 :) 저는 " + user.getNickname()
                        + "님의 감정과 행동을 함께 살펴주는 AI 상담 친구 '포린'이에요. 포린이 24시 필요할 때 함께 도와줄게요 ADHD, 우울감, 불안 같은 감정들도 비난 없이, 천천히, 함께 마주볼 수 있어요. 편안한 마음으로 이야기를 시작해 주세요\n"
                        + "상담을 시작하기 전, 상담에 도움이 될 수 있는 질문을 몇가지 하겠습니다. 먼저, 성별을 선택해주세요!")
                .type(MessageType.OPTION)
                .options(List.of(
                        ButtonOption.builder().label("여성").multiSelect(false).build(),
                        ButtonOption.builder().label("남성").multiSelect(false).build(),
                        ButtonOption.builder().label("기타").multiSelect(false).build()))
                .build();
    }

    private Gender mapToGender(String responseValue) {
        if ("남성".equals(responseValue)) {
            return Gender.MALE;
        } else if ("여성".equals(responseValue)) {
            return Gender.FEMALE;
        } else if ("기타".equals(responseValue)) {
            return Gender.OTHER;
        } else {
            throw new ChatHandler(ErrorStatus.INVALID_BUTTON_SELECTION);
        }
    }
}
