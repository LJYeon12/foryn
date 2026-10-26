package com.forA.chatbot.diary.service;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class MonthlySummaryRetryExecutor {

  private static final int MAX_ATTEMPT_COUNT = 3;
  private static final long INITIAL_BACKOFF_MILLIS = 100L;

  public <T> T execute(Supplier<T> operation) {
    RuntimeException lastException = null;
    for (int attempt = 1; attempt <= MAX_ATTEMPT_COUNT; attempt++) {
      try {
        return operation.get();
      } catch (RuntimeException e) {
        lastException = e;
        if (attempt == MAX_ATTEMPT_COUNT) {
          throw e;
        }
        waitBeforeRetry(attempt);
      }
    }
    throw lastException;
  }

  private void waitBeforeRetry(int attempt) {
    try {
      // 월간 리포트는 최대 두 번만 재시도하며, 대기 시간은 지수적으로 늘린다.
      Thread.sleep(INITIAL_BACKOFF_MILLIS * (1L << (attempt - 1)));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("월간 요약 재시도 대기 중 인터럽트가 발생했습니다.", e);
    }
  }
}
