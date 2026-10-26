package com.forA.chatbot;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatbotApplicationTest {

  @Test
  @DisplayName("애플리케이션 시작 시 JVM 기본 timezone 변경 hook을 두지 않는다")
  void applicationDoesNotHaveGlobalTimeZoneOverrideHook() {
    boolean hasPostConstructHook = Arrays.stream(ChatbotApplication.class.getDeclaredMethods())
        .anyMatch(method -> method.isAnnotationPresent(PostConstruct.class));

    assertThat(hasPostConstructHook).isFalse();
  }
}
