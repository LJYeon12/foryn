package com.forA.chatbot.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.notification.converter.NotificationConverter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.forA.chatbot.notification.domain.DeviceToken;
import com.forA.chatbot.notification.domain.NotificationType;
import com.forA.chatbot.notification.dto.FcmNotificationRequestDto;
import com.forA.chatbot.notification.dto.FcmSendMessageDto;
import com.forA.chatbot.notification.repository.DeviceTokenRepository;
import com.forA.chatbot.user.domain.User;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  @InjectMocks private NotificationService notificationService;

  @Mock private DeviceTokenRepository deviceTokenRepository;

  @Mock private DeviceTokenCleanupService deviceTokenCleanupService;

  @Mock private UserRepository userRepository;

  @Mock private NotificationConverter notificationConverter;

  @Spy private MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @Test
  @DisplayName("채팅 리마인더 알림은 기획 문구로 제목과 본문을 전송한다")
  void sendChatReminderNotification_ShouldSendPlannedCopy() {
    Long userId = 1L;
    User user =
        User.builder()
            .id(userId)
            .email("test@example.com")
            .providerUserId("provider-user-id")
            .fullName("테스트 유저")
            .nickname("포리니")
            .build();
    DeviceToken deviceToken =
        DeviceToken.builder().user(user).deviceToken("device-token").isPushEnabled(true).build();

    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(deviceTokenRepository.findAllByUserIdAndIsPushEnabledTrue(userId))
        .willReturn(List.of(deviceToken));

    FcmNotificationRequestDto expectedRequest =
        FcmNotificationRequestDto.builder()
            .deviceToken("device-token")
            .notificationType(NotificationType.CHAT_REMINDER)
            .title("포리니님이 pick한 행동하고 오셨나요?🎯")
            .body("포린이가 간절하게 기다리고 있어냥😻")
            .build();
    given(notificationConverter.toChatReminderRequest(deviceToken, "포리니"))
        .willReturn(expectedRequest);

    NotificationService spyService =
        Mockito.spy(
            new NotificationService(
                deviceTokenRepository,
                deviceTokenCleanupService,
                userRepository,
                meterRegistry,
                notificationConverter));
    ArgumentCaptor<FcmNotificationRequestDto> requestCaptor =
        ArgumentCaptor.forClass(FcmNotificationRequestDto.class);
    doReturn(true).when(spyService).sendNotification(requestCaptor.capture());

    spyService.sendChatReminderNotification(userId, "산책");

    FcmNotificationRequestDto request = requestCaptor.getValue();
    assertThat(request.getNotificationType()).isEqualTo(NotificationType.CHAT_REMINDER);
    assertThat(request.getDeviceToken()).isEqualTo("device-token");
    assertThat(request.getTitle()).isEqualTo("포리니님이 pick한 행동하고 오셨나요?🎯");
    assertThat(request.getBody()).isEqualTo("포린이가 간절하게 기다리고 있어냥😻");
  }

  @Test
  @DisplayName("유효하지 않은 토큰이면 예외를 던지지 않고 토큰을 삭제한다")
  void sendNotification_ShouldNotThrowException_AndShouldDeleteToken_WhenUnregistered()
      throws FirebaseMessagingException {
    String targetToken = "expired_token_12345";
    FcmNotificationRequestDto request =
        FcmNotificationRequestDto.builder()
            .deviceToken(targetToken)
            .notificationType(NotificationType.STREAK_ACHIEVEMENT)
            .title("test")
            .body("test")
            .build();
    given(notificationConverter.toFcmSendMessage(request))
        .willReturn(new FcmSendMessageDto(targetToken, "test", "test",
            Map.of("type", "streak_achievement")));

    FirebaseMessagingException fcmException = mock(FirebaseMessagingException.class);
    given(fcmException.getMessagingErrorCode()).willReturn(MessagingErrorCode.UNREGISTERED);
    given(fcmException.getMessage()).willReturn("Requested entity was not found.");

    FirebaseMessaging firebaseMessagingMock = mock(FirebaseMessaging.class);
    given(firebaseMessagingMock.send(any(Message.class))).willThrow(fcmException);

    try (MockedStatic<FirebaseMessaging> mockedStatic = mockStatic(FirebaseMessaging.class)) {
      mockedStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessagingMock);

      boolean result = notificationService.sendNotification(request);

      assertThat(result).isFalse();
      verify(deviceTokenCleanupService).deleteInvalidToken(targetToken);
      assertThat(cleanupMetricCount("success")).isEqualTo(1.0);
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = MessagingErrorCode.class,
      names = {"INVALID_ARGUMENT", "UNREGISTERED", "SENDER_ID_MISMATCH"})
  @DisplayName("무효 토큰 오류면 false를 반환하고 토큰 정리 성공을 기록한다")
  void sendNotification_ShouldReturnFalse_AndCleanupToken_WhenInvalidToken(
      MessagingErrorCode errorCode)
      throws FirebaseMessagingException {
    String targetToken = "invalid_format_token_abc";
    FcmNotificationRequestDto request =
        FcmNotificationRequestDto.builder()
            .deviceToken(targetToken)
            .notificationType(NotificationType.STREAK_ACHIEVEMENT)
            .title("test")
            .body("test")
            .build();
    given(notificationConverter.toFcmSendMessage(request))
        .willReturn(new FcmSendMessageDto(targetToken, "test", "test",
            Map.of("type", "streak_achievement")));

    FirebaseMessagingException fcmException = mock(FirebaseMessagingException.class);
    given(fcmException.getMessagingErrorCode()).willReturn(errorCode);
    given(fcmException.getMessage()).willReturn("Invalid registration token.");

    FirebaseMessaging firebaseMessagingMock = mock(FirebaseMessaging.class);
    given(firebaseMessagingMock.send(any(Message.class))).willThrow(fcmException);

    try (MockedStatic<FirebaseMessaging> mockedStatic = mockStatic(FirebaseMessaging.class)) {
      mockedStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessagingMock);

      boolean result = notificationService.sendNotification(request);

      assertThat(result).isFalse();
      verify(deviceTokenCleanupService).deleteInvalidToken(targetToken);
      assertThat(cleanupMetricCount("success")).isEqualTo(1.0);
    }
  }

  @Test
  @DisplayName("무효 토큰 정리 실패도 전파하지 않고 false와 실패 메트릭을 반환한다")
  void sendNotification_ShouldReturnFalse_WhenInvalidTokenCleanupFails()
      throws FirebaseMessagingException {
    String targetToken = "expired_token_cleanup_failure";
    FcmNotificationRequestDto request =
        FcmNotificationRequestDto.builder()
            .deviceToken(targetToken)
            .notificationType(NotificationType.MEDICATION)
            .title("test")
            .body("test")
            .build();
    given(notificationConverter.toFcmSendMessage(request))
        .willReturn(new FcmSendMessageDto(targetToken, "test", "test",
            Map.of("type", "medication")));

    FirebaseMessagingException fcmException = mock(FirebaseMessagingException.class);
    given(fcmException.getMessagingErrorCode()).willReturn(MessagingErrorCode.UNREGISTERED);
    given(fcmException.getMessage()).willReturn("Requested entity was not found.");
    doThrow(new RuntimeException("cleanup transaction failed"))
        .when(deviceTokenCleanupService)
        .deleteInvalidToken(targetToken);

    FirebaseMessaging firebaseMessagingMock = mock(FirebaseMessaging.class);
    given(firebaseMessagingMock.send(any(Message.class))).willThrow(fcmException);

    try (MockedStatic<FirebaseMessaging> mockedStatic = mockStatic(FirebaseMessaging.class)) {
      mockedStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessagingMock);

      boolean result = notificationService.sendNotification(request);

      assertThat(result).isFalse();
      assertThat(cleanupMetricCount("failure")).isEqualTo(1.0);
    }
  }

  @Test
  @DisplayName("일시적 FCM 오류면 false를 반환하고 토큰은 삭제하지 않는다")
  void sendNotification_ShouldNotDeleteToken_WhenTransientFcmError()
      throws FirebaseMessagingException {
    String targetToken = "valid_token_xyz";
    FcmNotificationRequestDto request =
        FcmNotificationRequestDto.builder()
            .deviceToken(targetToken)
            .notificationType(NotificationType.STREAK_ACHIEVEMENT)
            .title("test")
            .body("test")
            .build();
    given(notificationConverter.toFcmSendMessage(request))
        .willReturn(new FcmSendMessageDto(targetToken, "test", "test",
            Map.of("type", "streak_achievement")));

    FirebaseMessagingException fcmException = mock(FirebaseMessagingException.class);
    given(fcmException.getMessagingErrorCode()).willReturn(MessagingErrorCode.INTERNAL);
    given(fcmException.getMessage()).willReturn("Internal error.");

    FirebaseMessaging firebaseMessagingMock = mock(FirebaseMessaging.class);
    given(firebaseMessagingMock.send(any(Message.class))).willThrow(fcmException);

    try (MockedStatic<FirebaseMessaging> mockedStatic = mockStatic(FirebaseMessaging.class)) {
      mockedStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessagingMock);

      boolean result = notificationService.sendNotification(request);

      assertThat(result).isFalse();
      verify(deviceTokenCleanupService, never()).deleteInvalidToken(any());
    }
  }

  @Test
  @DisplayName("Connection 에러 시 1회 재시도하여 성공하면 true를 반환한다")
  void sendNotification_ShouldRetryOnce_WhenConnectionError_AndSucceedOnRetry()
      throws FirebaseMessagingException {
    // 준비
    String targetToken = "valid_token_123";
    FcmNotificationRequestDto request =
        FcmNotificationRequestDto.builder()
            .deviceToken(targetToken)
            .notificationType(NotificationType.MEDICATION)
            .title("test")
            .body("test")
            .build();
    given(notificationConverter.toFcmSendMessage(request))
        .willReturn(new FcmSendMessageDto(targetToken, "test", "test",
            Map.of("type", "medication")));

    ConnectionClosedException connClosedEx =
        new ConnectionClosedException("Connection is closed");
    ExecutionException execEx = new ExecutionException(connClosedEx);
    FirebaseMessagingException fcmException = mock(FirebaseMessagingException.class);
    given(fcmException.getMessagingErrorCode()).willReturn(null);
    given(fcmException.getCause()).willReturn(execEx);
    given(fcmException.getMessage()).willReturn("Unknown error");

    FirebaseMessaging firebaseMessagingMock = mock(FirebaseMessaging.class);
    given(firebaseMessagingMock.send(any(Message.class)))
        .willThrow(fcmException)
        .willReturn("projects/test/messages/123");

    try (MockedStatic<FirebaseMessaging> mockedStatic = mockStatic(FirebaseMessaging.class)) {
      mockedStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessagingMock);

      // 실행
      boolean result = notificationService.sendNotification(request);

      // 검증
      assertThat(result).isTrue();
      verify(firebaseMessagingMock, times(2)).send(any(Message.class));
      verify(deviceTokenCleanupService, never()).deleteInvalidToken(any());
    }
  }

  @Test
  @DisplayName("Connection 에러 재시도도 실패하면 false를 반환한다")
  void sendNotification_ShouldReturnFalse_WhenConnectionError_AndRetryAlsoFails()
      throws FirebaseMessagingException {
    // 준비
    String targetToken = "valid_token_456";
    FcmNotificationRequestDto request =
        FcmNotificationRequestDto.builder()
            .deviceToken(targetToken)
            .notificationType(NotificationType.MEDICATION)
            .title("test")
            .body("test")
            .build();
    given(notificationConverter.toFcmSendMessage(request))
        .willReturn(new FcmSendMessageDto(targetToken, "test", "test",
            Map.of("type", "medication")));

    ConnectionClosedException connClosedEx =
        new ConnectionClosedException("Connection is closed");
    FirebaseMessagingException firstException = mock(FirebaseMessagingException.class);
    given(firstException.getMessagingErrorCode()).willReturn(null);
    given(firstException.getCause()).willReturn(new ExecutionException(connClosedEx));
    given(firstException.getMessage()).willReturn("Unknown error");

    FirebaseMessagingException retryException = mock(FirebaseMessagingException.class);
    given(retryException.getMessagingErrorCode()).willReturn(null);
    given(retryException.getCause()).willReturn(
        new ExecutionException(new ConnectionClosedException("Still closed")));
    given(retryException.getMessage()).willReturn("Unknown error on retry");

    FirebaseMessaging firebaseMessagingMock = mock(FirebaseMessaging.class);
    given(firebaseMessagingMock.send(any(Message.class)))
        .willThrow(firstException)
        .willThrow(retryException);

    try (MockedStatic<FirebaseMessaging> mockedStatic = mockStatic(FirebaseMessaging.class)) {
      mockedStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessagingMock);

      // 실행
      boolean result = notificationService.sendNotification(request);

      // 검증
      assertThat(result).isFalse();
      verify(firebaseMessagingMock, times(2)).send(any(Message.class));
    }
  }

  @Test
  @DisplayName("errorCode가 null이어도 ConnectionClosedException이 아니면 재시도하지 않는다")
  void sendNotification_ShouldNotRetry_WhenNullErrorCodeButNotConnectionError()
      throws FirebaseMessagingException {
    // 준비
    String targetToken = "valid_token_789";
    FcmNotificationRequestDto request =
        FcmNotificationRequestDto.builder()
            .deviceToken(targetToken)
            .notificationType(NotificationType.MEDICATION)
            .title("test")
            .body("test")
            .build();
    given(notificationConverter.toFcmSendMessage(request))
        .willReturn(new FcmSendMessageDto(targetToken, "test", "test",
            Map.of("type", "medication")));

    FirebaseMessagingException fcmException = mock(FirebaseMessagingException.class);
    given(fcmException.getMessagingErrorCode()).willReturn(null);
    given(fcmException.getCause()).willReturn(new IOException("Some IO error"));
    given(fcmException.getMessage()).willReturn("Unknown error");

    FirebaseMessaging firebaseMessagingMock = mock(FirebaseMessaging.class);
    given(firebaseMessagingMock.send(any(Message.class))).willThrow(fcmException);

    try (MockedStatic<FirebaseMessaging> mockedStatic = mockStatic(FirebaseMessaging.class)) {
      mockedStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessagingMock);

      // 실행
      boolean result = notificationService.sendNotification(request);

      // 검증
      assertThat(result).isFalse();
      verify(firebaseMessagingMock, times(1)).send(any(Message.class));
    }
  }

  private double cleanupMetricCount(String result) {
    return meterRegistry
        .find("chatbot.fcm.token_cleanup.total")
        .tag("result", result)
        .counter()
        .count();
  }
}
