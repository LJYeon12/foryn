package com.forA.chatbot.chat.domain;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@Builder
@Document(collection = "chat_messages")
public class ChatMessage {
  public enum SenderType {
    USER,
    BOT,
    SYSTEM
  }

  @Id private String id;

  @Field(name = "session_id")
  private String sessionId;
  @Field(name = "sender_type")
  private SenderType senderType;

  @Field(name = "chat_step")
  private String chatStep;

  @Field(name = "message_content")
  private String messageContent;
  @Field(name = "message_type") private String messageType;
  @Field(name = "options_json") private String optionsJson;

  @Field(name = "sent_at")
  @Builder.Default
  private LocalDateTime sentAt = LocalDateTime.now();

  @Field(name = "response_code")
  private String responseCode;

  @Field("operation_id")
  private String operationId;

  @Field("write_status")
  private String writeStatus;
}
