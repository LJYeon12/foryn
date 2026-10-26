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
import com.forA.chatbot.user.domain.enums.SymptomType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SymptomStepHandler implements ChatStepHandler {
    @Override
    public void validateState(ChatSession session, User user) {
        if (user.getDisorders() == null) {
            log.warn("데이터 불일치 감지(Atomic Gap). 세션을 초기 단계로 복구합니다. UserId: {}", user.getId());
            session.setCurrentStep(ChatStep.DISORDER_TYPE.name());
            throw new ChatHandler(ErrorStatus.INCONSISTENT_CHAT_STATE);
        }
    }

    @Override
    public String getStepName() {
        return ChatStep.SYMPTOM_TYPE.name();
    }

    @Override
    public String handleRequest(ChatSession session, User user, ChatRequest request) {
        String responseValue = request.getResponseValue();

        Set<SymptomType> relevantSymptoms = SymptomType.getByDisorderTypes(user.getDisorders());
        Set<SymptomType> selectedSymptoms = relevantSymptoms.stream()
                .filter(symptom -> responseValue.contains(symptom.getDescription()))
                .collect(Collectors.toSet());

        if (selectedSymptoms.isEmpty()) {
            throw new ChatHandler(ErrorStatus.INVALID_BUTTON_SELECTION);
        }

        Map<DisorderType, Long> countByDisorder = selectedSymptoms.stream()
                .collect(Collectors.groupingBy(SymptomType::getDisorderType, Collectors.counting()));

        boolean limitExceeded = countByDisorder.values().stream().anyMatch(count -> count > 3);
        if (limitExceeded) {
            throw new ChatHandler(ErrorStatus.CHAT_SELECTION_LIMIT_EXCEEDED);
        }

        user.updateSymptoms(selectedSymptoms);

        return ChatStep.EMOTION_SELECT.name();
    }

    @Override
    public ChatBotMessage getBotMessage(ChatSession session, User user) {
        Set<SymptomType> relevantSymptoms = SymptomType.getByDisorderTypes(user.getDisorders());

        // 만약 선택된 질환이 없거나 매핑된 증상이 없으면 전체 노출 (안전장치)
        if (relevantSymptoms.isEmpty()) {
            relevantSymptoms = Set.of(SymptomType.values());
        }
        List<ButtonOption> options = relevantSymptoms.stream()
                .map(symptom -> ButtonOption.builder()
                        .label(symptom.getDescription())
                        .group(symptom.getDisorderType().getName())
                        .multiSelect(true)
                        .build())
                .collect(Collectors.toList());
        return ChatBotMessage.builder()
                .content("주로 힘들어 하는 일은 어떤건가요? 포린이 참고해서 도와줄게요. 질환 당 최대 3개까지 선택할 수 있어요!")
                .type(MessageType.OPTION)
                .options(options)
                .build();
    }
}
