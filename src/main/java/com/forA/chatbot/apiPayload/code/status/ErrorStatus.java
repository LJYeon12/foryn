package com.forA.chatbot.apiPayload.code.status;

import com.forA.chatbot.apiPayload.code.BaseErrorCode;
import com.forA.chatbot.apiPayload.code.ErrorReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorStatus implements BaseErrorCode {

        // 테스트용
        TEMP_EXCEPTION(HttpStatus.BAD_REQUEST, "TEMP4001", "이거는 테스트"),

        // 가장 일반적인 응답
        _INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 에러, 관리자에게 문의 바랍니다."),
        _BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON400", "잘못된 요청입니다."),
        _UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON401", "인증이 필요합니다."),
        _FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON403", "금지된 요청입니다."),

        // 인증
        APPLE_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "AUTH4001", "Apple 토큰이 유효하지 않습니다."),
        APPLE_TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "AUTH4002", "Apple 토큰이 만료되었습니다."),
        APPLE_LOGIN_FAILED(HttpStatus.BAD_REQUEST, "AUTH4003", "Apple 로그인에 실패했습니다."),
        APPLE_PUBLIC_KEY_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH5001", "Apple 공개키 조회에 실패했습니다."),
        TOKEN_REFRESH_FAILED(HttpStatus.BAD_REQUEST, "AUTH4004", "토큰 재발급에 실패했습니다."),
        EXPIRED_REFRESH_TOKEN(HttpStatus.BAD_REQUEST, "AUTH4005", "REFRESH TOKEN이 만료되었습니다"),

        // 복약 관련 오류 코드
        MEDICATION_PLAN_ACCESS_DENIED(HttpStatus.FORBIDDEN, "MEDICATION002", "해당 복용 계획에 접근할 권한이 없습니다."),
        MEDICATION_INVALID_TIME_FORMAT(
                        HttpStatus.BAD_REQUEST, "MEDICATION4002", "시간 형식이 올바르지 않습니다. HH:mm 형식으로 입력해주세요."),
        MEDICATION_NOTIFICATION_TIME_REQUIRED(
                        HttpStatus.BAD_REQUEST, "MEDICATION4003", "알림이 활성화된 경우 알림 시간은 필수입니다."),
        MEDICATION_INVALID_TIME_RANGE(
                        HttpStatus.BAD_REQUEST, "MEDICATION4004", "시간은 00:00 ~ 23:59 범위 내에서 입력해주세요."),
        MEDICATION_NAME_TOO_LONG(HttpStatus.BAD_REQUEST, "MEDICATION4005", "약 이름은 최대 20자까지 입력 가능합니다."),
        MEDICATION_PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "MEDICATION4006", "약 복용 계획을 찾을 수 없습니다."),
        MEDICATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "MEDICATION4007", "해당 약 복용 계획에 대한 접근 권한이 없습니다."),
        MEDICATION_INVALID_DAY_OF_WEEK(HttpStatus.BAD_REQUEST, "MEDICATION4008", "올바르지 않은 요일 형식입니다."),
        MEDICATION_TYPE_TAGS_EMPTY(HttpStatus.BAD_REQUEST, "MEDICATION4009", "약 종류 태그는 최소 1개 이상 필요합니다."),
        MEDICATION_TAKE_DAYS_EMPTY(
                        HttpStatus.BAD_REQUEST, "MEDICATION4010", "복용할 요일은 최소 1개 이상 선택해야 합니다."),
        MEDICATION_CONDITION_REQUIRED(HttpStatus.BAD_REQUEST, "MEDICATION4011", "컨디션을 입력해주세요."),
        MEDICATION_INVALID_DATE_FORMAT(
                        HttpStatus.BAD_REQUEST, "MEDICATION4012", "날짜 형식이 올바르지 않습니다. YYYY-MM-DD 형식으로 입력해주세요."),
        REPORT_DATA_SOURCE_UNAVAILABLE(
                        HttpStatus.INTERNAL_SERVER_ERROR, "REPORT5001", "Report data source is unavailable."),
        MEDICATION_PLAN_ALREADY_EXISTS(
                        HttpStatus.BAD_REQUEST, "MEDICATION4013", "같은 이름의 복용 계획이 존재합니다."),

        // 일기 관련
        DIARY_DAILY_LIMIT_EXCEEDED(
                        HttpStatus.BAD_REQUEST, "DIARY4001", "오늘의 기록 3번을 모두 채웠어요. 내일 또 포린이에게 이야기해 주세요."),
        DIARY_ANSWERS_SIZE_INVALID(
                        HttpStatus.BAD_REQUEST, "DIARY4002", "한줄일기 답변은 정확히 3개여야 합니다."),
        DIARY_QUESTION_SET_INVALID(
                        HttpStatus.BAD_REQUEST, "DIARY4003", "questionId는 1, 2, 3이 각각 한 번씩 포함되어야 합니다."),
        DIARY_ANSWER_CONTENT_INVALID(
                        HttpStatus.BAD_REQUEST, "DIARY4004", "답변은 비어 있을 수 없고 최대 500자까지 입력할 수 있습니다."),
        DIARY_EMOTION_SCORE_INVALID(
                        HttpStatus.BAD_REQUEST, "DIARY4005", "감정 점수는 -2, -1, 0, 1, 2 중 하나여야 합니다."),
        DIARY_TAG_CODE_INVALID(
                        HttpStatus.BAD_REQUEST, "DIARY4006", "유효하지 않은 한줄일기 태그입니다."),
        DIARY_TAG_LIMIT_EXCEEDED(
                        HttpStatus.BAD_REQUEST, "DIARY4007", "태그는 최대 6개까지 선택할 수 있습니다."),
        DIARY_TAG_DUPLICATED(
                        HttpStatus.BAD_REQUEST, "DIARY4008", "같은 태그를 중복 선택할 수 없습니다."),
        DIARY_TAG_CODES_NULL(
                        HttpStatus.BAD_REQUEST, "DIARY4009", "tagCodes는 null일 수 없습니다. 선택하지 않은 경우 빈 배열을 보내주세요."),
        // 미래 날짜에는 작성, 완료, 조회를 할 수 없다.
        DIARY_DATE_INVALID(
                        HttpStatus.BAD_REQUEST, "DIARY4010", "오늘 또는 과거 날짜만 선택할 수 있습니다."),
        DIARY_NOT_FOUND(
                        HttpStatus.NOT_FOUND, "DIARY4041", "한줄일기를 찾을 수 없습니다."),
        DIARY_ALREADY_COMPLETED(
                        HttpStatus.CONFLICT, "DIARY4091", "이미 완료된 한줄일기입니다."),
        DIARY_DRAFT_ALREADY_EXISTS(
                        HttpStatus.CONFLICT, "DIARY4092", "작성 중인 한줄일기가 있습니다. 기존 기록을 먼저 완료해 주세요."),
        DIARY_REMINDER_LIMIT_EXCEEDED(
                        HttpStatus.BAD_REQUEST, "DIARY_REMINDER4001", "한줄일기 알림은 하루 최대 3개까지 설정할 수 있습니다."),
        DIARY_REMINDER_TIME_INVALID(
                        HttpStatus.BAD_REQUEST, "DIARY_REMINDER4002", "알림 시간은 HH:mm 형식이어야 합니다."),
        DIARY_REMINDER_TIME_DUPLICATED(
                        HttpStatus.BAD_REQUEST, "DIARY_REMINDER4003", "같은 시간의 알림을 중복 설정할 수 없습니다."),
        DIARY_REMINDER_TIME_REQUIRED(
                        HttpStatus.BAD_REQUEST, "DIARY_REMINDER4004", "알림 시간은 비어 있을 수 없습니다."),
        DIARY_REMINDER_ENABLED_REQUIRED(
                        HttpStatus.BAD_REQUEST, "DIARY_REMINDER4005", "알림 활성화 여부는 필수입니다."),

        // 알림 관련
        _FCM_SEND_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "NOTIFICATION5001", "FCM 메시지 전송에 실패했습니다."),
        NOTIFICATION_NOT_ENABLED(HttpStatus.BAD_REQUEST, "NOTIFICATION4001", "알림이 비활성화되어 있습니다."),
        DEVICE_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION4002", "등록된 디바이스 토큰이 없습니다."),
        FCM_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "NOTIFICATION5001", "FCM 알림 발송에 실패했습니다."),
        INVALID_DEVICE_TOKEN(HttpStatus.BAD_REQUEST, "NOTIFICATION4003", "유효하지 않은 디바이스 토큰입니다."),

        TEST_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "TEST500", "테스트를 실패하였습니다"),

        // 사용자 관련 오류
        USER_GENDER_ALREADY_EXIST(HttpStatus.BAD_REQUEST, "USER4001", "유저 성별을 변경할 수 없습니다."),
        USER_BIRTH_YEAR_ALREADY_EXIST(HttpStatus.BAD_REQUEST, "USER4002", "유저 생년을 변경할 수 없습니다."),
        INVALID_YEAR_OF_BIRTH(HttpStatus.BAD_REQUEST, "USER4003", "유저 생년이 유효하지 않습니다."),
        USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER4004", "사용자를 찾을 수 없습니다."),
        USER_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "USER5001", "회원 탈퇴 처리에 실패했습니다."),
        USER_SYMPTOM_DISORDER_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "USER4005", "질환 당 최대 3개의 증상만 선택 가능합니다."),

        // 채팅 관련 오류
        SESSION_NOT_FOUND(HttpStatus.BAD_REQUEST, "CHAT4001", "존재하는 세션이 아닙니다."),
        INVALID_BUTTON_SELECTION(HttpStatus.BAD_REQUEST, "CHAT4002", "올바른 버튼을 선택해 주세요."),
        AI_RESPONSE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "CHAT5001", "ai 응답 생성에 실패하였습니다"),
        SKILL_NAME_IS_NULL(HttpStatus.BAD_REQUEST, "CHAT4003", "skill name이 공백입니다."),
        CHAT_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "CHAT4004", "하루에 최대 3번의 채팅만 가능합니다."),
        CHAT_STEP_NOT_FOUND(HttpStatus.BAD_REQUEST, "CHAT4005", "지원하지 않는 대화 단계입니다."),
        CHAT_SELECTION_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "CHAT4006", "선택 가능한 개수를 초과했습니다."),
        JSON_PROCESSING_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "CHAT5002", "JSON 처리 중 오류가 발생했습니다."),
        DUPLICATE_CHAT_STEP_HANDLER(HttpStatus.INTERNAL_SERVER_ERROR, "CHAT5003", "중복된 ChatStepHandler가 존재합니다."),
        CHAT_HISTORY_EMPTY(HttpStatus.BAD_REQUEST, "CHAT4007", "채팅 기록이 비어있습니다."),
        INVALID_EMOTION_NAME(HttpStatus.BAD_REQUEST, "CHAT4008", "올바르지 않은 감정 이름입니다."),
        INCONSISTENT_CHAT_STATE(HttpStatus.BAD_REQUEST, "CHAT4009", "데이터 정합성 오류가 감지되어 안전한 단계로 세션을 복구했습니다. 다시 시도해주세요."),
        // 500 서버 내부 오류: 서버 내부 로직(DB 저장 등)이 실패한 경우
        CHAT_PROCESSING_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "CHAT5001", "대화를 저장하지 못했습니다. 잠시 후 다시 시도해주세요."),
        CHAT_OPERATION_BUSY(HttpStatus.CONFLICT, "CHAT4091", "이전 대화를 처리 중입니다. 잠시 후 대화를 다시 조회해주세요."),
        CHAT_STATE_CONFLICT(HttpStatus.CONFLICT, "CHAT4092", "대화 상태가 변경되었습니다. 대화를 다시 조회해주세요."),
        CHAT_RECOVERY_PENDING(HttpStatus.SERVICE_UNAVAILABLE, "CHAT5031", "저장 결과를 확인 중입니다. 같은 응답을 다시 전송하지 말고 잠시 후 대화를 조회해주세요."),
        // 인앱 결제 (구독)
        FAILED_TO_CREATE_CERTIFICATE_FACTORY(HttpStatus.INTERNAL_SERVER_ERROR, "SUBSCRIPTION5001",
                        "CertificateFactory X.509 인스턴스 생성 실패"),
        IAP_APPLE_API_CALL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "IAP5001", "Apple App Store 서버 API 호출에 실패했습니다."),
        IAP_APPLE_INVALID_TRANSACTION(HttpStatus.BAD_REQUEST, "IAP4001", "Apple 응답에서 유효한 트랜잭션 데이터를 찾을 수 없습니다."),
        IAP_SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "IAP4002", "구독 정보를 찾을 수 없습니다. 구매/복원을 시도해주세요."),
        IAP_PREMIUM_REQUIRED(HttpStatus.FORBIDDEN, "IAP4003", "구독이 필요한 서비스입니다.");

        private final HttpStatus httpStatus;
        private final String code;
        private final String message;

        @Override
        public ErrorReasonDTO getReason() {
                return ErrorReasonDTO.builder().message(message).code(code).isSuccess(false).build();
        }

        @Override
        public ErrorReasonDTO getReasonHttpStatus() {
                return ErrorReasonDTO.builder()
                                .message(message)
                                .code(code)
                                .isSuccess(false)
                                .httpStatus(httpStatus)
                                .build();
        }
}
