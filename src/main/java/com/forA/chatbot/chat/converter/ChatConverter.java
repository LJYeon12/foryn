package com.forA.chatbot.chat.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.ChatHandler;
import com.forA.chatbot.chat.domain.ChatMessage;
import com.forA.chatbot.chat.domain.ChatMessage.SenderType;
import com.forA.chatbot.chat.domain.enums.ChatStep;
import com.forA.chatbot.chat.domain.enums.MessageType;
import com.forA.chatbot.chat.domain.ChatSession;
import com.forA.chatbot.chat.dto.ButtonOption;
import com.forA.chatbot.chat.dto.ChatBotMessage;
import com.forA.chatbot.chat.dto.ChatMessageDto;
import com.forA.chatbot.chat.dto.ChatResponse;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatConverter {

  private final ObjectMapper objectMapper;

  public ChatBotMessage convertLatestHistoryToBotMessage(List<ChatMessageDto> history) {
    if (history == null || history.isEmpty()) {
      throw new ChatHandler(ErrorStatus.CHAT_HISTORY_EMPTY);
    }
    ChatMessageDto latestMessage = history.stream()
        .max(Comparator.comparing(ChatMessageDto::getSentAt))
        .get();
    return ChatBotMessage.builder()
        .content(latestMessage.getContent())
        .type(latestMessage.getType())
        .options(latestMessage.getOptions())
        .build();
  }

  public List<ChatMessageDto> toDtoList(List<ChatMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      return Collections.emptyList();
    }
    return messages.stream()
        .map(this::toDto)
        .collect(Collectors.toList());
  }

  private ChatMessageDto toDto(ChatMessage msg) {
    MessageType type = null;
    List<ButtonOption> options = null;
    ChatStep step = null;

    if (msg.getMessageType() != null) {
      try {
        type = MessageType.valueOf(msg.getMessageType());
      } catch (IllegalArgumentException e) {
        log.warn("DB에서 유효하지 않은 messageType 발견: {}", msg.getMessageType());
      }
    }

    if (msg.getChatStep() != null) {
      try {
        step = ChatStep.valueOf(msg.getChatStep());
      } catch (IllegalArgumentException e) {
        log.warn("DB에서 유효하지 않은 chatStep 발견: {}", msg.getChatStep());
      }
    }

    if (msg.getOptionsJson() != null && !msg.getOptionsJson().isEmpty()) {
      try {
        options = objectMapper.readValue(msg.getOptionsJson(), new TypeReference<List<ButtonOption>>() {});
      } catch (JsonProcessingException e) {
        log.error("DB에서 options JSON 역직렬화 실패: {}", msg.getOptionsJson(), e);
      }
    }
    return ChatMessageDto.builder()
        .sender(msg.getSenderType().name())
        .content(msg.getMessageContent())
        .sentAt(msg.getSentAt())
        .type(type)
        .step(step)
        .options(options)
        .build();
  }

  public ChatMessage toUserMessageEntity(String sessionId, String step, String content) {
    return ChatMessage.builder()
        .sessionId(sessionId)
        .senderType(SenderType.USER)
        .chatStep(step)
        .messageContent(content)
        .responseCode(content)
        .sentAt(LocalDateTime.now())
        .build();
  }

  public ChatMessageDto toBotChatMessageDto(ChatBotMessage botMessage, ChatSession session) {
    return ChatMessageDto.builder()
        .sender("BOT")
        .content(botMessage.getContent())
        .type(botMessage.getType())
        .step(ChatStep.valueOf(session.getCurrentStep()))
        .options(botMessage.getOptions())
        .sentAt(LocalDateTime.now())
        .build();
  }

  public ChatResponse toChatResponse(
      ChatSession session,
      List<ChatMessageDto> messages,
      ChatBotMessage botMessage,
      boolean isCompleted,
      int todayChatCount) {
    return ChatResponse.builder()
        .sessionId(session.getId())
        .currentStep(session.getCurrentStep())
        .messages(messages)
        .botMessage(botMessage)
        .isCompleted(isCompleted)
        .onboardingCompleted(session.getOnboardingCompleted())
        .todayChatCount(todayChatCount)
        .build();
  }

  public ChatResponse toChatResponse(
      ChatSession session,
      String currentStep,
      ChatBotMessage botMessage,
      boolean isCompleted,
      int todayChatCount) {
    return ChatResponse.builder()
        .sessionId(session.getId())
        .currentStep(currentStep)
        .botMessage(botMessage)
        .isCompleted(isCompleted)
        .onboardingCompleted(session.getOnboardingCompleted())
        .todayChatCount(todayChatCount)
        .build();
  }

  public ChatMessage toBotMessageEntity(String sessionId, String step, ChatBotMessage botMessage) {
    String optionJson = null;
    if (botMessage.getOptions() != null && !botMessage.getOptions().isEmpty()) {
      try {
        optionJson = objectMapper.writeValueAsString(botMessage.getOptions());
      } catch (JsonProcessingException e) {
        log.error("JSON 직렬화에 실패했습니다. 세션ID: {}", sessionId, e);
        throw new ChatHandler(ErrorStatus.JSON_PROCESSING_ERROR);
      }
    }
    return ChatMessage.builder()
        .sessionId(sessionId)
        .senderType(SenderType.BOT)
        .chatStep(step)
        .messageContent(botMessage.getContent())
        .messageType(botMessage.getType().toString())
        .optionsJson(optionJson)
        .sentAt(LocalDateTime.now())
        .build();
  }
}
