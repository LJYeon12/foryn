package com.forA.chatbot.diary.service;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.DiaryHandler;
import com.forA.chatbot.apiPayload.exception.handler.UserHandler;
import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.diary.domain.Diary;
import com.forA.chatbot.diary.domain.enums.DiaryQuestion;
import com.forA.chatbot.diary.domain.enums.DiaryStatus;
import com.forA.chatbot.diary.domain.enums.DiaryTagCode;
import com.forA.chatbot.diary.dto.DiaryCompleteRequest;
import com.forA.chatbot.diary.dto.DiaryCompleteResponse;
import com.forA.chatbot.diary.dto.DiaryCreateRequest;
import com.forA.chatbot.diary.dto.DiaryCreateResponse;
import com.forA.chatbot.diary.dto.DiaryDailyResponse;
import com.forA.chatbot.diary.repository.DiaryRepository;
import com.forA.chatbot.global.time.AppDateTimeProvider;
import com.forA.chatbot.user.domain.User;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryService {

  private static final int REQUIRED_ANSWER_COUNT = 3;
  private static final int MAX_ANSWER_LENGTH = 500;
  private static final int MAX_DAILY_DIARY_COUNT = 3;
  private static final int MAX_TAG_COUNT = 6;
  private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Seoul");

  private final UserRepository userRepository;
  private final DiaryRepository diaryRepository;
  private final DiaryAiService diaryAiService;
  private final DiaryDraftCleanupService diaryDraftCleanupService;
  private final DiaryTransactionExecutor diaryTransactionExecutor;
  private final AppDateTimeProvider appDateTimeProvider;

  public DiaryCreateResponse createDiaryDraft(
      Long userId, LocalDate date, DiaryCreateRequest request) {
    // 세 질문의 답을 확인한 뒤, 질문 번호에 맞게 저장할 값을 꺼낸다.
    Map<DiaryQuestion, String> answers = validateAndMapAnswers(request);
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));
    ZoneId userZoneId = zoneIdOf(user);
    LocalDate today = appDateTimeProvider.today(userZoneId.getId());
    // 선택 날짜가 미래 날짜인 경우 차단
    validateTargetDate(date, today);

    // 만료된 초안 삭제
    diaryDraftCleanupService.cleanupExpiredDrafts(userId, userZoneId);
    return diaryTransactionExecutor.execute(
        () -> createDiaryDraftInTransaction(userId, date, answers, user, today));
  }

  private DiaryCreateResponse createDiaryDraftInTransaction(
      Long userId,
      LocalDate date,
      Map<DiaryQuestion, String> answers,
      User user,
      LocalDate today) {
    Optional<Diary> existingDraft =
        diaryRepository.findDraftByUserIdAndDiaryDate(userId, date);
    // 오늘 초안이 있으면 이어서 쓴다. 과거 날짜 초안만 내용을 다시 저장할 수 있다.
    if (date.isEqual(today) && existingDraft.isPresent()) {
      throw new DiaryHandler(ErrorStatus.DIARY_DRAFT_ALREADY_EXISTS);
    }
    long selectedDateUsedCount = diaryRepository.countByUserIdAndDiaryDate(userId, date);
    if (existingDraft.isEmpty() && selectedDateUsedCount >= MAX_DAILY_DIARY_COUNT) {
      throw new DiaryHandler(ErrorStatus.DIARY_DAILY_LIMIT_EXCEEDED);
    }

    String momentAnswer = answers.get(DiaryQuestion.MOMENT);
    String thoughtAnswer = answers.get(DiaryQuestion.THOUGHT);
    String emotionAnswer = answers.get(DiaryQuestion.EMOTION);
    // AI 답변이 실패해도 일기 작성은 계속한다. 기본 문구를 대신 쓴다.
    String forynComment = generateForynCommentSafely(momentAnswer, thoughtAnswer, emotionAnswer);

    Optional<Diary> lockedDraft =
        diaryRepository.findDraftByUserIdAndDiaryDateForUpdate(userId, date);
    Diary savedDiary;
    if (lockedDraft.isPresent()) {
      if (date.isEqual(today)) {
        throw new DiaryHandler(ErrorStatus.DIARY_DRAFT_ALREADY_EXISTS);
      }
      Diary draft = lockedDraft.get();
      // 과거 초안은 새 일기를 만들지 않고, 같은 일기의 내용만 바꾼다.
      draft.overwriteDraft(momentAnswer, thoughtAnswer, emotionAnswer, forynComment);
      diaryRepository.flush();
      savedDiary = draft;
    } else {
      // 동시에 다른 요청이 일기를 만들었을 수 있어, 저장 직전에 한 번 더 개수를 확인한다.
      long recheckedCount = diaryRepository.countByUserIdAndDiaryDate(userId, date);
      if (recheckedCount >= MAX_DAILY_DIARY_COUNT) {
        throw new DiaryHandler(ErrorStatus.DIARY_DAILY_LIMIT_EXCEEDED);
      }
      savedDiary = saveDraft(
          user, date, momentAnswer, thoughtAnswer, emotionAnswer, forynComment);
    }

    int todayCreatedDiaryCount = Math.toIntExact(
        diaryRepository.countByUserIdAndDiaryDate(userId, today));
    int selectedDateCount = Math.toIntExact(
        diaryRepository.countByUserIdAndDiaryDate(userId, date));
    // todayCreatedDiaryCount는 오늘 개수다. remainingDiaryCount는 고른 날짜에 더 쓸 수 있는 개수다.
    int remainingDiaryCount = Math.max(0, MAX_DAILY_DIARY_COUNT - selectedDateCount);

    return new DiaryCreateResponse(
        savedDiary.getId(),
        savedDiary.getStatus().name(),
        savedDiary.getForynComment(),
        todayCreatedDiaryCount,
        remainingDiaryCount);
  }

  public DiaryCompleteResponse completeDiary(
      Long userId, Long diaryId, DiaryCompleteRequest request) {
    CompleteValidationResult validationResult = validateCompleteRequest(request);
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));
    ZoneId userZoneId = zoneIdOf(user);
    LocalDate today = appDateTimeProvider.today(userZoneId.getId());

    // 완료가 실패해도 오래된 초안은 지워져야 한다. 그래서 완료 처리 전에 먼저 정리한다.
    diaryDraftCleanupService.cleanupExpiredDrafts(userId, userZoneId);
    return diaryTransactionExecutor.execute(
        () -> completeDiaryInTransaction(userId, diaryId, validationResult, userZoneId, today));
  }

  private DiaryCompleteResponse completeDiaryInTransaction(
      Long userId,
      Long diaryId,
      CompleteValidationResult validationResult,
      ZoneId userZoneId,
      LocalDate today) {
    Diary diary = diaryRepository.findByIdAndUserIdForUpdate(diaryId, userId)
        .orElseThrow(() -> new DiaryHandler(ErrorStatus.DIARY_NOT_FOUND));

    validateCanComplete(diary, today);
    // 초안을 지우고 새 일기를 만들지 않는다. 같은 일기를 완료 상태로 바꾼다.
    diary.complete(
        validationResult.emotionScore(),
        validationResult.tagCodes(),
        LocalDateTime.now(userZoneId));
    log.info("한줄일기 작성 완료 - 사용자 ID: {}, diaryId: {}", userId, diaryId);

    int todayCompletedDiaryCount = Math.toIntExact(
        diaryRepository.countByUserIdAndDiaryDateAndStatus(userId, today, DiaryStatus.COMPLETED));
    int selectedDateUsedCount = Math.toIntExact(
        diaryRepository.countByUserIdAndDiaryDate(userId, diary.getDiaryDate()));
    int remainingDiaryCount = Math.max(0, MAX_DAILY_DIARY_COUNT - selectedDateUsedCount);
    // 과거 날짜 일기는 '오늘'이라는 문구 대신 기본 완료 문구를 보여 준다.
    String message = diary.getDiaryDate().isEqual(today)
        ? completionMessage(todayCompletedDiaryCount)
        : "한줄일기 작성을 완료했습니다.";

    return new DiaryCompleteResponse(
        diary.getId(),
        diary.getStatus().name(),
        todayCompletedDiaryCount,
        remainingDiaryCount,
        message);
  }

  public DiaryDailyResponse getDailyDiaries(Long userId, LocalDate date) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));
    ZoneId userZoneId = zoneIdOf(user);
    LocalDate today = appDateTimeProvider.today(userZoneId.getId());
    // 조회도 작성과 똑같이 미래 날짜는 허용하지 않는다.
    validateTargetDate(date, today);

    // 조회하기 전에 오래된 초안을 지워 응답에 섞이지 않게 한다.
    diaryDraftCleanupService.cleanupExpiredDrafts(userId, userZoneId);
    return diaryTransactionExecutor.execute(
        () -> getDailyDiariesInTransaction(userId, date, userZoneId, today));
  }

  private DiaryDailyResponse getDailyDiariesInTransaction(
      Long userId, LocalDate date, ZoneId userZoneId, LocalDate today) {
    List<Diary> completedDiaries =
        diaryRepository.findCompletedByUserIdAndDiaryDateOrderByCompletedAtAsc(userId, date);
    DiaryDailyResponse.DraftDiaryItem draftDiary =
        diaryRepository.findDraftByUserIdAndDiaryDate(userId, date)
            .map(diary -> toDraftDiaryItem(diary, userZoneId))
            .orElse(null);

    int selectedDateUsedCount = Math.toIntExact(
        diaryRepository.countByUserIdAndDiaryDate(userId, date));
    int todayDiaryCount = Math.toIntExact(diaryRepository.countByUserIdAndDiaryDate(userId, today));
    int remainingDiaryCount = Math.max(0, MAX_DAILY_DIARY_COUNT - selectedDateUsedCount);

    // 선택한 날짜의 초안도 함께 보낸다. 화면에 어떻게 보여 줄지는 앱이 정한다.
    return new DiaryDailyResponse(
        date.toString(),
        !completedDiaries.isEmpty(),
        completedDiaries.size(),
        selectedDateUsedCount < MAX_DAILY_DIARY_COUNT,
        MAX_DAILY_DIARY_COUNT,
        todayDiaryCount,
        remainingDiaryCount,
        draftDiary,
        toDiaryItems(completedDiaries, userZoneId));
  }

  private Map<DiaryQuestion, String> validateAndMapAnswers(DiaryCreateRequest request) {
    if (request == null || request.getAnswers() == null
        || request.getAnswers().size() != REQUIRED_ANSWER_COUNT) {
      throw new DiaryHandler(ErrorStatus.DIARY_ANSWERS_SIZE_INVALID);
    }

    Map<DiaryQuestion, String> answers = new HashMap<>();
    for (DiaryCreateRequest.AnswerRequest answer : request.getAnswers()) {
      if (answer == null || answer.getQuestionId() == null) {
        throw new DiaryHandler(ErrorStatus.DIARY_QUESTION_SET_INVALID);
      }
      DiaryQuestion question = parseQuestion(answer.getQuestionId());
      if (answers.containsKey(question)) {
        throw new DiaryHandler(ErrorStatus.DIARY_QUESTION_SET_INVALID);
      }
      validateContent(answer.getContent());
      answers.put(question, answer.getContent().strip());
    }

    if (answers.size() != REQUIRED_ANSWER_COUNT
        || !answers.keySet().containsAll(List.of(
            DiaryQuestion.MOMENT, DiaryQuestion.THOUGHT, DiaryQuestion.EMOTION))) {
      throw new DiaryHandler(ErrorStatus.DIARY_QUESTION_SET_INVALID);
    }
    return answers;
  }

  private DiaryQuestion parseQuestion(int questionId) {
    try {
      return DiaryQuestion.fromId(questionId);
    } catch (IllegalArgumentException e) {
      throw new DiaryHandler(ErrorStatus.DIARY_QUESTION_SET_INVALID);
    }
  }

  private void validateContent(String content) {
    if (content == null || content.isBlank() || content.length() > MAX_ANSWER_LENGTH) {
      throw new DiaryHandler(ErrorStatus.DIARY_ANSWER_CONTENT_INVALID);
    }
  }

  private ZoneId zoneIdOf(User user) {
    try {
      return ZoneId.of(user.getTimeZone());
    } catch (DateTimeException | NullPointerException e) {
      // 사용자 시간대가 잘못돼도 날짜 기능이 멈추지 않게 서울 시간을 사용한다.
      log.warn("사용자 timeZone이 유효하지 않아 기본 시간대를 사용합니다. userId={}", user.getId());
      return DEFAULT_ZONE_ID;
    }
  }

  private void validateTargetDate(LocalDate date, LocalDate today) {
    // 미래 날짜를 차단
    if (date == null || date.isAfter(today)) {
      throw new DiaryHandler(ErrorStatus.DIARY_DATE_INVALID);
    }
  }

  private String generateForynCommentSafely(
      String momentAnswer, String thoughtAnswer, String emotionAnswer) {
    try {
      return diaryAiService.generateForynComment(momentAnswer, thoughtAnswer, emotionAnswer);
    } catch (Exception e) {
      log.error("한줄일기 AI 서비스 호출 중 예외가 발생해 fallback 코멘트를 사용합니다.", e);
      return DiaryAiService.FALLBACK_FORYN_COMMENT;
    }
  }

  private Diary saveDraft(
      User user,
      LocalDate date,
      String momentAnswer,
      String thoughtAnswer,
      String emotionAnswer,
      String forynComment) {
    try {
      // 동시에 같은 날짜에 초안을 만들면 DB가 하나만 남기고, 나머지는 여기서 막는다.
      return diaryRepository.saveAndFlush(Diary.builder()
          .user(user)
          .diaryDate(date)
          .status(DiaryStatus.DRAFT)
          .momentAnswer(momentAnswer)
          .thoughtAnswer(thoughtAnswer)
          .emotionAnswer(emotionAnswer)
          .forynComment(forynComment)
          .build());
    } catch (DataIntegrityViolationException e) {
      log.warn("한줄일기 DRAFT 저장 중 중복 제약이 발생했습니다. userId={}", user.getId());
      throw new DiaryHandler(ErrorStatus.DIARY_DRAFT_ALREADY_EXISTS);
    }
  }

  private CompleteValidationResult validateCompleteRequest(DiaryCompleteRequest request) {
    if (request == null || request.emotionScore() == null
        || request.emotionScore() < -2 || request.emotionScore() > 2) {
      throw new DiaryHandler(ErrorStatus.DIARY_EMOTION_SCORE_INVALID);
    }
    if (request.tagCodes() == null) {
      throw new DiaryHandler(ErrorStatus.DIARY_TAG_CODES_NULL);
    }
    if (request.tagCodes().size() > MAX_TAG_COUNT) {
      throw new DiaryHandler(ErrorStatus.DIARY_TAG_LIMIT_EXCEEDED);
    }

    Set<String> uniqueTagCodes = new LinkedHashSet<>();
    for (String tagCode : request.tagCodes()) {
      if (tagCode == null || !uniqueTagCodes.add(tagCode)) {
        throw new DiaryHandler(ErrorStatus.DIARY_TAG_DUPLICATED);
      }
    }

    List<DiaryTagCode> parsedTagCodes = uniqueTagCodes.stream()
        .map(this::parseTagCode)
        .toList();
    return new CompleteValidationResult(request.emotionScore(), parsedTagCodes);
  }

  private DiaryTagCode parseTagCode(String tagCode) {
    try {
      return DiaryTagCode.valueOf(tagCode);
    } catch (IllegalArgumentException e) {
      throw new DiaryHandler(ErrorStatus.DIARY_TAG_CODE_INVALID);
    }
  }

  private void validateCanComplete(Diary diary, LocalDate today) {
    // 작성 중인 초안만 완료할 수 있다. 이미 끝난 일기와 미래 날짜 일기는 바꾸지 않는다.
    if (diary.getStatus() == DiaryStatus.COMPLETED) {
      throw new DiaryHandler(ErrorStatus.DIARY_ALREADY_COMPLETED);
    }
    if (diary.getStatus() != DiaryStatus.DRAFT) {
      throw new DiaryHandler(ErrorStatus.DIARY_NOT_FOUND);
    }
    if (diary.getDiaryDate().isAfter(today)) {
      throw new DiaryHandler(ErrorStatus.DIARY_DATE_INVALID);
    }
  }

  private String completionMessage(int todayCompletedDiaryCount) {
    return switch (todayCompletedDiaryCount) {
      case 1 -> "오늘 첫 기록 완료! 👏";
      case 2 -> "오늘 벌써 두 번째 기록이네요! 🐾";
      case 3 -> "오늘의 3가지 기록 대성공! 🎉";
      default -> "한줄일기 작성을 완료했습니다.";
    };
  }

  private List<DiaryDailyResponse.DiaryItem> toDiaryItems(
      List<Diary> diaries, ZoneId userZoneId) {
    return java.util.stream.IntStream.range(0, diaries.size())
        .mapToObj(index -> toDiaryItem(diaries.get(index), index + 1, userZoneId))
        .toList();
  }

  private DiaryDailyResponse.DiaryItem toDiaryItem(
      Diary diary, int recordOrder, ZoneId userZoneId) {
    return new DiaryDailyResponse.DiaryItem(
        diary.getId(),
        recordOrder,
        formatDateTime(diary.getCreatedAt(), userZoneId),
        toAnswerItems(diary),
        diary.getForynComment(),
        diary.getEmotionScore(),
        toTagItems(diary.getTagCodes()));
  }

  private DiaryDailyResponse.DraftDiaryItem toDraftDiaryItem(Diary diary, ZoneId userZoneId) {
    return new DiaryDailyResponse.DraftDiaryItem(
        diary.getId(),
        diary.getStatus().name(),
        formatDateTime(diary.getCreatedAt(), userZoneId),
        toAnswerItems(diary),
        diary.getForynComment());
  }

  private List<DiaryDailyResponse.AnswerItem> toAnswerItems(Diary diary) {
    // 따로 저장한 세 답을 앱이 받는 질문 순서로 다시 묶는다.
    return List.of(
        new DiaryDailyResponse.AnswerItem(
            DiaryQuestion.MOMENT.getId(), DiaryQuestion.MOMENT.getQuestion(), diary.getMomentAnswer()),
        new DiaryDailyResponse.AnswerItem(
            DiaryQuestion.THOUGHT.getId(), DiaryQuestion.THOUGHT.getQuestion(), diary.getThoughtAnswer()),
        new DiaryDailyResponse.AnswerItem(
            DiaryQuestion.EMOTION.getId(), DiaryQuestion.EMOTION.getQuestion(), diary.getEmotionAnswer()));
  }

  private List<DiaryDailyResponse.TagItem> toTagItems(List<DiaryTagCode> tagCodes) {
    return tagCodes.stream()
        .map(tagCode -> new DiaryDailyResponse.TagItem(
            tagCode.getTagType().name(),
            tagCode.name(),
            tagCode.getLabel()))
        .toList();
  }

  private String formatDateTime(LocalDateTime dateTime, ZoneId userZoneId) {
    if (dateTime == null) {
      return null;
    }
    // DB에는 서울 시간으로 저장한다. 앱에는 사용자의 시간대로 바꿔서 보낸다.
    return dateTime.atZone(DEFAULT_ZONE_ID)
        .withZoneSameInstant(userZoneId)
        .toOffsetDateTime()
        .toString();
  }

  private record CompleteValidationResult(
      Integer emotionScore,
      List<DiaryTagCode> tagCodes
  ) {
  }
}
