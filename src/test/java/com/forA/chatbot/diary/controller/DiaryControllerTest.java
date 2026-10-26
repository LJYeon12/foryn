package com.forA.chatbot.diary.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.code.status.SuccessStatus;
import com.forA.chatbot.apiPayload.exception.handler.DiaryHandler;
import com.forA.chatbot.auth.jwt.CustomUserDetails;
import com.forA.chatbot.auth.jwt.JwtAuthenticationFilter;
import com.forA.chatbot.diary.dto.DiaryCreateRequest;
import com.forA.chatbot.diary.dto.DiaryCreateResponse;
import com.forA.chatbot.diary.dto.DiaryCompleteRequest;
import com.forA.chatbot.diary.dto.DiaryCompleteResponse;
import com.forA.chatbot.diary.dto.DiaryDailyResponse;
import com.forA.chatbot.diary.service.DiaryService;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DiaryController.class)
@AutoConfigureMockMvc(addFilters = false)
class DiaryControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private DiaryService diaryService;

  @MockitoBean
  private JwtAuthenticationFilter jwtAuthenticationFilter;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("한줄일기 작성 시작 API는 DIARY2001 응답을 반환한다")
  void createDiaryDraft_success() throws Exception {
    authenticateAsUser(1L);
    LocalDate selectedDate = LocalDate.of(2026, 6, 25);
    given(diaryService.createDiaryDraft(
        eq(1L), eq(selectedDate), any(DiaryCreateRequest.class)))
        .willReturn(new DiaryCreateResponse(10L, "DRAFT", "AI 코멘트", 1, 2));

    mockMvc.perform(post("/api/v1/diaries")
            .param("date", "2026-06-25")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "answers": [
                    {"questionId": 1, "content": "순간"},
                    {"questionId": 2, "content": "생각"},
                    {"questionId": 3, "content": "감정"}
                  ]
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.code").value(SuccessStatus.DIARY_CREATED.getCode()))
        .andExpect(jsonPath("$.message").value(SuccessStatus.DIARY_CREATED.getMessage()))
        .andExpect(jsonPath("$.result.diaryId").value(10))
        .andExpect(jsonPath("$.result.status").value("DRAFT"))
        .andExpect(jsonPath("$.result.forynComment").value("AI 코멘트"))
        .andExpect(jsonPath("$.result.todayCreatedDiaryCount").value(1))
        .andExpect(jsonPath("$.result.remainingDiaryCount").value(2));

    verify(diaryService).createDiaryDraft(
        eq(1L), eq(selectedDate), any(DiaryCreateRequest.class));
  }

  @Test
  @DisplayName("한줄일기 작성 날짜가 없으면 400을 반환한다")
  void createDiaryDraft_missingDate() throws Exception {
    authenticateAsUser(1L);

    mockMvc.perform(post("/api/v1/diaries")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"answers": []}
                """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("한줄일기 작성 날짜 형식이 잘못되면 400을 반환한다")
  void createDiaryDraft_invalidDateFormat() throws Exception {
    authenticateAsUser(1L);

    mockMvc.perform(post("/api/v1/diaries")
            .param("date", "2026/06/25")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"answers": []}
                """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("한줄일기 비즈니스 에러는 공통 예외 응답으로 변환된다")
  void createDiaryDraft_businessError() throws Exception {
    authenticateAsUser(1L);
    LocalDate selectedDate = LocalDate.of(2026, 6, 25);
    given(diaryService.createDiaryDraft(
        eq(1L), eq(selectedDate), any(DiaryCreateRequest.class)))
        .willThrow(new DiaryHandler(ErrorStatus.DIARY_DRAFT_ALREADY_EXISTS));

    mockMvc.perform(post("/api/v1/diaries")
            .param("date", "2026-06-25")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "answers": [
                    {"questionId": 1, "content": "순간"},
                    {"questionId": 2, "content": "생각"},
                    {"questionId": 3, "content": "감정"}
                  ]
                }
                """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value(ErrorStatus.DIARY_DRAFT_ALREADY_EXISTS.getCode()))
        .andExpect(jsonPath("$.message").value(ErrorStatus.DIARY_DRAFT_ALREADY_EXISTS.getMessage()));
  }

  @Test
  @DisplayName("한줄일기 작성 완료 API는 DIARY2002 응답을 반환한다")
  void completeDiary_success() throws Exception {
    authenticateAsUser(1L);
    given(diaryService.completeDiary(eq(1L), eq(10L), any(DiaryCompleteRequest.class)))
        .willReturn(new DiaryCompleteResponse(
            10L, "COMPLETED", 1, 2, "오늘 첫 기록 완료! 👏"));

    mockMvc.perform(post("/api/v1/diaries/{diaryId}/complete", 10L)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "emotionScore": 2,
                  "tagCodes": ["WORK", "PROUD"]
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.code").value(SuccessStatus.DIARY_COMPLETED.getCode()))
        .andExpect(jsonPath("$.message").value(SuccessStatus.DIARY_COMPLETED.getMessage()))
        .andExpect(jsonPath("$.result.diaryId").value(10))
        .andExpect(jsonPath("$.result.status").value("COMPLETED"))
        .andExpect(jsonPath("$.result.todayCompletedDiaryCount").value(1))
        .andExpect(jsonPath("$.result.remainingDiaryCount").value(2))
        .andExpect(jsonPath("$.result.completionMessage").value("오늘 첫 기록 완료! 👏"));

    verify(diaryService).completeDiary(eq(1L), eq(10L), any(DiaryCompleteRequest.class));
  }

  @Test
  @DisplayName("한줄일기 완료 비즈니스 에러는 공통 예외 응답으로 변환된다")
  void completeDiary_businessError() throws Exception {
    authenticateAsUser(1L);
    given(diaryService.completeDiary(eq(1L), eq(10L), any(DiaryCompleteRequest.class)))
        .willThrow(new DiaryHandler(ErrorStatus.DIARY_ALREADY_COMPLETED));

    mockMvc.perform(post("/api/v1/diaries/{diaryId}/complete", 10L)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "emotionScore": 2,
                  "tagCodes": ["WORK"]
                }
                """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value(ErrorStatus.DIARY_ALREADY_COMPLETED.getCode()))
        .andExpect(jsonPath("$.message").value(ErrorStatus.DIARY_ALREADY_COMPLETED.getMessage()));
  }

  @Test
  @DisplayName("일별 한줄일기 조회 API는 DIARY2003 응답을 반환한다")
  void getDailyDiaries_success() throws Exception {
    authenticateAsUser(1L);
    DiaryDailyResponse response = new DiaryDailyResponse(
        "2026-06-25",
        true,
        1,
        true,
        3,
        2,
        1,
        new DiaryDailyResponse.DraftDiaryItem(
            30L,
            "DRAFT",
            "2026-06-25T08:30:00+09:00",
            List.of(new DiaryDailyResponse.AnswerItem(1, "오늘 가장 기억 남는 순간은?", "임시 순간")),
            "임시 코멘트"),
        List.of(new DiaryDailyResponse.DiaryItem(
            10L,
            1,
            "2026-06-25T09:00:00+09:00",
            List.of(new DiaryDailyResponse.AnswerItem(1, "오늘 가장 기억 남는 순간은?", "순간")),
            "코멘트",
            2,
            List.of(new DiaryDailyResponse.TagItem("POSITIVE", "PROUD", "뿌듯함")))));
    given(diaryService.getDailyDiaries(1L, LocalDate.of(2026, 6, 25))).willReturn(response);

    mockMvc.perform(get("/api/v1/diaries/daily")
            .param("date", "2026-06-25"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.code").value(SuccessStatus.DIARY_DAILY_RETRIEVED.getCode()))
        .andExpect(jsonPath("$.message").value(SuccessStatus.DIARY_DAILY_RETRIEVED.getMessage()))
        .andExpect(jsonPath("$.result.date").value("2026-06-25"))
        .andExpect(jsonPath("$.result.hasWritten").value(true))
        .andExpect(jsonPath("$.result.diaryCount").value(1))
        .andExpect(jsonPath("$.result.canWriteMore").value(true))
        .andExpect(jsonPath("$.result.maxDiaryCount").value(3))
        .andExpect(jsonPath("$.result.todayDiaryCount").value(2))
        .andExpect(jsonPath("$.result.remainingDiaryCount").value(1))
        .andExpect(jsonPath("$.result.draftDiary.diaryId").value(30))
        .andExpect(jsonPath("$.result.draftDiary.status").value("DRAFT"))
        .andExpect(jsonPath("$.result.diaries[0].diaryId").value(10))
        .andExpect(jsonPath("$.result.diaries[0].recordOrder").value(1))
        .andExpect(jsonPath("$.result.diaries[0].tags[0].tagCode").value("PROUD"));

    verify(diaryService).getDailyDiaries(1L, LocalDate.of(2026, 6, 25));
  }

  private void authenticateAsUser(Long userId) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(userId), null, Collections.emptyList()));
  }
}
