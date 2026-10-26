package com.forA.chatbot.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChatRequest {
  @Schema(description = "사용자 응답 값 (텍스트 또는 콤마로 구분된 선택지)", example = "우울증, 불안장애")
  @NotBlank(message = "응답 값은 필수입니다.")
  private String responseValue;
}
