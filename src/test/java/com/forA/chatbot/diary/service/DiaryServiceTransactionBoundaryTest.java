package com.forA.chatbot.diary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.enums.Gender;
import com.forA.chatbot.enums.ProviderType;
import com.forA.chatbot.global.time.SystemAppDateTimeProvider;
import com.forA.chatbot.user.domain.User;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import({DiaryService.class, DiaryTransactionExecutor.class, SystemAppDateTimeProvider.class})
class DiaryServiceTransactionBoundaryTest {

  @Autowired
  private DiaryService diaryService;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @Autowired
  private DiaryTransactionExecutor diaryTransactionExecutor;

  @MockitoBean
  private DiaryAiService diaryAiService;

  @MockitoBean
  private DiaryDraftCleanupService diaryDraftCleanupService;

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("cleanup 호출 전에는 main transaction이 시작되지 않는다")
  void getDailyDiaries_cleanupRunsWithoutPreexistingTransaction() {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    Long userId = transactionTemplate.execute(status -> userRepository.saveAndFlush(User.builder()
        .email("cleanup-boundary@test.com")
        .providerUserId("cleanup-boundary-provider")
        .providerType(ProviderType.APPLE)
        .gender(Gender.UNKNOWN)
        .fullName("Cleanup Boundary User")
        .timeZone("Asia/Seoul")
        .build()).getId());
    AtomicBoolean cleanupSawActiveTransaction = new AtomicBoolean();
    given(diaryDraftCleanupService.cleanupExpiredDrafts(anyLong(), any(ZoneId.class)))
        .willAnswer(invocation -> {
          cleanupSawActiveTransaction.set(
              TransactionSynchronizationManager.isActualTransactionActive());
          return 0;
        });

    diaryService.getDailyDiaries(userId, LocalDate.now(ZoneId.of("Asia/Seoul")));

    assertThat(cleanupSawActiveTransaction).isFalse();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("cleanup 이후 main work는 새 transaction에서 실행된다")
  void transactionExecutor_runsMainWorkInTransaction() {
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();

    Boolean mainWorkSawActiveTransaction = diaryTransactionExecutor.execute(
        TransactionSynchronizationManager::isActualTransactionActive);

    assertThat(mainWorkSawActiveTransaction).isTrue();
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
  }
}
