package com.forA.chatbot.apiPayload.code.status;

import com.forA.chatbot.apiPayload.code.BaseCode;
import com.forA.chatbot.apiPayload.code.ReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SuccessStatus implements BaseCode {

  // 일반적인 응답
  _OK(HttpStatus.OK, "COMMON200", "성공입니다."),
  _CREATED(HttpStatus.CREATED, "COMMON201", "생성에 성공했습니다."),

  // 복약 관련
  MEDICATION_CREATED(HttpStatus.CREATED, "MEDICATION2001", "약 복용 계획이 성공적으로 생성되었습니다."),
  MEDICATION_LOG_CREATED(HttpStatus.CREATED, "MEDICATION2002", "약 복용 기록이 성공적으로 생성되었습니다."),
  MEDICATION_LIST_RETRIEVED(HttpStatus.OK, "MEDICATION2003", "약 복용 계획을 성공적으로 조회하였습니다."),
  MEDICATION_UPDATED(HttpStatus.OK, "MEDICATION2004", "복용 계획이 성공적으로 수정되었습니다."),
  MEDICATION_DELETED(HttpStatus.OK, "MEDICATION2005", "복용 계획이 성공적으로 삭제되었습니다."),

  // 일기 관련
  DIARY_CREATED(HttpStatus.CREATED, "DIARY2001", "한줄일기 작성을 시작했습니다."),
  DIARY_COMPLETED(HttpStatus.OK, "DIARY2002", "한줄일기 작성을 완료했습니다."),
  DIARY_DAILY_RETRIEVED(HttpStatus.OK, "DIARY2003", "일별 한줄일기 기록을 성공적으로 조회했습니다."),
  DIARY_REMINDERS_UPDATED(HttpStatus.OK, "DIARY_REMINDER2001", "한줄일기 알림 설정을 저장했습니다."),

  // 알림 관련
  _TOKEN_REFRESH_SUCCESS(HttpStatus.OK, "NOTIFICATION200", "FCM 토큰 갱신에 성공했습니다."),
  //리포트 관련
  _WEEKLY_MEDICATION_REPORT_RETRIEVED(HttpStatus.OK, "REPORT2000","주간 복약 현황을 성공적으로 조회하였습니다."),
  _MONTHLY_MEDICATION_REPORT_RETRIEVED(HttpStatus.OK, "REPORT2001","월간 복약 현황을 성공적으로 조회했습니다."),
  _WEEKLY_EMOTION_REPORT_RETRIEVED(HttpStatus.OK, "REPORT2002", "주간 감정 리포트를 성공적으로 조회했습니다."),
  _MONTHLY_EMOTION_REPORT_RETRIEVED(HttpStatus.OK, "REPORT2003","월간 감정 리포트를 성공적으로 조회했습니다."),
  _WEEKLY_REPORT_SUMMARY_RETRIEVED(HttpStatus.OK,"REPORT2004","주간 리포트 요약을 성공적으로 조회했습니다.");
  private final HttpStatus httpStatus;
  private final String code;
  private final String message;

  @Override
  public ReasonDTO getReason() {
    return ReasonDTO.builder().message(message).code(code).isSuccess(true).build();
  }

  @Override
  public ReasonDTO getReasonHttpStatus() {
    return ReasonDTO.builder()
        .message(message)
        .code(code)
        .isSuccess(true)
        .httpStatus(httpStatus)
        .build();
  }
}
