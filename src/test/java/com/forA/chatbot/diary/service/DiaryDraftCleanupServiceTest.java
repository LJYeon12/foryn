package com.forA.chatbot.diary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.forA.chatbot.diary.repository.DiaryRepository;
import com.forA.chatbot.global.time.SystemAppDateTimeProvider;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiaryDraftCleanupServiceTest {

  private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

  @Mock
  private DiaryRepository diaryRepository;

  @Spy
  private SystemAppDateTimeProvider appDateTimeProvider = new SystemAppDateTimeProvider();

  @InjectMocks
  private DiaryDraftCleanupService diaryDraftCleanupService;

  @Test
  @DisplayName("cleanup cutoff은 사용자 현지 자정을 DB timezone으로 변환한다")
  void cleanupExpiredDrafts_usesUserMidnightInDbZone() {
    ZoneId userZone = ZoneId.of("America/Los_Angeles");
    LocalDate today = LocalDate.of(2026, 5, 29);
    doReturn(today).when(appDateTimeProvider).today(userZone.getId());

    diaryDraftCleanupService.cleanupExpiredDrafts(1L, userZone);

    ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(diaryRepository)
        .deleteExpiredDraftsByUpdatedAtBefore(eq(1L), cutoffCaptor.capture());
    LocalDateTime expected = today.atStartOfDay(userZone)
        .withZoneSameInstant(SEOUL_ZONE_ID)
        .toLocalDateTime();
    assertThat(cutoffCaptor.getValue()).isEqualTo(expected);
  }
}
