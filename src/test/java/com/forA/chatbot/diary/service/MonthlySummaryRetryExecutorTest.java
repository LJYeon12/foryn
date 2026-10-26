package com.forA.chatbot.diary.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MonthlySummaryRetryExecutorTest {

  private final MonthlySummaryRetryExecutor retryExecutor = new MonthlySummaryRetryExecutor();

  @Test
  @DisplayName("월간 요약 생성이 두 번 실패한 뒤 세 번째 시도에 성공하면 결과를 반환한다")
  void execute_retriesTwiceBeforeReturningSuccessfulResult() {
    AtomicInteger attemptCount = new AtomicInteger();

    String result = retryExecutor.execute(() -> {
      if (attemptCount.incrementAndGet() < 3) {
        throw new IllegalStateException("provider failure");
      }
      return "요약";
    });

    assertThat(result).isEqualTo("요약");
    assertThat(attemptCount).hasValue(3);
  }
}
