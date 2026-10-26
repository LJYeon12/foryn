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
import com.forA.chatbot.user.domain.User;
import com.forA.chatbot.user.domain.enums.DisorderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DisorderStepHandler implements ChatStepHandler {
    @Override
    public void validateState(ChatSession session, User user) {
        if (user.getJobs() == null) {
            log.warn("데이터 불일치 감지(Atomic Gap). 세션을 초기 단계로 복구합니다. UserId: {}", user.getId());
            session.setCurrentStep(ChatStep.JOB_TYPE.name());
            throw new ChatHandler(ErrorStatus.INCONSISTENT_CHAT_STATE);
        }
    }
    @Override
    public String getStepName() {
        return ChatStep.DISORDER_TYPE.name();
    }

    @Override
    public String handleRequest(ChatSession session, User user, ChatRequest request) {
        String responseValue = request.getResponseValue();

        String[] selections = responseValue.split(",");

        if (selections.length > 2) {
            throw new ChatHandler(ErrorStatus.CHAT_SELECTION_LIMIT_EXCEEDED);
        }
        Set<DisorderType> selectedDisorders = new HashSet<>();
        for (String selection : selections) {
            String label = selection.trim();
            DisorderType disorder = Arrays.stream(DisorderType.values())
                    .filter(d -> d.getName().equals(label))
                    .findFirst()
                    .orElseThrow(() -> new ChatHandler(ErrorStatus.INVALID_BUTTON_SELECTION));
            selectedDisorders.add(disorder);
        }
        user.updateDisorders(selectedDisorders);
        if (selectedDisorders.contains(DisorderType.NONE)) {
            session.setOnboardingCompleted(true); // 온보딩 완료 처리
            return ChatStep.EMOTION_SELECT.name();
        }

        return ChatStep.SYMPTOM_TYPE.name();
    }

    @Override
    public ChatBotMessage getBotMessage(ChatSession session, User user) {
        List<ButtonOption> options = Arrays.stream(DisorderType.values())
                .map(disorder -> ButtonOption.builder()
                        .label(disorder.getName())
                        .multiSelect(true)
                        .build())
                .collect(Collectors.toList());
        return ChatBotMessage.builder()
                .content("앓고 있는 정신 질환이 있으신가요? 최대 2개까지 선택할 수 있어요!")
                .type(MessageType.OPTION)
                .options(options)
                .build();
    }
}
