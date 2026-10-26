package com.forA.chatbot.diary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.DiaryHandler;
import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.diary.domain.Diary;
import com.forA.chatbot.diary.domain.enums.DiaryStatus;
import com.forA.chatbot.diary.dto.DiaryCompleteRequest;
import com.forA.chatbot.diary.repository.DiaryRepository;
import com.forA.chatbot.enums.Gender;
import com.forA.chatbot.enums.ProviderType;
import com.forA.chatbot.global.time.SystemAppDateTimeProvider;
import com.forA.chatbot.user.domain.User;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import({
    DiaryService.class,
    DiaryDraftCleanupService.class,
    DiaryTransactionExecutor.class,
    SystemAppDateTimeProvider.class
})
class DiaryServiceTransactionTest {

  private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

  @Autowired
  private DiaryService diaryService;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private DiaryRepository diaryRepository;

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @MockitoBean
  private DiaryAiService diaryAiService;

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("완료 진입 후 실패해도 만료 DRAFT cleanup은 커밋된다")
  void completeDiary_missingAfterCleanup_keepsCleanupCommitted() {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    Long[] ids = transactionTemplate.execute(status -> {
      User user = userRepository.saveAndFlush(User.builder()
          .email("cleanup-rollback@test.com")
          .providerUserId("cleanup-rollback-provider")
          .providerType(ProviderType.APPLE)
          .gender(Gender.UNKNOWN)
          .fullName("Cleanup Rollback User")
          .timeZone("Asia/Seoul")
          .build());
      Diary expiredDraft = diaryRepository.saveAndFlush(Diary.builder()
          .user(user)
          .diaryDate(LocalDate.now(SEOUL_ZONE_ID).minusDays(1))
          .status(DiaryStatus.DRAFT)
          .momentAnswer("만료 순간")
          .thoughtAnswer("만료 생각")
          .emotionAnswer("만료 감정")
          .build());
      entityManager.createNativeQuery("UPDATE diaries SET updated_at = ? WHERE id = ?")
          .setParameter(1, LocalDateTime.now(SEOUL_ZONE_ID).minusDays(1))
          .setParameter(2, expiredDraft.getId())
          .executeUpdate();
      entityManager.flush();
      return new Long[] {user.getId(), expiredDraft.getId()};
    });

    assertThatThrownBy(() -> diaryService.completeDiary(
        ids[0], ids[1], new DiaryCompleteRequest(0, List.of())))
        .isInstanceOf(DiaryHandler.class)
        .hasMessage(ErrorStatus.DIARY_NOT_FOUND.getMessage());

    Boolean expiredDraftDeleted = transactionTemplate.execute(
        status -> diaryRepository.findById(ids[1]).isEmpty());
    assertThat(expiredDraftDeleted).isTrue();
  }
}
