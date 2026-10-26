package com.forA.chatbot.user.dto;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserProfileUpdateRequest {

  @Size(max = 20, message = "닉네임은 20자 이하여야 합니다")
  private String nickname;

  @Size(max = 2, message = "직업은 최대 2개까지 선택 가능합니다")
  private List<String> jobs;

  @Size(min = 1, max = 2, message = "정신질환은 1~2개 선택해주세요")
  private List<String> disorders;

  private Map<String, List<String>> symptoms;
}
