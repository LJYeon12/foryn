package com.forA.chatbot.chat.component.impl;

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
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SkillConfirmStepHandler implements ChatStepHandler {

    private final ChatAiService chatAiService;
    private final BehavioralSkillProvider behavioralSkillProvider;

    @Override
    public String getStepName() {
        return ChatStep.SKILL_CONFIRM.name();
    }

    @Override
    public String handleRequest(ChatSession session, User user, ChatRequest request) {
        String responseValue = request.getResponseValue();

        if ("응, 하고 왔어".equals(responseValue)) {
            return ChatStep.ACTION_FEEDBACK.name();
        } else if ("아니, 안 하고 왔어".equals(responseValue)) {
            processSkippedAction(session);
            return ChatStep.CHAT_END.name();
        } else {
            throw new ChatHandler(ErrorStatus.INVALID_BUTTON_SELECTION);
        }
    }

    @Override
    public ChatBotMessage getBotMessage(ChatSession session, User user) {
        String skillName = session.getTemporaryData("selectedSkillName");
        String nickname = user.getNickname() != null ? user.getNickname() : "USER";

        if (skillName == null) {
             throw new ChatHandler(ErrorStatus.SESSION_NOT_FOUND); 
        }
        String content = "포린이 기다리고 있었어요! " + nickname + "님 " + skillName + " 하고 오셨나요?";
        return ChatBotMessage.builder()
                .content(content)
                .type(MessageType.OPTION)
                .options(Arrays.asList(
                        ButtonOption.builder().label("응, 하고 왔어").build(),
                        ButtonOption.builder().label("아니, 안 하고 왔어").build()
                ))
                .build();
    }

    private void processSkippedAction(ChatSession session) {
        String skippedSkillId = session.getTemporaryData("selectedSkillId");
        String userSituation = session.getTemporaryData("userSituation");
        String selectedEmotionsString = session.getTemporaryData("selectedEmotions");
        Set<EmotionType> selectedEmotions = ChatUtils.parseEmotionsFromString(selectedEmotionsString);

        BehavioralSkill skippedSkill = behavioralSkillProvider.getAllSkills().stream()
                .filter(s -> s.getChunkId().equals(skippedSkillId))
                .findFirst()
                .orElse(null);

        String comfortMessage;
        if (skippedSkill != null) {
            comfortMessage = chatAiService.generateActionSkipped(userSituation, selectedEmotions, skippedSkill);
        } else {
            log.warn("SKILL_CONFIRM(SKIP): 스킵한 스킬을 찾을 수 없음: {}", skippedSkillId);
            comfortMessage = chatAiService.generateSelfSoothingMessages(userSituation, selectedEmotions);
        }
        
        session.setTemporaryData("comfortMessage", comfortMessage);
        session.setTemporaryData("endReason", "SKIPPED");
    }
}
