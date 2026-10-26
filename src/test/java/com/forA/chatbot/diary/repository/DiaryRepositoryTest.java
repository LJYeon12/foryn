package com.forA.chatbot.diary.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.diary.domain.Diary;
import com.forA.chatbot.diary.domain.DiaryReminder;
import com.forA.chatbot.diary.domain.DiaryReminderRescheduleJob;
import com.forA.chatbot.diary.domain.DiaryReportSummaryCache;
import com.forA.chatbot.diary.domain.enums.DiaryReminderRescheduleJobStatus;
import com.forA.chatbot.diary.domain.enums.DiaryStatus;
import com.forA.chatbot.diary.domain.enums.DiaryTagCode;
import com.forA.chatbot.enums.Gender;
import com.forA.chatbot.enums.ProviderType;
import com.forA.chatbot.user.domain.User;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class DiaryRepositoryTest {

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private DiaryRepository diaryRepository;

  @Autowired
  private DiaryReminderRepository diaryReminderRepository;

  @Autowired
  private DiaryReportSummaryCacheRepository summaryCacheRepository;

  @Autowired
  private DiaryReminderRescheduleJobRepository rescheduleJobRepository;

  @Autowired
  private EntityManager entityManager;

  @Test
  @DisplayName("diary repository saves and finds a draft diary")
  void diaryRepository_saveAndFindDraft() {
    User user = saveUser("diary@test.com");
    LocalDate diaryDate = LocalDate.of(2026, 6, 25);

    Diary diary = diaryRepository.saveAndFlush(Diary.builder()
        .user(user)
        .diaryDate(diaryDate)
        .status(DiaryStatus.DRAFT)
        .momentAnswer("순간")
        .thoughtAnswer("생각")
        .emotionAnswer("감정")
        .forynComment("포린이 코멘트")
        .tagCodes(List.of(DiaryTagCode.WORK, DiaryTagCode.PROUD))
        .build());

    assertThat(diary.getId()).isNotNull();
    assertThat(diaryRepository.findDraftByUserIdAndDiaryDate(user.getId(), diaryDate))
        .contains(diary);
    assertThat(diaryRepository.countByUserIdAndDiaryDate(user.getId(), diaryDate)).isEqualTo(1);
  }

  @Test
  @DisplayName("updatedAt cutoff 이전 DRAFT만 삭제한다")
  void deleteExpiredDraftsByUpdatedAtBefore_deletesOnlyExpiredDrafts() {
    User user = saveUser("draft-cleanup@test.com");
    User otherUser = saveUser("other-draft-cleanup@test.com");
    Diary expiredDraft = diaryRepository.saveAndFlush(Diary.builder()
        .user(user)
        .diaryDate(LocalDate.of(2026, 6, 20))
        .status(DiaryStatus.DRAFT)
        .momentAnswer("만료 시간")
        .thoughtAnswer("만료 생각")
        .emotionAnswer("만료 감정")
        .build());
    Diary freshDraft = diaryRepository.saveAndFlush(Diary.builder()
        .user(user)
        .diaryDate(LocalDate.of(2026, 6, 19))
        .status(DiaryStatus.DRAFT)
        .momentAnswer("유효 시간")
        .thoughtAnswer("유효 생각")
        .emotionAnswer("유효 감정")
        .build());
    Diary completed = diaryRepository.saveAndFlush(Diary.builder()
        .user(user)
        .diaryDate(LocalDate.of(2026, 6, 18))
        .status(DiaryStatus.COMPLETED)
        .momentAnswer("완료 시간")
        .thoughtAnswer("완료 생각")
        .emotionAnswer("완료 감정")
        .emotionScore(0)
        .completedAt(LocalDateTime.of(2026, 6, 18, 10, 0))
        .build());
    Diary otherUserExpiredDraft = diaryRepository.saveAndFlush(Diary.builder()
        .user(otherUser)
        .diaryDate(LocalDate.of(2026, 6, 17))
        .status(DiaryStatus.DRAFT)
        .momentAnswer("다른 사용자 시간")
        .thoughtAnswer("다른 사용자 생각")
        .emotionAnswer("다른 사용자 감정")
        .build());

    LocalDateTime cutoff = LocalDateTime.of(2026, 6, 26, 0, 0);
    setDiaryUpdatedAt(expiredDraft.getId(), cutoff.minusSeconds(1));
    setDiaryUpdatedAt(freshDraft.getId(), cutoff);
    setDiaryUpdatedAt(completed.getId(), cutoff.minusDays(1));
    setDiaryUpdatedAt(otherUserExpiredDraft.getId(), cutoff.minusDays(2));
    entityManager.clear();

    int deleted = diaryRepository.deleteExpiredDraftsByUpdatedAtBefore(user.getId(), cutoff);
    entityManager.clear();

    assertThat(deleted).isEqualTo(1);
    assertThat(diaryRepository.findById(expiredDraft.getId())).isEmpty();
    assertThat(diaryRepository.findById(freshDraft.getId())).isPresent();
    assertThat(diaryRepository.findById(completed.getId())).isPresent();
    assertThat(diaryRepository.findById(otherUserExpiredDraft.getId())).isPresent();
  }

  @Test
  @DisplayName("diary reminder repository saves and orders reminders")
  void diaryReminderRepository_saveAndFindByUser() {
    User user = saveUser("reminder@test.com");

    diaryReminderRepository.save(DiaryReminder.builder()
        .user(user)
        .reminderTime(LocalTime.of(22, 0))
        .enabled(true)
        .build());
    diaryReminderRepository.saveAndFlush(DiaryReminder.builder()
        .user(user)
        .reminderTime(LocalTime.of(21, 0))
        .enabled(true)
        .build());

    List<DiaryReminder> reminders =
        diaryReminderRepository.findByUserIdOrderByReminderTimeAsc(user.getId());

    assertThat(reminders).extracting(DiaryReminder::getReminderTime)
        .containsExactly(LocalTime.of(21, 0), LocalTime.of(22, 0));
  }

  @Test
  @DisplayName("재예약용 reminder 조회는 lazy user를 함께 로딩한다")
  void diaryReminderRepository_findEnabledByUserIdWithUser_fetchesUser() {
    User user = saveUser("reschedule-reminder@test.com");
    diaryReminderRepository.save(DiaryReminder.builder()
        .user(user)
        .reminderTime(LocalTime.of(22, 0))
        .enabled(true)
        .build());
    diaryReminderRepository.save(DiaryReminder.builder()
        .user(user)
        .reminderTime(LocalTime.of(21, 0))
        .enabled(false)
        .build());
    diaryReminderRepository.saveAndFlush(DiaryReminder.builder()
        .user(user)
        .reminderTime(LocalTime.of(20, 0))
        .enabled(true)
        .build());
    entityManager.clear();

    List<DiaryReminder> reminders =
        diaryReminderRepository.findEnabledByUserIdWithUserOrderByReminderTimeAsc(user.getId());

    assertThat(reminders).extracting(DiaryReminder::getReminderTime)
        .containsExactly(LocalTime.of(20, 0), LocalTime.of(22, 0));
    assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil()
        .isLoaded(reminders.get(0).getUser()))
        .isTrue();
    assertThat(reminders.get(0).getUser().getTimeZone()).isEqualTo("Asia/Seoul");
  }

  @Test
  @DisplayName("summary cache repository finds cache by user and date")
  void summaryCacheRepository_saveAndFindByUserAndDate() {
    User user = saveUser("summary@test.com");
    LocalDate summaryDate = LocalDate.of(2026, 6, 25);

    DiaryReportSummaryCache cache = summaryCacheRepository.saveAndFlush(
        DiaryReportSummaryCache.builder()
            .user(user)
            .summaryDate(summaryDate)
            .hasRecentDiaryData(true)
            .content("요약")
            .build());

    assertThat(summaryCacheRepository.findByUserIdAndSummaryDate(user.getId(), summaryDate))
        .contains(cache);
  }

  @Test
  @DisplayName("reschedule job repository saves old reminder ids")
  void rescheduleJobRepository_save() {
    User user = saveUser("job@test.com");

    DiaryReminderRescheduleJob job = rescheduleJobRepository.saveAndFlush(
        DiaryReminderRescheduleJob.builder()
            .user(user)
            .scheduledDate(LocalDate.of(2026, 6, 25))
            .oldReminderIds(List.of(1L, 2L))
            .build());

    assertThat(job.getId()).isNotNull();
    assertThat(job.getOldReminderIds()).containsExactly(1L, 2L);
  }

  @Test
  @DisplayName("reschedule job repository finds pending jobs with null next retry time")
  void rescheduleJobRepository_findRunnableJobsWithNullNextRetryAt() {
    User user = saveUser("pending-job@test.com");
    DiaryReminderRescheduleJob job = rescheduleJobRepository.saveAndFlush(
        DiaryReminderRescheduleJob.builder()
            .user(user)
            .scheduledDate(LocalDate.of(2026, 6, 25))
            .oldReminderIds(List.of(1L))
            .status(DiaryReminderRescheduleJobStatus.PENDING)
            .build());

    List<DiaryReminderRescheduleJob> runnableJobs =
        rescheduleJobRepository.findRunnableJobsByStatus(
            DiaryReminderRescheduleJobStatus.PENDING, LocalDateTime.of(2026, 6, 25, 12, 0));

    assertThat(runnableJobs).contains(job);
  }

  @Test
  @DisplayName("reschedule job repository recovers stale processing jobs")
  void rescheduleJobRepository_recoverStaleProcessingJobs() {
    User user = saveUser("stale-processing-job@test.com");
    LocalDateTime now = LocalDateTime.of(2026, 6, 25, 12, 0);
    DiaryReminderRescheduleJob job = rescheduleJobRepository.saveAndFlush(
        DiaryReminderRescheduleJob.builder()
            .user(user)
            .scheduledDate(LocalDate.of(2026, 6, 25))
            .oldReminderIds(List.of(1L))
            .status(DiaryReminderRescheduleJobStatus.PROCESSING)
            .build());

    int recovered =
        rescheduleJobRepository.recoverStaleProcessingJobs(now, LocalDateTime.now().plusMinutes(1));
    entityManager.clear();

    DiaryReminderRescheduleJob recoveredJob =
        rescheduleJobRepository.findById(job.getId()).orElseThrow();
    assertThat(recovered).isEqualTo(1);
    assertThat(recoveredJob.getStatus()).isEqualTo(DiaryReminderRescheduleJobStatus.RETRY_WAITING);
    assertThat(recoveredJob.getNextRetryAt()).isEqualTo(now);
    assertThat(recoveredJob.getLastError()).isEqualTo("stale processing job recovered");
    assertThat(recoveredJob.getUpdatedAt()).isEqualTo(now);
  }

  @Test
  @DisplayName("reschedule job repository does not recover a freshly claimed old pending job")
  void rescheduleJobRepository_doesNotRecoverFreshlyClaimedOldPendingJob() {
    User user = saveUser("freshly-claimed-job@test.com");
    DiaryReminderRescheduleJob job = rescheduleJobRepository.saveAndFlush(
        DiaryReminderRescheduleJob.builder()
            .user(user)
            .scheduledDate(LocalDate.of(2026, 6, 25))
            .oldReminderIds(List.of(1L))
            .status(DiaryReminderRescheduleJobStatus.PENDING)
            .build());
    LocalDateTime claimTime = LocalDateTime.of(2026, 6, 25, 12, 0);
    LocalDateTime oldUpdatedAt = claimTime.minusHours(1);
    entityManager.createNativeQuery(
            "UPDATE diary_reminder_reschedule_jobs SET updated_at = ? WHERE id = ?")
        .setParameter(1, oldUpdatedAt)
        .setParameter(2, job.getId())
        .executeUpdate();
    entityManager.flush();
    entityManager.clear();

    int claimed = rescheduleJobRepository.claimRunnableJob(
        job.getId(),
        List.of(DiaryReminderRescheduleJobStatus.PENDING,
            DiaryReminderRescheduleJobStatus.RETRY_WAITING),
        claimTime);
    int recovered = rescheduleJobRepository.recoverStaleProcessingJobs(
        claimTime.plusMinutes(1), claimTime.minusMinutes(10));
    entityManager.clear();

    DiaryReminderRescheduleJob claimedJob =
        rescheduleJobRepository.findById(job.getId()).orElseThrow();
    assertThat(claimed).isEqualTo(1);
    assertThat(recovered).isZero();
    assertThat(claimedJob.getStatus()).isEqualTo(DiaryReminderRescheduleJobStatus.PROCESSING);
    assertThat(claimedJob.getUpdatedAt()).isEqualTo(claimTime);
  }

  private User saveUser(String email) {
    return userRepository.saveAndFlush(User.builder()
        .email(email)
        .providerUserId(email + "-provider")
        .providerType(ProviderType.APPLE)
        .gender(Gender.UNKNOWN)
        .fullName("Test User")
        .build());
  }

  private void setDiaryUpdatedAt(Long diaryId, LocalDateTime updatedAt) {
    entityManager.createNativeQuery("UPDATE diaries SET updated_at = ? WHERE id = ?")
        .setParameter(1, updatedAt)
        .setParameter(2, diaryId)
        .executeUpdate();
    entityManager.flush();
  }
}
