package com.forA.chatbot.chat.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.ChatHandler;
import com.forA.chatbot.chat.domain.ChatMessage;
import com.forA.chatbot.chat.domain.enums.ChatStep;
import com.forA.chatbot.chat.domain.enums.MessageType;
import com.forA.chatbot.chat.dto.ButtonOption;
import com.forA.chatbot.chat.dto.ChatBotMessage;
import com.forA.chatbot.chat.dto.ChatMessageDto;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatConverterTest {

  private final ChatConverter chatConverter = new ChatConverter(new ObjectMapper());

  @Test
  @DisplayName("convertLatestHistoryToBotMessage returns the latest message by sentAt")
  void convertLatestHistoryToBotMessage_success() {
    ChatBotMessage botMessage =
        chatConverter.convertLatestHistoryToBotMessage(
            List.of(
                ChatMessageDto.builder()
                    .content("older")
                    .type(MessageType.TEXT)
                    .sentAt(LocalDateTime.of(2026, 3, 16, 8, 0))
                    .build(),
                ChatMessageDto.builder()
                    .content("latest")
                    .type(MessageType.OPTION)
                    .options(List.of(ButtonOption.builder().label("next").build()))
                    .sentAt(LocalDateTime.of(2026, 3, 16, 9, 0))
                    .build()));

    assertThat(botMessage.getContent()).isEqualTo("latest");
    assertThat(botMessage.getType()).isEqualTo(MessageType.OPTION);
    assertThat(botMessage.getOptions()).hasSize(1);
    assertThat(botMessage.getOptions().get(0).getLabel()).isEqualTo("next");
  }

  @Test
  @DisplayName("convertLatestHistoryToBotMessage throws when history is empty")
  void convertLatestHistoryToBotMessage_emptyHistory() {
    assertThatThrownBy(() -> chatConverter.convertLatestHistoryToBotMessage(List.of()))
        .isInstanceOf(ChatHandler.class)
        .extracting("code")
        .isEqualTo(ErrorStatus.CHAT_HISTORY_EMPTY);
  }

  @Test
  @DisplayName("toDtoList keeps dto mapping even when options json parsing fails")
  void toDtoList_success() {
    ChatMessage chatMessage =
        ChatMessage.builder()
            .sessionId("session-1")
            .senderType(ChatMessage.SenderType.BOT)
            .chatStep(ChatStep.EMOTION_SELECT.name())
            .messageContent("Choose one")
            .messageType(MessageType.OPTION.name())
            .optionsJson(
                "[{\"label\":\"happy\",\"emoji\":\"smile\",\"multiSelect\":false,\"group\":\"emotion\"}]")
            .sentAt(LocalDateTime.of(2026, 3, 16, 9, 30))
            .build();

    List<ChatMessageDto> dtoList = chatConverter.toDtoList(List.of(chatMessage));

    assertThat(dtoList).hasSize(1);
    ChatMessageDto dto = dtoList.get(0);
    assertThat(dto.getSender()).isEqualTo(ChatMessage.SenderType.BOT.name());
    assertThat(dto.getContent()).isEqualTo("Choose one");
    assertThat(dto.getType()).isEqualTo(MessageType.OPTION);
    assertThat(dto.getStep()).isEqualTo(ChatStep.EMOTION_SELECT);
    assertThat(dto.getOptions()).isNull();


  }

  @Test
  @DisplayName("toDtoList keeps invalid enum values as null")
  void toDtoList_invalidEnums() {
    ChatMessage chatMessage =
        ChatMessage.builder()
            .sessionId("session-1")
            .senderType(ChatMessage.SenderType.SYSTEM)
            .chatStep("UNKNOWN_STEP")
            .messageContent("system")
            .messageType("UNKNOWN_TYPE")
            .sentAt(LocalDateTime.of(2026, 3, 16, 10, 0))
            .build();

    ChatMessageDto dto = chatConverter.toDtoList(List.of(chatMessage)).get(0);

    assertThat(dto.getType()).isNull();
    assertThat(dto.getStep()).isNull();
  }

  @Test
  @DisplayName("toUserMessageEntity creates user message with responseCode")
  void toUserMessageEntity_success() {
    ChatMessage entity =
        chatConverter.toUserMessageEntity("session-1", ChatStep.ACTION_FEEDBACK.name(), "yes");

    assertThat(entity.getSessionId()).isEqualTo("session-1");
    assertThat(entity.getSenderType()).isEqualTo(ChatMessage.SenderType.USER);
    assertThat(entity.getChatStep()).isEqualTo(ChatStep.ACTION_FEEDBACK.name());
    assertThat(entity.getMessageContent()).isEqualTo("yes");
    assertThat(entity.getResponseCode()).isEqualTo("yes");
    assertThat(entity.getSentAt()).isNotNull();
  }

  @Test
  @DisplayName("toBotMessageEntity serializes options and message type")
  void toBotMessageEntity_success() {
    ChatMessage entity =
        chatConverter.toBotMessageEntity(
            "session-1",
            ChatStep.EMOTION_SELECT.name(),
            ChatBotMessage.builder()
                .content("Choose one")
                .type(MessageType.OPTION)
                .options(
                    List.of(
                        ButtonOption.builder()
                            .label("happy")
                            .emoji("smile")
                            .multiSelect(false)
                            .group("emotion")
                            .build()))
                .build());

    assertThat(entity.getSessionId()).isEqualTo("session-1");
    assertThat(entity.getSenderType()).isEqualTo(ChatMessage.SenderType.BOT);
    assertThat(entity.getChatStep()).isEqualTo(ChatStep.EMOTION_SELECT.name());
    assertThat(entity.getMessageContent()).isEqualTo("Choose one");
    assertThat(entity.getMessageType()).isEqualTo(MessageType.OPTION.name());
    assertThat(entity.getOptionsJson()).contains("\"label\":\"happy\"");
    assertThat(entity.getSentAt()).isNotNull();
  }
}

