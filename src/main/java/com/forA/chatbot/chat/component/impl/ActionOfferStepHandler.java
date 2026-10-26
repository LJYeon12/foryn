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
import com.forA.chatbot.chat.dto.ButtonOption;
import com.forA.chatbot.chat.dto.ChatBotMessage;
import com.forA.chatbot.chat.dto.ChatRequest;
import com.forA.chatbot.chat.service.ChatAiService;
import com.forA.chatbot.chat.util.ChatUtils;
import com.forA.chatbot.user.domain.User;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActionOfferStepHandler implements ChatStepHandler {

  private final ChatAiService chatAiService;
  private final ObjectMapper objectMapper;
  private final BehavioralSkillProvider behavioralSkillProvider;

  @Override
  public String getStepName() {
    return ChatStep.ACTION_OFFER.name();
  }

  @Override
  public String handleRequest(ChatSession session, User user, ChatRequest request) {
    String responseValue = request.getResponseValue();

    if ("응, 뭔데?".equals(responseValue)) {
      String userSituation = session.getTemporaryData("userSituation");
      String selectedEmotionsString = session.getTemporaryData("selectedEmotions");
      String skillJson = convertSkillsToJson();

      List<String> recommendedIds = chatAiService.recommendSkillChunkId(userSituation, selectedEmotionsString, skillJson);
      
      String recommendedIdsString = String.join(",", recommendedIds);
      session.setTemporaryData("recommendedSkillIds", recommendedIdsString);

      return ChatStep.ACTION_PROPOSE.name();

    } else if ("아니 혼자 진정하고 싶어".equals(responseValue)) {
      String userSituation = session.getTemporaryData("userSituation");
      String selectedEmotionsString = session.getTemporaryData("selectedEmotions");
      Set<EmotionType> selectedEmotions = ChatUtils.parseEmotionsFromString(selectedEmotionsString);

      String comfortMessage = chatAiService.generateSelfSoothingMessages(userSituation, selectedEmotions);
      
      session.setTemporaryData("comfortMessage", comfortMessage);
      session.setTemporaryData("endReason", "COMFORT_ALONE");

      return ChatStep.CHAT_END.name();
    } else {
      throw new ChatHandler(ErrorStatus.INVALID_BUTTON_SELECTION);
    }
  }

  @Override
  public ChatBotMessage getBotMessage(ChatSession session, User user) {
    String empathySentence = session.getTemporaryData("empathySentence");
    String goalPhrase = session.getTemporaryData("goalPhrase");
    String nickname = user.getNickname() != null ? user.getNickname() : "USER";

    String content = String.format("%s 포린이 %s님을 위해 %s 도움이 될 수 있는 방법을 추천 드려도 될까요?",
        empathySentence, nickname, goalPhrase);

    return ChatBotMessage.builder()
        .content(content)
        .type(MessageType.OPTION)
        .options(Arrays.asList(
            ButtonOption.builder().label("응, 뭔데?").build(),
            ButtonOption.builder().label("아니 혼자 진정하고 싶어").build()
        ))
        .build();
  }

  private String convertSkillsToJson() {
    try {
      return objectMapper.writeValueAsString(behavioralSkillProvider.getAllSkills());
    } catch (JsonProcessingException e) {
      log.error("Json 문자열 직렬화 실패", e);
      return "[]";
    }
  }
}
