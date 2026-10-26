package com.forA.chatbot.chat.component.impl;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.ChatHandler;
import com.forA.chatbot.chat.component.ChatStepHandler;
import com.forA.chatbot.chat.domain.ChatSession;
import com.forA.chatbot.chat.domain.enums.ChatStep;
import com.forA.chatbot.chat.domain.enums.EmotionType;
import com.forA.chatbot.chat.domain.enums.MessageType;
import com.forA.chatbot.chat.dto.ChatBotMessage;
import com.forA.chatbot.chat.dto.ChatRequest;
import com.forA.chatbot.chat.service.ChatAiService;
import com.forA.chatbot.chat.util.ChatUtils;
import com.forA.chatbot.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SituationInputStepHandler implements ChatStepHandler {

    private final ChatAiService chatAiService;

    @Override
    public String getStepName() {
        return ChatStep.SITUATION_INPUT.name();
    }

    @Override
    public String handleRequest(ChatSession session, User user, ChatRequest request) {
        String userSituation = request.getResponseValue();
        session.setTemporaryData("userSituation", userSituation);

        String selectedEmotionsString = session.getTemporaryData("selectedEmotions");
        Set<EmotionType> selectedEmotions = ChatUtils.parseEmotionsFromString(selectedEmotionsString);

        String empathySentence = chatAiService.generateEmpathyResponse(userSituation, selectedEmotions, user);
        String goalPhrase = chatAiService.generateProposalGoalPhrase(userSituation, selectedEmotions);

        session.setTemporaryData("empathySentence", empathySentence);
        session.setTemporaryData("goalPhrase", goalPhrase);

        return ChatStep.ACTION_OFFER.name();
    }

    @Override
    public ChatBotMessage getBotMessage(ChatSession session, User user) {
        String selectedEmotionsString = session.getTemporaryData("selectedEmotions");
        Set<EmotionType> selectedEmotions = ChatUtils.parseEmotionsFromString(selectedEmotionsString);

        if (selectedEmotions.isEmpty()) {
            throw new ChatHandler(ErrorStatus.SESSION_NOT_FOUND);
        }

        String emotionNames = selectedEmotions.stream()
                .map(EmotionType::getName)
                .collect(Collectors.joining(" ", "지금 ", " 상태이시군요."));

        return ChatBotMessage.builder()
                .content(emotionNames + " 혹시 어떤 일이 있었는지 이야기 해줄 수 있나요?")
                .type(MessageType.INPUT)
                .build();
    }
}
