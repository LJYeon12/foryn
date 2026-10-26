package com.forA.chatbot.chat.component.impl;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.ChatHandler;
import com.forA.chatbot.chat.component.ChatStepHandler;
import com.forA.chatbot.chat.domain.ChatSession;
import com.forA.chatbot.chat.domain.enums.ChatStep;
import com.forA.chatbot.chat.domain.enums.EmotionType;
import com.forA.chatbot.chat.domain.enums.MessageType;
import com.forA.chatbot.chat.dto.ButtonOption;
import com.forA.chatbot.chat.dto.ChatBotMessage;
import com.forA.chatbot.chat.dto.ChatRequest;
import com.forA.chatbot.user.domain.User;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class EmotionStepHandler implements ChatStepHandler {
    @Override
    public String getStepName() {
        return ChatStep.EMOTION_SELECT.name();
    }

    @Override
    public String handleRequest(ChatSession session, User user, ChatRequest request) {
        String responseValue = request.getResponseValue();
        String[] selections = responseValue.split(",");

        if (selections.length > 2) {
            throw new ChatHandler(ErrorStatus.CHAT_SELECTION_LIMIT_EXCEEDED);
        }
        Set<EmotionType> selectedEmotions = Arrays.stream(selections)
                .map(String::trim)
                .map(name -> Arrays.stream(EmotionType.values())
                        .filter(e -> e.getName().equals(name))
                        .findFirst()
                        .orElseThrow(() -> new ChatHandler(ErrorStatus.INVALID_BUTTON_SELECTION)))
                .collect(Collectors.toSet());

        String emotionsStringValue = selectedEmotions.stream()
                .map(Enum::name)
                .collect(Collectors.joining(","));
        session.setTemporaryData("selectedEmotions", emotionsStringValue);

        if (!session.getOnboardingCompleted()) {
            session.setOnboardingCompleted(true);
        }
        boolean isPositiveOrSoSo = selectedEmotions.stream().allMatch(e ->
                e.getState() == EmotionType.EmotionState.POSITIVE || e == EmotionType.SO_SO
        );
        // 감정 상태에 따른 분기 처리
        if (isPositiveOrSoSo) {
            session.setTemporaryData("endReason", "POSITIVE_EMOTION");
            return ChatStep.CHAT_END.name();
        } else {
            return ChatStep.SITUATION_INPUT.name();
        }
    }

    @Override
    public ChatBotMessage getBotMessage(ChatSession session, User user) {
        String nickname = user.getNickname() != null ? user.getNickname() : "USER";
        String content = session.getOnboardingCompleted() ?
                String.format("안녕하세요, %s님! 포린이에요! 🐾오늘은 기분이 어때요? 포린이 눈치 빠르게 알아챌 수 있게 이모지 두 개만 콕! 찍어주세요.", nickname) :
                String.format("감사합니다! 모든 데이터는 마이페이지에서 수정과 삭제가 가능합니다. %s님 지금 어떤 기분이에요? 포린이 알아챌 수 있게 이모지 골라주세요", nickname);

        List<ButtonOption> options = Arrays.stream(EmotionType.values())
                .map(emotion -> ButtonOption.builder()
                        .label(emotion.getName())
                        .emoji(emotion.getEmoji())
                        .multiSelect(true)
                        .group(emotion.getState().name())
                        .build())
                .collect(Collectors.toList());

        return ChatBotMessage.builder()
                .content(content)
                .type(MessageType.OPTION)
                .options(options)
                .build();
    }
}
