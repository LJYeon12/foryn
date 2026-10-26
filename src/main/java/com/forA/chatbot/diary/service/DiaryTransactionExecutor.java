package com.forA.chatbot.diary.service;

import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiaryTransactionExecutor {

  // 오래된 초안 정리가 끝난 다음에만 이 안의 일기 작업을 시작한다.
  @Transactional
  public <T> T execute(Supplier<T> action) {
    return action.get();
  }
}
