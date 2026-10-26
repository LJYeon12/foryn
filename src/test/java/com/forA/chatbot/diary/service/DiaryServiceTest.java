package com.forA.chatbot.diary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.DiaryHandler;
import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.diary.domain.Diary;
import com.forA.chatbot.diary.domain.enums.DiaryStatus;
import com.forA.chatbot.diary.domain.enums.DiaryTagCode;
import com.forA.chatbot.diary.dto.DiaryCompleteRequest;
import com.forA.chatbot.diary.dto.DiaryCompleteResponse;
import com.forA.chatbot.diary.dto.DiaryCreateRequest;
import com.forA.chatbot.diary.dto.DiaryCreateResponse;
import com.forA.chatbot.diary.dto.DiaryDailyResponse;
import com.forA.chatbot.diary.repository.DiaryRepository;
import com.forA.chatbot.global.time.SystemAppDateTimeProvider;
import com.forA.chatbot.user.domain.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DiaryServiceTest {

  private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

  @Mock
  private UserRepository userRepository;

  @Mock
  private DiaryRepository diaryRepository;

  @Mock
  private DiaryAiService diaryAiService;

  @Mock
  private DiaryDraftCleanupService diaryDraftCleanupService;

  @Spy
  private SystemAppDateTimeProvider appDateTimeProvider = new SystemAppDateTimeProvider();

  @Spy
  private DiaryTransactionExecutor diaryTransactionExecutor = new DiaryTransactionExecutor();

  @InjectMocks
  private DiaryService diaryService;

  @Test
  @DisplayName("작성 날짜 검증은 고정 가능한 공통 날짜 provider를 사용한다")
  void createDiaryDraft_usesAppDateTimeProviderForFutureValidation() {
    LocalDate providerToday = LocalDate.of(2026, 5, 29);
    LocalDate futureDate = providerToday.plusDays(1);
    doReturn(providerToday).when(appDateTimeProvider).today("Asia/Seoul");
    given(userRepository.findById(1L)).willReturn(Optional.of(user("Asia/Seoul")));

    assertThatThrownBy(() -> diaryService.createDiaryDraft(1L, futureDate, validRequest()))
        .isInstanceOf(DiaryHandler.class)
        .hasMessage(ErrorStatus.DIARY_DATE_INVALID.getMessage());
  }

  @Test
  @DisplayName("한줄일기 작성 시작 요청은 답변을 컬럼에 매핑하고 DRAFT를 생성한다")
  void createDiaryDraft_success() {
    User user = user("Asia/Seoul");
    DiaryCreateRequest request = validRequest();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(diaryRepository.findDraftByUserIdAndDiaryDate(eq(1L), any(LocalDate.class)))
        .willReturn(Optional.empty());
    given(diaryRepository.countByUserIdAndDiaryDate(eq(1L), any(LocalDate.class)))
        .willReturn(0L, 0L, 1L, 1L);
    given(diaryAiService.generateForynComment("순간", "생각", "감정")).willReturn("AI 코멘트");
    given(diaryRepository.saveAndFlush(any(Diary.class))).willAnswer(invocation -> {
      Diary diary = invocation.getArgument(0);
      ReflectionTestUtils.setField(diary, "id", 10L);
      return diary;
    });

    DiaryCreateResponse response =
        diaryService.createDiaryDraft(1L, LocalDate.now(SEOUL_ZONE_ID), request);

    assertThat(response.diaryId()).isEqualTo(10L);
    assertThat(response.status()).isEqualTo(DiaryStatus.DRAFT.name());
    assertThat(response.forynComment()).isEqualTo("AI 코멘트");
    assertThat(response.todayCreatedDiaryCount()).isEqualTo(1);
    assertThat(response.remainingDiaryCount()).isEqualTo(2);

    ArgumentCaptor<Diary> diaryCaptor = ArgumentCaptor.forClass(Diary.class);
    verify(diaryRepository).saveAndFlush(diaryCaptor.capture());
    assertThat(diaryCaptor.getValue().getMomentAnswer()).isEqualTo("순간");
    assertThat(diaryCaptor.getValue().getThoughtAnswer()).isEqualTo("생각");
    assertThat(diaryCaptor.getValue().getEmotionAnswer()).isEqualTo("감정");
    InOrder inOrder = inOrder(diaryDraftCleanupService, diaryRepository);
    inOrder.verify(diaryDraftCleanupService)
        .cleanupExpiredDrafts(1L, SEOUL_ZONE_ID);
    inOrder.verify(diaryRepository)
        .findDraftByUserIdAndDiaryDate(eq(1L), any(LocalDate.class));
  }

  @Test
  @DisplayName("답변 개수가 3개가 아니면 DIARY4002를 반환한다")
  void createDiaryDraft_invalidAnswerSize() {
    DiaryCreateRequest request = new DiaryCreateRequest(List.of(
        new DiaryCreateRequest.AnswerRequest(1, "순간")));

    assertThatThrownBy(() ->
        diaryService.createDiaryDraft(1L, LocalDate.now(SEOUL_ZONE_ID), request))
        .isInstanceOf(DiaryHandler.class)
        .hasMessage(ErrorStatus.DIARY_ANSWERS_SIZE_INVALID.getMessage());

    verify(diaryRepository, never()).saveAndFlush(any(Diary.class));
  }

  @Test
  @DisplayName("questionId가 중복되면 DIARY4003을 반환한다")
  void createDiaryDraft_duplicateQuestionId() {
    DiaryCreateRequest request = new DiaryCreateRequest(List.of(
        new DiaryCreateRequest.AnswerRequest(1, "순간"),
        new DiaryCreateRequest.AnswerRequest(1, "생각"),
        new DiaryCreateRequest.AnswerRequest(3, "감정")));

    assertThatThrownBy(() ->
        diaryService.createDiaryDraft(1L, LocalDate.now(SEOUL_ZONE_ID), request))
        .isInstanceOf(DiaryHandler.class)
        .hasMessage(ErrorStatus.DIARY_QUESTION_SET_INVALID.getMessage());
  }

  @Test
  @DisplayName("답변이 blank이면 DIARY4004를 반환한다")
  void createDiaryDraft_blankContent() {
    DiaryCreateRequest request = new DiaryCreateRequest(List.of(
        new DiaryCreateRequest.AnswerRequest(1, "순간"),
        new DiaryCreateRequest.AnswerRequest(2, " "),
        new DiaryCreateRequest.AnswerRequest(3, "감정")));

    assertThatThrownBy(() ->
        diaryService.createDiaryDraft(1L, LocalDate.now(SEOUL_ZONE_ID), request))
        .isInstanceOf(DiaryHandler.class)
        .hasMessage(ErrorStatus.DIARY_ANSWER_CONTENT_INVALID.getMessage());
  }

  @Test
  @DisplayName("당일 DRAFT가 있으면 새 DRAFT를 만들지 않는다")
  void createDiaryDraft_existingDraft() {
    User user = user("Asia/Seoul");
    LocalDate today = LocalDate.now(SEOUL_ZONE_ID);
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(diaryRepository.findDraftByUserIdAndDiaryDate(1L, today))
        .willReturn(Optional.of(Diary.builder().build()));

    assertThatThrownBy(() -> diaryService.createDiaryDraft(
        1L, today, validRequest()))
        .isInstanceOf(DiaryHandler.class)
        .hasMessage(ErrorStatus.DIARY_DRAFT_ALREADY_EXISTS.getMessage());

    verify(diaryAiService, never()).generateForynComment(any(), any(), any());
    verify(diaryRepository, never()).saveAndFlush(any(Diary.class));
  }

  @Test
  @DisplayName("당일 생성 기록이 3개면 새 DRAFT를 만들지 않는다")
  void createDiaryDraft_dailyLimitExceeded() {
    User user = user("Asia/Seoul");
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(diaryRepository.findDraftByUserIdAndDiaryDate(eq(1L), any(LocalDate.class)))
        .willReturn(Optional.empty());
    given(diaryRepository.countByUserIdAndDiaryDate(eq(1L), any(LocalDate.class))).willReturn(3L);

    assertThatThrownBy(() -> diaryService.createDiaryDraft(
        1L, LocalDate.now(SEOUL_ZONE_ID), validRequest()))
        .isInstanceOf(DiaryHandler.class)
        .hasMessage(ErrorStatus.DIARY_DAILY_LIMIT_EXCEEDED.getMessage());
  }

  @Test
  @DisplayName("AI 호출 실패 시 fallback 코멘트로 DRAFT를 생성한다")
  void createDiaryDraft_aiFallback() {
    User user = user("Asia/Seoul");
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(diaryRepository.findDraftByUserIdAndDiaryDate(eq(1L), any(LocalDate.class)))
        .willReturn(Optional.empty());
    given(diaryRepository.countByUserIdAndDiaryDate(eq(1L), any(LocalDate.class))).willReturn(0L);
    given(diaryAiService.generateForynComment(any(), any(), any()))
        .willThrow(new RuntimeException("AI 실패"));
    given(diaryRepository.saveAndFlush(any(Diary.class))).willAnswer(invocation -> {
      Diary diary = invocation.getArgument(0);
      ReflectionTestUtils.setField(diary, "id", 10L);
      return diary;
    });

    DiaryCreateResponse response = diaryService.createDiaryDraft(
        1L, LocalDate.now(SEOUL_ZONE_ID), validRequest());

    assertThat(response.forynComment()).isEqualTo(DiaryAiService.FALLBACK_FORYN_COMMENT);
  }

  @Test
  @DisplayName("DB unique 충돌은 DIARY4092로 변환한다")
  void createDiaryDraft_duplicateByDatabaseConstraint() {
    User user = user("Asia/Seoul");
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(diaryRepository.findDraftByUserIdAndDiaryDate(eq(1L), any(LocalDate.class)))
        .willReturn(Optional.empty());
    given(diaryRepository.countByUserIdAndDiaryDate(eq(1L), any(LocalDate.class))).willReturn(0L);
    given(diaryAiService.generateForynComment(any(), any(), any())).willReturn("AI 코멘트");
    given(diaryRepository.saveAndFlush(any(Diary.class)))
        .willThrow(new DataIntegrityViolationException("DRAFT 중복"));

    assertThatThrownBy(() -> diaryService.createDiaryDraft(
        1L, LocalDate.now(SEOUL_ZONE_ID), validRequest()))
        .isInstanceOf(DiaryHandler.class)
        .hasMessage(ErrorStatus.DIARY_DRAFT_ALREADY_EXISTS.getMessage());
  }

  @Test
  @DisplayName("과거 선택 날짜로도 DRAFT를 생성한다")
  void createDiaryDraft_pastDateCreatesDraft() {
    User user = user("Asia/Seoul");
    LocalDate today = LocalDate.now(SEOUL_ZONE_ID);
    LocalDate selectedDate = today.minusDays(3);
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(diaryRepository.findDraftByUserIdAndDiaryDate(1L, selectedDate))
        .willReturn(Optional.empty());
    given(diaryRepository.countByUserIdAndDiaryDate(1L, selectedDate))
        .willReturn(0L, 0L, 1L);
    given(diaryRepository.countByUserIdAndDiaryDate(1L, today)).willReturn(0L);
    given(diaryAiService.generateForynComment("순간", "생각", "감정"))
        .willReturn("AI 코멘트");
    given(diaryRepository.saveAndFlush(any(Diary.class))).willAnswer(invocation -> {
      Diary diary = invocation.getArgument(0);
      ReflectionTestUtils.setField(diary, "id", 10L);
      return diary;
    });

    DiaryCreateResponse response =
        diaryService.createDiaryDraft(1L, selectedDate, validRequest());

    ArgumentCaptor<Diary> captor = ArgumentCaptor.forClass(Diary.class);
    verify(diaryRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getDiaryDate()).isEqualTo(selectedDate);
    assertThat(response.todayCreatedDiaryCount()).isZero();
    assertThat(response.remainingDiaryCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("과거 날짜 기존 DRAFT는 같은 ID로 덮어쓴다")
  void createDiaryDraft_pastDateOverwritesExistingDraft() {
    User user = user("Asia/Seoul");
    LocalDate today = LocalDate.now(SEOUL_ZONE_ID);
    LocalDate selectedDate = today.minusDays(2);
    Diary existing = draftDiary(user, selectedDate);
    ReflectionTestUtils.setField(existing, "id", 77L);

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(diaryRepository.findDraftByUserIdAndDiaryDate(1L, selectedDate))
        .willReturn(Optional.of(existing));
    given(diaryRepository.findDraftByUserIdAndDiaryDateForUpdate(1L, selectedDate))
        .willReturn(Optional.of(existing));
    given(diaryRepository.countByUserIdAndDiaryDate(1L, selectedDate))
        .willReturn(1L);
    given(diaryRepository.countByUserIdAndDiaryDate(1L, today)).willReturn(0L);
    given(diaryAiService.generateForynComment("순간", "생각", "감정"))
        .willReturn("새 AI 코멘트");

    DiaryCreateResponse response =
        diaryService.createDiaryDraft(1L, selectedDate, validRequest());

    assertThat(response.diaryId()).isEqualTo(77L);
    assertThat(response.forynComment()).isEqualTo("새 AI 코멘트");
    assertThat(response.todayCreatedDiaryCount()).isZero();
    assertThat(response.remainingDiaryCount()).isEqualTo(2);
    assertThat(existing.getMomentAnswer()).isEqualTo("순간");
    assertThat(existing.getForynComment()).isEqualTo("새 AI 코멘트");
    verify(diaryRepository, never()).saveAndFlush(any(Diary.class));
  }

  @Test
  @DisplayName("미래 선택 날짜는 DIARY4010을 반환한다")
  void createDiaryDraft_futureDateRejected() {
    User user = user("Asia/Seoul");
    LocalDate futureDate = LocalDate.now(SEOUL_ZONE_ID).plusDays(1);
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    assertThatThrownBy(() ->
        diaryService.createDiaryDraft(1L, futureDate, validRequest()))
        .isInstanceOf(DiaryHandler.class)
        .hasMessage(ErrorStatus.DIARY_DATE_INVALID.getMessage());

    verify(diaryRepository, never()).saveAndFlush(any(Diary.class));
  }

  @Test
  @DisplayName("DRAFT 한줄일기 완료 요청은 감정 점수와 태그를 저장하고 COMPLETED로 전환한다")
  void completeDiary_success() {
    User user = user("Asia/Seoul");
    Diary diary = draftDiary(user, LocalDate.now(SEOUL_ZONE_ID));
    ReflectionTestUtils.setField(diary, "id", 10L);
    DiaryCompleteRequest request = new DiaryCompleteRequest(
        2,
        List.of("WORK", "PROUD", "ALONE"));

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(diaryRepository.findByIdAndUserIdForUpdate(10L, 1L)).willReturn(Optional.of(diary));
    given(diaryRepository.countByUserIdAndDiaryDateAndStatus(eq(1L), any(LocalDate.class),
        eq(DiaryStatus.COMPLETED))).willReturn(1L);
    given(diaryRepository.countByUserIdAndDiaryDate(eq(1L), any(LocalDate.class))).willReturn(1L);

    DiaryCompleteResponse response = diaryService.completeDiary(1L, 10L, request);

    assertThat(response.diaryId()).isEqualTo(10L);
    assertThat(response.status()).isEqualTo(DiaryStatus.COMPLETED.name());
    assertThat(response.todayCompletedDiaryCount()).isEqualTo(1);
    assertThat(response.remainingDiaryCount()).isEqualTo(2);
    assertThat(response.completionMessage()).isEqualTo("오늘 첫 기록 완료! 👏");
    assertThat(diary.getStatus()).isEqualTo(DiaryStatus.COMPLETED);
    assertThat(diary.getEmotionScore()).isEqualTo(2);
    assertThat(diary.getTagCodes())
        .containsExactly(DiaryTagCode.WORK, DiaryTagCode.PROUD, DiaryTagCode.ALONE);
    assertThat(diary.getCompletedAt()).isNotNull();
    InOrder inOrder = inOrder(diaryDraftCleanupService, diaryRepository);
    inOrder.verify(diaryDraftCleanupService)
        .cleanupExpiredDrafts(1L, SEOUL_ZONE_ID);
    inOrder.verify(diaryRepository).findByIdAndUserIdForUpdate(10L, 1L);
  }

  @Test
  @DisplayName("태그 0개로도 한줄일기 완료가 가능하다")
  void completeDiary_allowsEmptyTags() {
    User user = user("Asia/Seoul");
    Diary diary = draftDiary(user, LocalDate.now(SEOUL_ZONE_ID));
    ReflectionTestUtils.setField(diary, "id", 10L);

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(diaryRepository.findByIdAndUserIdForUpdate(10L, 1L)).willReturn(Optional.of(diary));
    given(diaryRepository.countByUserIdAndDiaryDateAndStatus(eq(1L), any(LocalDate.class),
        eq(DiaryStatus.COMPLETED))).willReturn(3L);
    given(diaryRepository.countByUserIdAndDiaryDate(eq(1L), any(LocalDate.class))).willReturn(3L);

    DiaryCompleteResponse response = diaryService.completeDiary(
        1L, 10L, new DiaryCompleteRequest(0, List.of()));

    assertThat(response.todayCompletedDiaryCount()).isEqualTo(3);
    assertThat(response.remainingDiaryCount()).isZero();
    assertThat(response.completionMessage()).isEqualTo("오늘의 3가지 기록 대성공! 🎉");
    assertThat(diary.getTagCodes()).isEmpty();
  }

  @Test
  @DisplayName("다른 유저의 한줄일기는 DIARY4041을 반환한다")
  void completeDiary_notFoundForOtherUser() {
    given(userRepository.findById(1L)).willReturn(Optional.of(user("Asia/Seoul")));
    given(diaryRepository.findByIdAndUserIdForUpdate(10L, 1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> diaryService.completeDiary(
        1L, 10L, new DiaryCompleteRequest(0, List.of())))
        .isInstanceOf(DiaryHandler.class)
        .hasMessage(ErrorStatus.DIARY_NOT_FOUND.getMessage());
  }

  @Test
  @DisplayName("이미 완료된 한줄일기는 DIARY4091을 반환한다")
  void completeDiary_alreadyCompleted() {
    User user = user("Asia/Seoul");
    Diary diary = Diary.builder()
        .user(user)
        .diaryDate(LocalDate.now(SEOUL_ZONE_ID))
        .status(DiaryStatus.COMPLETED)
        .momentAnswer("순간")
        .thoughtAnswer("생각")
        .emotionAnswer("감정")
        .forynComment("코멘트")
        .emotionScore(1)
        .tagCodes(List.of(DiaryTagCode.WORK))
        .completedAt(LocalDateTime.now())
        .build();

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(diaryRepository.findByIdAndUserIdForUpdate(10L, 1L)).willReturn(Optional.of(diary));

    assertThatThrownBy(() -> diaryService.completeDiary(
        1L, 10L, new DiaryCompleteRequest(1, List.of("WORK"))))
        .isInstanceOf(DiaryHandler.class)
        .hasMessage(ErrorStatus.DIARY_ALREADY_COMPLETED.getMessage());
  }

  @Test
  @DisplayName("과거 날짜 DRAFT를 완료하면 일반 완료 문구를 반환한다")
  void completeDiary_pastDateReturnsGenericMessage() {
    User user = user("Asia/Seoul");
    LocalDate today = LocalDate.now(SEOUL_ZONE_ID);
    LocalDate selectedDate = today.minusDays(2);
    Diary diary = draftDiary(user, selectedDate);
    ReflectionTestUtils.setField(diary, "id", 10L);
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(diaryRepository.findByIdAndUserIdForUpdate(10L, 1L)).willReturn(Optional.of(diary));
    given(diaryRepository.countByUserIdAndDiaryDateAndStatus(
        1L, today, DiaryStatus.COMPLETED)).willReturn(0L);
    given(diaryRepository.countByUserIdAndDiaryDate(1L, selectedDate)).willReturn(1L);

    DiaryCompleteResponse response = diaryService.completeDiary(
        1L, 10L, new DiaryCompleteRequest(1, List.of("WORK")));

    assertThat(diary.getStatus()).isEqualTo(DiaryStatus.COMPLETED);
    assertThat(response.todayCompletedDiaryCount()).isZero();
    assertThat(response.remainingDiaryCount()).isEqualTo(2);
    assertThat(response.completionMessage())
        .isEqualTo("한줄일기 작성을 완료했습니다.");
  }

  @Test
  @DisplayName("미래 날짜 DRAFT 완료는 DIARY4010을 반환한다")
  void completeDiary_futureDateRejected() {
    User user = user("Asia/Seoul");
    Diary diary = draftDiary(user, LocalDate.now(SEOUL_ZONE_ID).plusDays(1));
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(diaryRepository.findByIdAndUserIdForUpdate(10L, 1L)).willReturn(Optional.of(diary));

    assertThatThrownBy(() -> diaryService.completeDiary(
        1L, 10L, new DiaryCompleteRequest(1, List.of("WORK"))))
        .isInstanceOf(DiaryHandler.class)
        .hasMessage(ErrorStatus.DIARY_DATE_INVALID.getMessage());
  }

  @Test
  @DisplayName("감정 점수가 -2~2 범위 밖이면 DIARY4005를 반환한다")
  void completeDiary_invalidEmotionScore() {
    assertThatThrownBy(() -> diaryService.completeDiary(
        1L, 10L, new DiaryCompleteRequest(3, List.of("WORK"))))
        .isInstanceOf(DiaryHandler.class)
        .hasMessage(ErrorStatus.DIARY_EMOTION_SCORE_INVALID.getMessage());
  }

  @Test
  @DisplayName("tagCodes가 null이면 DIARY4009를 반환한다")
  void completeDiary_nullTagCodes() {
    assertThatThrownBy(() -> diaryService.completeDiary(
        1L, 10L, new DiaryCompleteRequest(1, null)))
        .isInstanceOf(DiaryHandler.class)
        .hasMessage(ErrorStatus.DIARY_TAG_CODES_NULL.getMessage());
  }

  @Test
  @DisplayName("태그가 6개를 초과하면 DIARY4007을 반환한다")
  void completeDiary_tagLimitExceeded() {
    assertThatThrownBy(() -> diaryService.completeDiary(1L, 10L, new DiaryCompleteRequest(
            1,
            List.of("WORK", "PROUD", "FATIGUE", "STRESS", "SNS", "GAME", "ANGER"))))
        .isInstanceOf(DiaryHandler.class)
        .hasMessage(ErrorStatus.DIARY_TAG_LIMIT_EXCEEDED.getMessage());
  }

  @Test
  @DisplayName("태그가 중복되면 DIARY4008을 반환한다")
  void completeDiary_duplicateTagCodes() {
    assertThatThrownBy(() -> diaryService.completeDiary(
        1L, 10L, new DiaryCompleteRequest(1, List.of("WORK", "WORK"))))
        .isInstanceOf(DiaryHandler.class)
        .hasMessage(ErrorStatus.DIARY_TAG_DUPLICATED.getMessage());
  }

  @Test
  @DisplayName("알 수 없는 태그 코드는 DIARY4006을 반환한다")
  void completeDiary_unknownTagCode() {
    assertThatThrownBy(() -> diaryService.completeDiary(
        1L, 10L, new DiaryCompleteRequest(1, List.of("UNKNOWN"))))
        .isInstanceOf(DiaryHandler.class)
        .hasMessage(ErrorStatus.DIARY_TAG_CODE_INVALID.getMessage());
  }

  @Test
  @DisplayName("일별 조회는 완료된 한줄일기를 완료 시간순으로 반환한다")
  void getDailyDiaries_completedTimeline() {
    User user = user("Asia/Seoul");
    LocalDate today = LocalDate.now(SEOUL_ZONE_ID);
    LocalDate diaryDate = LocalDate.of(2026, 6, 25);
    Diary second = completedDiary(user, diaryDate, 20L, 2,
        LocalDateTime.of(2026, 6, 25, 15, 0), List.of(DiaryTagCode.STRESS));
    Diary first = completedDiary(user, diaryDate, 10L, -1,
        LocalDateTime.of(2026, 6, 25, 9, 0), List.of(DiaryTagCode.WORK, DiaryTagCode.PROUD));

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(diaryRepository.findCompletedByUserIdAndDiaryDateOrderByCompletedAtAsc(1L, diaryDate))
        .willReturn(List.of(first, second));
    given(diaryRepository.findDraftByUserIdAndDiaryDate(1L, diaryDate))
        .willReturn(Optional.empty());
    given(diaryRepository.countByUserIdAndDiaryDate(1L, diaryDate)).willReturn(2L);
    given(diaryRepository.countByUserIdAndDiaryDate(1L, today)).willReturn(0L);

    DiaryDailyResponse response = diaryService.getDailyDiaries(1L, diaryDate);

    assertThat(response.date()).isEqualTo("2026-06-25");
    assertThat(response.hasWritten()).isTrue();
    assertThat(response.diaryCount()).isEqualTo(2);
    assertThat(response.canWriteMore()).isTrue();
    assertThat(response.todayDiaryCount()).isZero();
    assertThat(response.remainingDiaryCount()).isEqualTo(1);
    assertThat(response.draftDiary()).isNull();
    assertThat(response.diaries()).extracting(DiaryDailyResponse.DiaryItem::diaryId)
        .containsExactly(10L, 20L);
    assertThat(response.diaries()).extracting(DiaryDailyResponse.DiaryItem::recordOrder)
        .containsExactly(1, 2);
    assertThat(response.diaries().get(0).answers())
        .extracting(DiaryDailyResponse.AnswerItem::questionId)
        .containsExactly(1, 2, 3);
    assertThat(response.diaries().get(0).tags())
        .extracting(DiaryDailyResponse.TagItem::tagCode)
        .containsExactly("WORK", "PROUD");
    InOrder inOrder = inOrder(diaryDraftCleanupService, diaryRepository);
    inOrder.verify(diaryDraftCleanupService)
        .cleanupExpiredDrafts(1L, SEOUL_ZONE_ID);
    inOrder.verify(diaryRepository)
        .findCompletedByUserIdAndDiaryDateOrderByCompletedAtAsc(1L, diaryDate);
  }

  @Test
  @DisplayName("Draft cleanup에 사용자의 timezone을 전달한다")
  void getDailyDiaries_cleanupUsesUserZone() {
    User user = user("America/Los_Angeles");
    ZoneId userZone = ZoneId.of("America/Los_Angeles");
    LocalDate today = LocalDate.now(userZone);
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(diaryRepository.findCompletedByUserIdAndDiaryDateOrderByCompletedAtAsc(1L, today))
        .willReturn(List.of());
    given(diaryRepository.findDraftByUserIdAndDiaryDate(1L, today))
        .willReturn(Optional.empty());
    given(diaryRepository.countByUserIdAndDiaryDate(1L, today)).willReturn(0L);

    diaryService.getDailyDiaries(1L, today);

    verify(diaryDraftCleanupService).cleanupExpiredDrafts(1L, userZone);
  }

  @Test
  @DisplayName("일별 조회 시간은 서버 저장 기준 시간에서 사용자 timezone으로 변환한다")
  void getDailyDiaries_convertsCreatedAtFromServerZoneToUserZone() {
    User user = user("America/Los_Angeles");
    ZoneId userZoneId = ZoneId.of("America/Los_Angeles");
    LocalDate today = LocalDate.now(userZoneId);
    LocalDate diaryDate = LocalDate.of(2026, 6, 25);
    Diary diary = completedDiary(user, diaryDate, 10L, -1,
        LocalDateTime.of(2026, 6, 25, 9, 0), List.of(DiaryTagCode.WORK));

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(diaryRepository.findCompletedByUserIdAndDiaryDateOrderByCompletedAtAsc(1L, diaryDate))
        .willReturn(List.of(diary));
    given(diaryRepository.findDraftByUserIdAndDiaryDate(1L, diaryDate))
        .willReturn(Optional.empty());
    given(diaryRepository.countByUserIdAndDiaryDate(1L, diaryDate)).willReturn(1L);
    given(diaryRepository.countByUserIdAndDiaryDate(1L, today)).willReturn(1L);

    DiaryDailyResponse response = diaryService.getDailyDiaries(1L, diaryDate);

    assertThat(response.diaries().get(0).createdAt()).isEqualTo("2026-06-24T16:50-07:00");
  }

  @Test
  @DisplayName("오늘 조회는 서버 DRAFT와 작성 가능 count를 함께 반환한다")
  void getDailyDiaries_todayWithDraft() {
    User user = user("Asia/Seoul");
    LocalDate today = LocalDate.now(SEOUL_ZONE_ID);
    Diary draft = draftDiary(user, today);
    ReflectionTestUtils.setField(draft, "id", 30L);
    ReflectionTestUtils.setField(draft, "createdAt", LocalDateTime.of(2026, 6, 25, 16, 20));

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(diaryRepository.findCompletedByUserIdAndDiaryDateOrderByCompletedAtAsc(1L, today))
        .willReturn(List.of());
    given(diaryRepository.findDraftByUserIdAndDiaryDate(1L, today)).willReturn(Optional.of(draft));
    given(diaryRepository.countByUserIdAndDiaryDate(1L, today)).willReturn(1L);

    DiaryDailyResponse response = diaryService.getDailyDiaries(1L, today);

    assertThat(response.hasWritten()).isFalse();
    assertThat(response.diaryCount()).isZero();
    assertThat(response.todayDiaryCount()).isEqualTo(1);
    assertThat(response.remainingDiaryCount()).isEqualTo(2);
    assertThat(response.canWriteMore()).isTrue();
    assertThat(response.draftDiary()).isNotNull();
    assertThat(response.draftDiary().diaryId()).isEqualTo(30L);
    assertThat(response.draftDiary().status()).isEqualTo(DiaryStatus.DRAFT.name());
    assertThat(response.draftDiary().forynComment()).isEqualTo("코멘트");
  }

  @Test
  @DisplayName("과거 날짜 조회는 선택 날짜 DRAFT와 선택 날짜 count를 반환한다")
  void getDailyDiaries_pastDateIncludesDraftAndSelectedDateCounts() {
    User user = user("Asia/Seoul");
    LocalDate today = LocalDate.now(SEOUL_ZONE_ID);
    LocalDate selectedDate = today.minusDays(2);
    Diary draft = draftDiary(user, selectedDate);
    ReflectionTestUtils.setField(draft, "id", 30L);
    ReflectionTestUtils.setField(draft, "createdAt", LocalDateTime.now().minusMinutes(10));
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(diaryRepository.findCompletedByUserIdAndDiaryDateOrderByCompletedAtAsc(
        1L, selectedDate))
        .willReturn(List.of());
    given(diaryRepository.findDraftByUserIdAndDiaryDate(1L, selectedDate))
        .willReturn(Optional.of(draft));
    given(diaryRepository.countByUserIdAndDiaryDate(1L, selectedDate)).willReturn(1L);
    given(diaryRepository.countByUserIdAndDiaryDate(1L, today)).willReturn(2L);

    DiaryDailyResponse response = diaryService.getDailyDiaries(1L, selectedDate);

    assertThat(response.draftDiary()).isNotNull();
    assertThat(response.draftDiary().diaryId()).isEqualTo(30L);
    assertThat(response.diaryCount()).isZero();
    assertThat(response.canWriteMore()).isTrue();
    assertThat(response.remainingDiaryCount()).isEqualTo(2);
    assertThat(response.todayDiaryCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("과거 선택 날짜 사용 수가 3이면 추가 작성할 수 없다")
  void getDailyDiaries_selectedDateLimitReached() {
    User user = user("Asia/Seoul");
    LocalDate today = LocalDate.now(SEOUL_ZONE_ID);
    LocalDate selectedDate = today.minusDays(2);
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(diaryRepository.findCompletedByUserIdAndDiaryDateOrderByCompletedAtAsc(
        1L, selectedDate)).willReturn(List.of());
    given(diaryRepository.findDraftByUserIdAndDiaryDate(1L, selectedDate))
        .willReturn(Optional.empty());
    given(diaryRepository.countByUserIdAndDiaryDate(1L, selectedDate)).willReturn(3L);
    given(diaryRepository.countByUserIdAndDiaryDate(1L, today)).willReturn(0L);

    DiaryDailyResponse response = diaryService.getDailyDiaries(1L, selectedDate);

    assertThat(response.canWriteMore()).isFalse();
    assertThat(response.remainingDiaryCount()).isZero();
    assertThat(response.todayDiaryCount()).isZero();
  }

  @Test
  @DisplayName("미래 날짜 일별 조회는 DIARY_DATE_INVALID(DIARY4010)을 반환한다")
  void getDailyDiaries_futureDateRejected() {
    User user = user("Asia/Seoul");
    LocalDate futureDate = LocalDate.now(SEOUL_ZONE_ID).plusDays(1);
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    assertThatThrownBy(() -> diaryService.getDailyDiaries(1L, futureDate))
        .isInstanceOf(DiaryHandler.class)
        .hasMessage(ErrorStatus.DIARY_DATE_INVALID.getMessage());
  }

  private DiaryCreateRequest validRequest() {
    return new DiaryCreateRequest(List.of(
        new DiaryCreateRequest.AnswerRequest(1, "순간"),
        new DiaryCreateRequest.AnswerRequest(2, "생각"),
        new DiaryCreateRequest.AnswerRequest(3, "감정")));
  }

  private User user(String timeZone) {
    return User.builder()
        .id(1L)
        .email("user@test.com")
        .fullName("테스트 유저")
        .timeZone(timeZone)
        .build();
  }

  private Diary draftDiary(User user, LocalDate diaryDate) {
    return Diary.builder()
        .user(user)
        .diaryDate(diaryDate)
        .status(DiaryStatus.DRAFT)
        .momentAnswer("순간")
        .thoughtAnswer("생각")
        .emotionAnswer("감정")
        .forynComment("코멘트")
        .build();
  }

  private Diary completedDiary(
      User user,
      LocalDate diaryDate,
      Long diaryId,
      int emotionScore,
      LocalDateTime completedAt,
      List<DiaryTagCode> tagCodes) {
    Diary diary = Diary.builder()
        .user(user)
        .diaryDate(diaryDate)
        .status(DiaryStatus.COMPLETED)
        .momentAnswer("순간")
        .thoughtAnswer("생각")
        .emotionAnswer("감정")
        .forynComment("코멘트")
        .emotionScore(emotionScore)
        .tagCodes(tagCodes)
        .completedAt(completedAt)
        .build();
    ReflectionTestUtils.setField(diary, "id", diaryId);
    ReflectionTestUtils.setField(diary, "createdAt", completedAt.minusMinutes(10));
    return diary;
  }
}
