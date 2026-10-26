package com.forA.chatbot.subscriptions.service;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.SubscriptionHandler;
import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.config.apple.AppleStoreSecretGenerator;
import com.forA.chatbot.enums.SubscriptionStatus;
import com.forA.chatbot.subscriptions.client.AppleAppStoreClient;
import com.forA.chatbot.subscriptions.client.AppleAppStoreSandboxClient;
import com.forA.chatbot.subscriptions.converter.SubscriptionConverter;
import com.forA.chatbot.subscriptions.domain.Subscription;
import com.forA.chatbot.subscriptions.dto.DecodedNotificationPayload;
import com.forA.chatbot.subscriptions.dto.DecodedSignedRenewalInfo;
import com.forA.chatbot.subscriptions.dto.DecodedSignedTransactionInfo;
import com.forA.chatbot.subscriptions.dto.GetSubscriptionResponse;
import com.forA.chatbot.subscriptions.dto.NotificationDataPayload;
import com.forA.chatbot.subscriptions.dto.SubscriptionResponseDto;
import com.forA.chatbot.subscriptions.dto.SubscriptionStatusResponse;
import com.forA.chatbot.subscriptions.dto.SubscriptionStatusResponse.LastTransaction;
import com.forA.chatbot.subscriptions.dto.SubscriptionVerifyRequest;
import com.forA.chatbot.subscriptions.repository.SubscriptionRepository;
import com.forA.chatbot.subscriptions.util.AppStoreJwsValidator;
import com.forA.chatbot.user.domain.User;
import feign.FeignException;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {
  private final AppleAppStoreClient appleAppStoreClient;
  private final AppleStoreSecretGenerator appleStoreSecretGenerator;
  private final AppStoreJwsValidator jwsValidator;
  private final UserRepository userRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final AppleAppStoreSandboxClient appleAppStoreSandboxClient;
  private final SubscriptionSaver subscriptionSaver;
  private final SubscriptionConverter subscriptionConverter;

  /**
   * iOS 앱이 보낸 영수증 검증 후 DB에 구독 정보 생성·갱신
   */
  @Transactional
  public SubscriptionResponseDto verifySubscription(Long userId, SubscriptionVerifyRequest request) {
    String transactionId = request.getOriginalTransactionId();
    log.info("영수증 검증 시작 - userId = {}, transactionId = {}", userId, transactionId);

    // 애플 서버 인증용 JWT 생성
    String serverToServerJwt;
    try {
      serverToServerJwt = appleStoreSecretGenerator.generate();
      log.info("Apple API 토큰 생성 완료");
    } catch (Exception e) {
      log.error("Apple Client Secret (JWT) 생성 실패", e);
      throw new SubscriptionHandler(ErrorStatus.IAP_APPLE_API_CALL_FAILED);
    }
    String bearerToken = "Bearer " + serverToServerJwt;

    // 애플 서버와 통신
    SubscriptionStatusResponse appleResponse;
    try {
      appleResponse = appleAppStoreClient.getAllSubscriptionStatuses(bearerToken, transactionId);
      log.info("Apple Production API 호출 성공 - userId: {}", userId);
    } catch (FeignException.NotFound | FeignException.Unauthorized e) { // 404 또는 401 예외 발생 시
      log.warn("Production API에서 404 Not Found. Sandbox API로 재시도합니다. OTI: {}", transactionId);
      try {
        appleResponse = appleAppStoreSandboxClient.getAllSubscriptionStatuses(
            bearerToken, transactionId);
        log.debug("Apple Sandbox API 호출 성공");
      } catch (Exception sandboxE) {
        log.error("Apple Sandbox API 호출 실패", sandboxE);
        throw new SubscriptionHandler(ErrorStatus.IAP_APPLE_API_CALL_FAILED);
      }
    } catch (Exception e) {
      log.error("Apple App Store API 호출 실패", e);
      throw new SubscriptionHandler(ErrorStatus.IAP_APPLE_API_CALL_FAILED);
    }
    // 4. 응답에서 JWS 추출
    LastTransaction lastTransaction = extractLastTransaction(appleResponse, transactionId);

    String signedTransactionInfo = lastTransaction.getSignedTransactionInfo();
    String signedRenewalInfo = lastTransaction.getSignedRenewalInfo();
    Integer appleStatus = lastTransaction.getStatus();

    // 5. JWS 검증 및 디코딩
    log.debug("JWS 디코딩 시작...");
    DecodedSignedTransactionInfo transactionInfo = jwsValidator.decodeSignedTransaction(signedTransactionInfo);
    DecodedSignedRenewalInfo renewalInfo = jwsValidator.decodeRenewalInfo(signedRenewalInfo);
    log.debug("JWS 디코딩 성공: productId={}", transactionInfo.getProductId());

    User user = userRepository.findById(userId)
        .orElseThrow(() -> new SubscriptionHandler(ErrorStatus.USER_NOT_FOUND));
    // 6. OTI → userId 순서로 기존 구독 조회 (1유저=1구독 정책)
    Subscription subscription = findOrCreateSubscription(transactionId, userId, user);

    subscription.updateFromApple(
        transactionInfo.getProductId(),
        transactionInfo.getTransactionId(),
        transactionInfo.getExpiresDateAsLocalDateTime(),
        renewalInfo.getAutoRenewStatus() == 1,
        transactionInfo.isTrial(),
        convertAppleStatus(appleStatus));

    Subscription savedSubscription;
    try {
      // 1. 새로운 트랜잭션에서 저장을 시도합니다.
      savedSubscription = subscriptionSaver.saveNewSubscription(subscription);
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      // 2. 다른 인스턴스가 불과 몇 밀리초 차이로 먼저 INSERT를 성공한 상황 (DB 제약조건 튕김)
      log.warn("이미 동일한 Transaction ID로 구독 정보가 생성되었습니다. 기존 데이터를 반환합니다. OTI: {}", transactionId);
      savedSubscription = subscriptionRepository.findByOriginalTransactionId(transactionId)
          .orElseThrow(() -> new SubscriptionHandler(ErrorStatus.IAP_SUBSCRIPTION_NOT_FOUND));
    }

    log.info("구독 상태 DB 저장 완료. User: {}, Status: {}, ExpiresAt: {}",
        userId, savedSubscription.getStatus(), savedSubscription.getExpiresAt());
    // 7. 클라이언트에 보낼 응답 생성
    return subscriptionConverter.toSubscriptionResponseDto(savedSubscription);
  }

  /**
   * Apple 응답에서 최신 트랜잭션을 안전하게 추출하는 헬퍼 메서드
   */
  private LastTransaction extractLastTransaction(SubscriptionStatusResponse response, String originalTransactionId) {
    if (response == null || response.getData() == null || response.getData().isEmpty()) {
      log.warn("Apple 응답에 [data] 필드가 비어있습니다. originalTransactionId: {}", originalTransactionId);
      throw new SubscriptionHandler(ErrorStatus.IAP_APPLE_INVALID_TRANSACTION);
    }
    // getData().get(0) -> Mori 앱의 단일 구독 그룹
    SubscriptionStatusResponse.SubscriptionGroupData groupData = response.getData().get(0);
    if (groupData == null || groupData.getLastTransactions() == null || groupData.getLastTransactions().isEmpty()) {
      log.warn("Apple 응답에 [lastTransactions] 필드가 비어있습니다. originalTransactionId: {}", originalTransactionId);
      throw new SubscriptionHandler(ErrorStatus.IAP_APPLE_INVALID_TRANSACTION);
    }
    SubscriptionStatusResponse.LastTransaction lastTransaction = groupData.getLastTransactions().get(0);

    if (lastTransaction.getSignedTransactionInfo() == null || lastTransaction.getSignedRenewalInfo() == null) {
      log.error("JWS 데이터가 null입니다. DTO의 'signedTransactionInfo' 필드명이 Apple JSON 키와 일치하는지 확인하세요.");
      throw new SubscriptionHandler(ErrorStatus.IAP_APPLE_INVALID_TRANSACTION);
    }
    return lastTransaction;
  }

  /**
   * 애플의 숫자 상태를 내부 열거형으로 변환하는 보조 메서드 (이전 단계와 동일)
   */
  private SubscriptionStatus convertAppleStatus(Integer appleStatus) {
    // ... (이전 단계 6-4와 동일)
    if (appleStatus == null) {
      return SubscriptionStatus.EXPIRED;
    }
    switch (appleStatus) {
      case 1:
        return SubscriptionStatus.ACTIVE;
      case 2:
        return SubscriptionStatus.EXPIRED;
      case 3:
        return SubscriptionStatus.ACTIVE; // (결제 재시도)
      case 4:
        return SubscriptionStatus.ACTIVE; // (유예 기간)
      case 5:
        return SubscriptionStatus.CANCELLED; // (권한 회수)
      default:
        return SubscriptionStatus.EXPIRED;
    }
  }

  /**
   * 알림 처리 & 구독 상태 실시간 업데이트
   */
  @Transactional
  public void handleAppleWebhook(String signedPayload) {
    if (signedPayload == null || signedPayload.isEmpty()) {
      log.warn("[Webhook] signedPayload가 비어있습니다.");
      return;
    }
    try {
      // 1. "바깥쪽" JWS 해독 (알림 타입 확인)
      DecodedNotificationPayload notification = jwsValidator.decodeNotificationPayload(signedPayload);
      String notificationType = notification.getNotificationType();
      log.info("[Webhook] 알림 수신: Type = {}", notificationType);
      // 2. "안쪽" JWS 해독 (실제 거래 정보)
      NotificationDataPayload data = notification.getData();
      if (data == null || data.getSignedTransactionInfo() == null || data.getSignedRenewalInfo() == null) {
        log.warn("[Webhook] 알림 내부에 data 또는 JWS 정보가 없습니다. Type = {}", notificationType);
        return;
      }
      DecodedSignedTransactionInfo transactionInfo = jwsValidator.decodeSignedTransaction(
          data.getSignedTransactionInfo());
      DecodedSignedRenewalInfo renewalInfo = jwsValidator.decodeRenewalInfo(
          data.getSignedRenewalInfo());

      // 3. DB에서 구독 정보 조회 (웹훅: 기존 구독자의 변경 알림)
      String originalTransactionId = transactionInfo.getOriginalTransactionId();
      Optional<Subscription> optionalSubscription = subscriptionRepository.findByOriginalTransactionId(
          originalTransactionId);
      if (optionalSubscription.isEmpty()) {
        log.warn("[Webhook] DB에 해당 OTI 없음. 정상 종료. OTI: {}", originalTransactionId);
        return;
      }
      Subscription subscription = optionalSubscription.get();
      // 4. 알람 타입에 따라 DB 상태 업데이트
      switch (notificationType) {
        case "DID_RENEW": // 자동 갱신 성공
        case "SUBSCRIBED": // 신규 구독 (또는 구독 상품 변경)
          log.info("[Webhook] 구독 갱신/변경. OTI: {}", originalTransactionId);
          subscription.updateFromApple(
              transactionInfo.getProductId(),
              transactionInfo.getTransactionId(),
              transactionInfo.getExpiresDateAsLocalDateTime(),
              renewalInfo.getAutoRenewStatus() == 1, // 1 : 자동 갱신 o, 0 : x
              transactionInfo.isTrial(),
              SubscriptionStatus.ACTIVE // 갱신, 구독 시 무조건 ACTIVE
          );
          break;
        case "EXPIRED": // 구독 만료
          log.info("[Webhook] 구독 만료. OTI: {}", originalTransactionId);
          subscription.updateFromApple(
              subscription.getProductId(),
              transactionInfo.getTransactionId(),
              transactionInfo.getExpiresDateAsLocalDateTime(),
              false,
              subscription.getIsTrial(),
              SubscriptionStatus.EXPIRED // 만료
          );
          break;
        case "REVOKE": // 환불 (Apple 에 의해 강제 취소)
        case "REFUND": // 환불
          log.info("[Webhook] 구독 환불/취소. OTI: {}", originalTransactionId);
          subscription.updateFromApple(
              subscription.getProductId(),
              transactionInfo.getTransactionId(),
              LocalDateTime.now(),
              false,
              subscription.getIsTrial(),
              SubscriptionStatus.CANCELLED);
          break;
        case "DID_CHANGE_RENEWAL_STATUS": // 사용자가 '자동 갱신' 변경
          log.info("[Webhook] 자동 갱신 상태 변경. OTI: {}", originalTransactionId);
          subscription.updateFromApple(
              subscription.getProductId(),
              subscription.getTransactionId(),
              subscription.getExpiresAt(),
              renewalInfo.getAutoRenewStatus() == 1, // 갱신된 상태(아마도 false)
              subscription.getIsTrial(),
              subscription.getStatus() // 현재 상태 유지
          );
          break;
        case "DID_FAIL_TO_RENEW": // 결제 실패
          log.warn("[Webhook] 결제 실패(유예 기간). OTI: {}", originalTransactionId);
          break;
        default:
          log.info("[Webhook] 처리하지 않는 알림 타입 수신: {}", notificationType);
          break;
      }
      subscriptionRepository.save(subscription);
      log.info("[Webhook] DB 업데이트 완료. OTI: {}, NewStatus: {}",
          originalTransactionId, subscription.getStatus());
    } catch (SubscriptionHandler e) {
      log.warn("[Webhook] 비즈니스 예외. 재시도 방지를 위해 정상 종료: {}", e.getMessage());
    } catch (Exception e) {
      log.error("[Webhook] 처리 중 심각한 오류 발생", e);
      throw new SubscriptionHandler(ErrorStatus.IAP_APPLE_API_CALL_FAILED);
    }

  }

  @Transactional(readOnly = true)
  public GetSubscriptionResponse getSubscriptionStatus(Long userId) {
    log.info("DB에서 구독 상태 조회 - userId = {}", userId);
    Subscription subscription = subscriptionRepository.findByUser_Id(userId)
        .orElseThrow(() -> new SubscriptionHandler(ErrorStatus.IAP_SUBSCRIPTION_NOT_FOUND));
    return subscriptionConverter.toGetSubscriptionResponse(subscription);
  }

  /**
   * OTI → userId 순서로 기존 구독을 조회하고, 없으면 신규 엔티티를 생성합니다.
   * 1유저=1구독 정책에 따라, OTI가 변경되었더라도 userId로 기존 레코드를 찾아 OTI를 갱신합니다.
   */
  private Subscription findOrCreateSubscription(String transactionId, Long userId, User user) {
    return subscriptionRepository.findByOriginalTransactionId(transactionId)
        .orElseGet(() -> subscriptionRepository.findByUser_Id(userId)
            .map(existing -> {
              log.info("기존 구독 발견. OTI 갱신: {} -> {}", existing.getOriginalTransactionId(), transactionId);
              existing.updateOriginalTransactionId(transactionId);
              return existing;
            })
            .orElseGet(() -> {
              log.info("완전 신규 구독. DB에 생성: {}", transactionId);
              return Subscription.builder()
                  .user(user)
                  .originalTransactionId(transactionId)
                  .build();
            }));
  }

}
