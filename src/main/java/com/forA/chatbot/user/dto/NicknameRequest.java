package com.forA.chatbot.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class NicknameRequest {

  @NotBlank(message = "닉네임은 필수입니다")
  @Size(max = 20, message = "닉네임은 20자 이하여야 합니다")
  private String nickname;
}
