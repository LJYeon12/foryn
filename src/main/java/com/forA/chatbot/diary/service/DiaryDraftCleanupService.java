package com.forA.chatbot.diary.service;

import com.forA.chatbot.diary.repository.DiaryRepository;
import com.forA.chatbot.global.time.AppDateTimeProvider;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryDraftCleanupService {

  private static final ZoneId DB_ZONE_ID = ZoneId.of("Asia/Seoul");

  private final DiaryRepository diaryRepository;
  private final AppDateTimeProvider appDateTimeProvider;

  // 만료된 초안 삭제
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int cleanupExpiredDrafts(Long userId, ZoneId userZoneId) {
    // 사용자 기준 오늘 0시보다 전에 수정된 초안만 지우기 위해, 시간을 서울 시간으로 맞춘다.
    LocalDateTime cutoff = appDateTimeProvider.today(userZoneId.getId())
        .atStartOfDay(userZoneId)
        .withZoneSameInstant(DB_ZONE_ID)
        .toLocalDateTime();
    int deletedDraftCount =
        diaryRepository.deleteExpiredDraftsByUpdatedAtBefore(userId, cutoff);
    if (deletedDraftCount > 0) {
      log.info("만료 한줄일기 DRAFT 삭제 - 사용자 ID: {}, 삭제 수: {}", userId, deletedDraftCount);
    }
    return deletedDraftCount;
  }
}
