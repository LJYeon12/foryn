package com.forA.chatbot.notification.service;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.NotificationHandler;
import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.notification.converter.NotificationConverter;
import com.forA.chatbot.notification.domain.DeviceToken;
import com.forA.chatbot.notification.dto.FcmNotificationRequestDto;
import com.forA.chatbot.notification.dto.FcmSendMessageDto;
import com.forA.chatbot.notification.dto.TokenRefreshRequestDto;
import com.forA.chatbot.notification.repository.DeviceTokenRepository;
import com.forA.chatbot.user.domain.User;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.apache.hc.core5.http.ConnectionClosedException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

  private final DeviceTokenRepository deviceTokenRepository;
  private final DeviceTokenCleanupService deviceTokenCleanupService;
  private final UserRepository userRepository;
  private final MeterRegistry meterRegistry;
  private final NotificationConverter notificationConverter;

  @Transactional
  public void syncDeviceToken(Long userId, TokenRefreshRequestDto dto) {
    User user = userRepository.findById(userId).orElse(null);

    deviceTokenRepository.findByDeviceToken(dto.getToken())
        .ifPresentOrElse(
            deviceToken -> {
              if (dto.getIsPushEnabled() != null) {
                deviceToken.updatePushEnabled(dto.getIsPushEnabled());
              }
              deviceToken.updateUser(user);
              log.info("기존 토큰 상태 업데이트 완료: {}", dto.getIsPushEnabled());
            },
            () -> {
              DeviceToken newToken =
                  DeviceToken.builder()
                      .user(user)
                      .deviceToken(dto.getToken())
                      .isPushEnabled(dto.getIsPushEnabled())
                      .build();
              deviceTokenRepository.save(newToken);
              log.info("새로운 토큰 등록 완료");
            });
  }

  @Transactional
  public void sendChatReminderNotification(Long userId, String skillName) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new NotificationHandler(ErrorStatus.USER_NOT_FOUND));

    String nickname = user.getNickname() != null ? user.getNickname() : "사용자";

    List<DeviceToken> enabledTokens =
        deviceTokenRepository.findAllByUserIdAndIsPushEnabledTrue(userId);

    for (DeviceToken deviceToken : enabledTokens) {
      FcmNotificationRequestDto request =
          notificationConverter.toChatReminderRequest(deviceToken, nickname);

      sendNotification(request);
    }
  }

  @Transactional
  public void sendStreakAchievementNotification(Long userId) {
    List<DeviceToken> enabledTokens =
        deviceTokenRepository.findAllByUserIdAndIsPushEnabledTrue(userId);

    for (DeviceToken deviceToken : enabledTokens) {
      FcmNotificationRequestDto request =
          notificationConverter.toStreakAchievementRequest(deviceToken);

      sendNotification(request);
    }
  }

  public boolean sendNotification(FcmNotificationRequestDto request) {
    String type = request.getNotificationType().name();
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      doSend(request);
      meterRegistry.counter("chatbot.fcm.send.total", "type", type, "result", "success").increment();
      return true;

    } catch (FirebaseMessagingException e) {
      if (isConnectionError(e)) {
        log.warn("FCM connection 에러 감지, 1회 재시도: {}", e.getMessage());
        try {
          doSend(request);
          meterRegistry.counter("chatbot.fcm.send.total", "type", type, "result", "retry_success").increment();
          return true;
        } catch (FirebaseMessagingException retryEx) {
          log.error("FCM 재시도 실패", retryEx);
          handleSendError(request, retryEx);
          return false;
        }
      }
      handleSendError(request, e);
      return false;

    } catch (Exception e) {
      log.error(
          "FCM 발송 중 예상치 못한 오류: type={}, token={}",
          request.getNotificationType(),
          maskToken(request.getDeviceToken()), e);
      meterRegistry.counter("chatbot.fcm.send.total", "type", type, "result", "failure").increment();
      return false;

    } finally {
      sample.stop(
          Timer.builder("chatbot.fcm.send.duration").tag("type", type).register(meterRegistry));
    }
  }

  private void doSend(FcmNotificationRequestDto request) throws FirebaseMessagingException {
    FcmSendMessageDto sendMessage = notificationConverter.toFcmSendMessage(request);

    Notification notification =
        Notification.builder().setTitle(sendMessage.title()).setBody(sendMessage.body()).build();

    Message message =
        Message.builder()
            .setToken(sendMessage.token())
            .setNotification(notification)
            .putAllData(sendMessage.data())
            .build();

    String response = FirebaseMessaging.getInstance().send(message);

    log.info(
        "FCM 알림 발송 성공: type={}, response={}, token={}",
        request.getNotificationType(),
        response,
        maskToken(request.getDeviceToken()));
  }

  private void handleSendError(FcmNotificationRequestDto request, FirebaseMessagingException e) {
    String type = request.getNotificationType().name();
    log.error(
        "FCM 알림 발송 실패: type={}, token={}, errorCode={}, message={}",
        request.getNotificationType(),
        maskToken(request.getDeviceToken()),
        e.getMessagingErrorCode(),
        e.getMessage());

    if (isInvalidToken(e)) {
      log.warn(
          "유효하지 않은 FCM 토큰 감지. DB에서 자동 제거합니다. token: {}",
          maskToken(request.getDeviceToken()));
      try {
        deviceTokenCleanupService.deleteInvalidToken(request.getDeviceToken());
        meterRegistry.counter("chatbot.fcm.token_cleanup.total", "result", "success").increment();
      } catch (Exception cleanupException) {
        log.error(
            "유효하지 않은 FCM 토큰 정리에 실패했습니다. token={}",
            maskToken(request.getDeviceToken()),
            cleanupException);
        meterRegistry.counter("chatbot.fcm.token_cleanup.total", "result", "failure").increment();
      }
      meterRegistry.counter("chatbot.fcm.send.total", "type", type, "result", "invalid_token").increment();
    } else {
      log.error("기타 FCM 발송 에러 발생. 트랜잭션 보호를 위해 예외는 전파하지 않습니다.", e);
      meterRegistry.counter("chatbot.fcm.send.total", "type", type, "result", "failure").increment();
    }
  }

  private boolean isConnectionError(FirebaseMessagingException e) {
    return e.getMessagingErrorCode() == null
        && hasCause(e, ConnectionClosedException.class);
  }

  private boolean hasCause(Throwable throwable, Class<? extends Throwable> targetType) {
    Throwable cause = throwable.getCause();
    while (cause != null) {
      if (targetType.isInstance(cause)) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  private boolean isInvalidToken(FirebaseMessagingException e) {
    String errorCode = e.getMessagingErrorCode() != null ? e.getMessagingErrorCode().name() : "";

    return errorCode.equals("INVALID_ARGUMENT")
        || errorCode.equals("UNREGISTERED")
        || errorCode.equals("SENDER_ID_MISMATCH");
  }

  private String maskToken(String token) {
    if (token == null || token.length() < 10) {
      return "***";
    }
    return token.substring(0, 10) + "***";
  }
}
