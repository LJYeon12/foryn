package com.forA.chatbot.chat.component.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SkillSelectStepHandler implements ChatStepHandler {

    private final ObjectMapper objectMapper;

    @Override
    public String getStepName() {
        return ChatStep.SKILL_SELECT.name();
    }

    @Override
    public String handleRequest(ChatSession session, User user, ChatRequest request) {
        return ChatStep.SKILL_CONFIRM.name();
    }

    @Override
    public ChatBotMessage getBotMessage(ChatSession session, User user) {
        String customDescription = session.getTemporaryData("customDescription");
        String detailedStepsJson = session.getTemporaryData("detailedSteps");

        if (customDescription == null) {
            log.warn("선택한 스킬 정보를 찾을 수 없습니다. (customDescription is null)");
            customDescription = "선택하신 스킬 정보를 찾을 수 없어요. 다시 시도해 주시겠어요?";
        }

        List<String> detailedSteps;
        try {
            detailedSteps = objectMapper.readValue(detailedStepsJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.error("detailedSteps JSON 파싱 실패", e);
            throw new ChatHandler(ErrorStatus.JSON_PROCESSING_ERROR);
        }

        List<ButtonOption> options = detailedSteps.stream()
                .map(stepText -> ButtonOption.builder()
                        .label(stepText)
                        .multiSelect(false)
                        .build())
                .collect(Collectors.toList());

        return ChatBotMessage.builder()
                .content(customDescription)
                .type(MessageType.OPTION)
                .options(options)
                .build();
    }
}
