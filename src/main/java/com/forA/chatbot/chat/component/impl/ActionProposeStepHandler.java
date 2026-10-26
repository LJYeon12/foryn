package com.forA.chatbot.chat.component.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.ChatHandler;
import com.forA.chatbot.chat.component.BehavioralSkillProvider;
import com.forA.chatbot.chat.component.ChatStepHandler;
import com.forA.chatbot.chat.domain.ChatSession;
import com.forA.chatbot.chat.domain.enums.ChatStep;
import com.forA.chatbot.chat.domain.enums.EmotionType;
import com.forA.chatbot.chat.domain.enums.MessageType;
import com.forA.chatbot.chat.dto.BehavioralSkill;
import com.forA.chatbot.chat.dto.ButtonOption;
import com.forA.chatbot.chat.dto.ChatBotMessage;
import com.forA.chatbot.chat.dto.ChatRequest;
import com.forA.chatbot.chat.service.ChatAiService;
import com.forA.chatbot.chat.util.ChatUtils;
import com.forA.chatbot.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActionProposeStepHandler implements ChatStepHandler {

    private final ChatAiService chatAiService;
    private final BehavioralSkillProvider behavioralSkillProvider;
    private final ObjectMapper objectMapper;

    @Override
    public String getStepName() {
        return ChatStep.ACTION_PROPOSE.name();
    }

    @Override
    public String handleRequest(ChatSession session, User user, ChatRequest request) {
        String selectedSkillName = request.getResponseValue();

        // 이름으로 스킬 찾기
        BehavioralSkill selectedSkill = behavioralSkillProvider.getAllSkills().stream()
                .filter(skill -> skill.getSkillName().equals(selectedSkillName))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("선택하신 스킬을 찾을 수 없습니다: {}", selectedSkillName);
                    return new ChatHandler(ErrorStatus.INVALID_BUTTON_SELECTION);
                });

        // 세션에 선택된 스킬 정보 저장
        session.setTemporaryData("selectedSkillId", selectedSkill.getChunkId());
        session.setTemporaryData("selectedSkillName", selectedSkill.getSkillName());

        // 다음 단계(SKILL_SELECT)를 위한 데이터 생성 및 저장
        String userSituation = session.getTemporaryData("userSituation");
        String selectedEmotionsString = session.getTemporaryData("selectedEmotions");
        Set<EmotionType> selectedEmotions = ChatUtils.parseEmotionsFromString(selectedEmotionsString);

        // 1. 상세 행동 단계 생성
        List<String> detailedSteps = chatAiService.generateDetailedSkillSteps(selectedSkill);
        try {
            String detailedStepsJson = objectMapper.writeValueAsString(detailedSteps);
            session.setTemporaryData("detailedSteps", detailedStepsJson);
        } catch (JsonProcessingException e) {
            log.error("detailedSteps JSON 직렬화 실패", e);
            throw new ChatHandler(ErrorStatus.JSON_PROCESSING_ERROR);
        }

        // 2. 맞춤형 설명 생성
        String customDescription = chatAiService.generateSkillDescription(userSituation, selectedEmotions, selectedSkill, user);
        session.setTemporaryData("customDescription", customDescription);

        return ChatStep.SKILL_SELECT.name();
    }

    @Override
    public ChatBotMessage getBotMessage(ChatSession session, User user) {
        String recommendedIdsString = session.getTemporaryData("recommendedSkillIds");
        List<String> recommendedIds = Arrays.asList(recommendedIdsString.split(","));

        // 추천된 ID에 해당하는 스킬들을 찾아서 버튼 옵션으로 변환
        List<ButtonOption> options = behavioralSkillProvider.getAllSkills().stream()
                .filter(skill -> recommendedIds.contains(skill.getChunkId()))
                .map(skill -> ButtonOption.builder()
                        .label(skill.getSkillName())
                        .build())
                .collect(Collectors.toList());

        String content = "좋아요, 그럼 지금 이 감정에 도움이 될 수 있는 방법들을 하나씩 소개해볼게요.\n"
                + "지금 감정에 도움이 될 수 있는 방법들을 소개했어요. 이 중에서 하나 골라 함께 해볼까요?";

        return ChatBotMessage.builder()
                .content(content)
                .type(MessageType.OPTION)
                .options(options)
                .build();
    }
}
