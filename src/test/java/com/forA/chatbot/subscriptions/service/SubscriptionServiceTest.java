package com.forA.chatbot.subscriptions.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.config.apple.AppleStoreSecretGenerator;
import com.forA.chatbot.enums.SubscriptionStatus;
import com.forA.chatbot.subscriptions.client.AppleAppStoreClient;
import com.forA.chatbot.subscriptions.client.AppleAppStoreSandboxClient;
import com.forA.chatbot.subscriptions.converter.SubscriptionConverter;
import com.forA.chatbot.subscriptions.domain.Subscription;
import com.forA.chatbot.subscriptions.dto.DecodedSignedRenewalInfo;
import com.forA.chatbot.subscriptions.dto.DecodedSignedTransactionInfo;
import com.forA.chatbot.subscriptions.dto.SubscriptionResponseDto;
import com.forA.chatbot.subscriptions.dto.SubscriptionStatusResponse;
import com.forA.chatbot.subscriptions.dto.SubscriptionVerifyRequest;
import com.forA.chatbot.subscriptions.repository.SubscriptionRepository;
import com.forA.chatbot.subscriptions.util.AppStoreJwsValidator;
import com.forA.chatbot.user.domain.User;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

        @InjectMocks
        private SubscriptionService subscriptionService;

        @Mock
        private SubscriptionRepository subscriptionRepository;

        @Mock
        private SubscriptionSaver subscriptionSaver;

        @Mock
        private UserRepository userRepository;

        @Mock
        private AppleAppStoreClient appleAppStoreClient;

        @Mock
        private AppleStoreSecretGenerator appleStoreSecretGenerator;

        @Mock
        private AppStoreJwsValidator jwsValidator;

        @Mock
        private AppleAppStoreSandboxClient appleAppStoreSandboxClient;

        @Mock
        private SubscriptionConverter subscriptionConverter;

        @Test
        @DisplayName("🔴 Step 1: 중복 결제(DataIntegrityViolationException) 발생 시 에러 없이 기존 데이터를 반환해야 한다 (멱등성 방어)")
        void verifySubscription_ConcurrentDuplicateInsert_ShouldReturnExistingData_WithoutError() throws Exception {
                // 준비
                Long userId = 1L;
                String transactionId = "oti_1234567";
                SubscriptionVerifyRequest request = new SubscriptionVerifyRequest();
                org.springframework.test.util.ReflectionTestUtils.setField(request, "originalTransactionId",
                                transactionId);

                User user = User.builder().id(userId).build();

                // 애플 API 인증 모의 설정
                when(appleStoreSecretGenerator.generate()).thenReturn("mock.jwt.token");

                // 애플 응답 데이터 모의 설정
                SubscriptionStatusResponse.LastTransaction lastTx = new SubscriptionStatusResponse.LastTransaction();
                org.springframework.test.util.ReflectionTestUtils.setField(lastTx, "signedTransactionInfo",
                                "signedTxInfo");
                org.springframework.test.util.ReflectionTestUtils.setField(lastTx, "signedRenewalInfo",
                                "signedRenewInfo");
                org.springframework.test.util.ReflectionTestUtils.setField(lastTx, "status", 1);

                SubscriptionStatusResponse.SubscriptionGroupData groupData = new SubscriptionStatusResponse.SubscriptionGroupData();
                org.springframework.test.util.ReflectionTestUtils.setField(groupData, "lastTransactions",
                                Collections.singletonList(lastTx));

                SubscriptionStatusResponse appleResponse = new SubscriptionStatusResponse();
                org.springframework.test.util.ReflectionTestUtils.setField(appleResponse, "data",
                                Collections.singletonList(groupData));

                when(appleAppStoreClient.getAllSubscriptionStatuses(anyString(), anyString()))
                                .thenReturn(appleResponse);

                // JWS 검증기 모의 설정
                DecodedSignedTransactionInfo decodedTx = new DecodedSignedTransactionInfo();
                org.springframework.test.util.ReflectionTestUtils.setField(decodedTx, "productId",
                                "com.foryn.premium.monthly");
                org.springframework.test.util.ReflectionTestUtils.setField(decodedTx, "transactionId", "tx_98765");
                org.springframework.test.util.ReflectionTestUtils.setField(decodedTx, "originalTransactionId",
                                transactionId);
                org.springframework.test.util.ReflectionTestUtils.setField(decodedTx, "expiresDate",
                                System.currentTimeMillis() + 86400000);

                DecodedSignedRenewalInfo decodedRenew = new DecodedSignedRenewalInfo();
                org.springframework.test.util.ReflectionTestUtils.setField(decodedRenew, "autoRenewStatus", 1);

                when(jwsValidator.decodeSignedTransaction("signedTxInfo")).thenReturn(decodedTx);
                when(jwsValidator.decodeRenewalInfo("signedRenewInfo")).thenReturn(decodedRenew);
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));

                // 멱등성 방어 후 다시 조회했을 때 반환해줄 (먼저 저장된) 데이터
                Subscription alreadySavedSub = Subscription.builder()
                                .user(user)
                                .originalTransactionId(transactionId)
                                .build();
                alreadySavedSub.updateFromApple("com.foryn.premium.monthly", "tx_98765",
                                LocalDateTime.now().plusDays(30), true,
                                false, SubscriptionStatus.ACTIVE);

                // 1차 조회는 빈 값, 2차 조회(예외 처리 안)는 이미 저장된 데이터 반환
                when(subscriptionRepository.findByOriginalTransactionId(transactionId))
                                .thenReturn(Optional.empty())
                                .thenReturn(Optional.of(alreadySavedSub));

                // saveNewSubscription() 호출 시 고유 제약조건 충돌 예외를 가정 (동시 요청 중 늦게 도착한 요청)
                when(subscriptionSaver.saveNewSubscription(any(Subscription.class)))
                                .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

                when(subscriptionConverter.toSubscriptionResponseDto(alreadySavedSub))
                                .thenReturn(SubscriptionResponseDto.builder()
                                                .productId("com.foryn.premium.monthly")
                                                .status(SubscriptionStatus.ACTIVE)
                                                .build());

                // 실행 및 검증
                SubscriptionResponseDto responseDto = subscriptionService.verifySubscription(userId, request);

                // AssertJ 검증
                assertThat(responseDto).isNotNull();
                assertThat(responseDto.getProductId()).isEqualTo("com.foryn.premium.monthly");
                assertThat(responseDto.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        }

        @Test
        @DisplayName("새로운 OTI로 영수증 검증 시, 유저의 기존 구독이 있으면 새로운 엔티티를 생성하지 않고 기존 엔티티를 업데이트해야 한다")
        void verifySubscription_WhenUserHasExistingSubscription_ShouldUpdateInsteadOfCreate() throws Exception {
                // 준비
                Long userId = 2L;
                String oldOti = "old_oti_111";
                String newOti = "new_oti_222";
                SubscriptionVerifyRequest request = new SubscriptionVerifyRequest();
                org.springframework.test.util.ReflectionTestUtils.setField(request, "originalTransactionId", newOti);

                User user = User.builder().id(userId).build();

                Subscription existingSubscription = Subscription.builder()
                                .user(user)
                                .originalTransactionId(oldOti)
                                .build();
                org.springframework.test.util.ReflectionTestUtils.setField(existingSubscription, "id", 999L);

                when(appleStoreSecretGenerator.generate()).thenReturn("mock.jwt");

                SubscriptionStatusResponse.LastTransaction lastTx = new SubscriptionStatusResponse.LastTransaction();
                org.springframework.test.util.ReflectionTestUtils.setField(lastTx, "signedTransactionInfo",
                                "signedTxInfo");
                org.springframework.test.util.ReflectionTestUtils.setField(lastTx, "signedRenewalInfo",
                                "signedRenewInfo");
                org.springframework.test.util.ReflectionTestUtils.setField(lastTx, "status", 1);

                SubscriptionStatusResponse.SubscriptionGroupData groupData = new SubscriptionStatusResponse.SubscriptionGroupData();
                org.springframework.test.util.ReflectionTestUtils.setField(groupData, "lastTransactions",
                                Collections.singletonList(lastTx));

                SubscriptionStatusResponse appleResponse = new SubscriptionStatusResponse();
                org.springframework.test.util.ReflectionTestUtils.setField(appleResponse, "data",
                                Collections.singletonList(groupData));

                when(appleAppStoreClient.getAllSubscriptionStatuses(anyString(), anyString()))
                                .thenReturn(appleResponse);

                DecodedSignedTransactionInfo decodedTx = new DecodedSignedTransactionInfo();
                org.springframework.test.util.ReflectionTestUtils.setField(decodedTx, "productId",
                                "com.foryn.premium.monthly");
                org.springframework.test.util.ReflectionTestUtils.setField(decodedTx, "transactionId", "tx_new");
                org.springframework.test.util.ReflectionTestUtils.setField(decodedTx, "originalTransactionId", newOti);
                org.springframework.test.util.ReflectionTestUtils.setField(decodedTx, "expiresDate",
                                System.currentTimeMillis() + 86400000);

                DecodedSignedRenewalInfo decodedRenew = new DecodedSignedRenewalInfo();
                org.springframework.test.util.ReflectionTestUtils.setField(decodedRenew, "autoRenewStatus", 1);

                when(jwsValidator.decodeSignedTransaction("signedTxInfo")).thenReturn(decodedTx);
                when(jwsValidator.decodeRenewalInfo("signedRenewInfo")).thenReturn(decodedRenew);
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));

                when(subscriptionRepository.findByOriginalTransactionId(newOti)).thenReturn(Optional.empty());
                when(subscriptionRepository.findByUser_Id(userId)).thenReturn(Optional.of(existingSubscription));

                when(subscriptionSaver.saveNewSubscription(any(Subscription.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                when(subscriptionConverter.toSubscriptionResponseDto(any(Subscription.class)))
                                .thenReturn(SubscriptionResponseDto.builder()
                                                .subscriptionId(999L)
                                                .productId("com.foryn.premium.monthly")
                                                .status(SubscriptionStatus.ACTIVE)
                                                .build());

                // 실행
                SubscriptionResponseDto responseDto = subscriptionService.verifySubscription(userId, request);

                // 검증
                assertThat(responseDto.getSubscriptionId()).isEqualTo(999L);
                org.mockito.Mockito.verify(subscriptionRepository).findByUser_Id(userId);
        }

        @Test
        @DisplayName("Webhook 알림 수신 시 DB에 해당 OTI가 없으면 500 예외를 던지지 않고 정상 종료해야 한다")
        void handleAppleWebhook_WhenOtiNotFound_ShouldNotThrowException() throws Exception {
                // 준비
                String mockPayload = "mockSignedPayload";
                com.forA.chatbot.subscriptions.dto.DecodedNotificationPayload mockNotification = new com.forA.chatbot.subscriptions.dto.DecodedNotificationPayload();
                org.springframework.test.util.ReflectionTestUtils.setField(mockNotification, "notificationType",
                                "PRICE_INCREASE");

                com.forA.chatbot.subscriptions.dto.NotificationDataPayload mockData = new com.forA.chatbot.subscriptions.dto.NotificationDataPayload();
                org.springframework.test.util.ReflectionTestUtils.setField(mockData, "signedTransactionInfo",
                                "signedTx");
                org.springframework.test.util.ReflectionTestUtils.setField(mockData, "signedRenewalInfo", "signedRn");
                org.springframework.test.util.ReflectionTestUtils.setField(mockNotification, "data", mockData);

                DecodedSignedTransactionInfo mockTx = new DecodedSignedTransactionInfo();
                org.springframework.test.util.ReflectionTestUtils.setField(mockTx, "originalTransactionId",
                                "unknown_oti");

                DecodedSignedRenewalInfo mockRn = new DecodedSignedRenewalInfo();

                when(jwsValidator.decodeNotificationPayload(anyString())).thenReturn(mockNotification);
                when(jwsValidator.decodeSignedTransaction(anyString())).thenReturn(mockTx);
                when(jwsValidator.decodeRenewalInfo(anyString())).thenReturn(mockRn);
                when(subscriptionRepository.findByOriginalTransactionId("unknown_oti")).thenReturn(Optional.empty());

                // 실행 및 검증
                org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
                        subscriptionService.handleAppleWebhook(mockPayload);
                }, "Apple 재시도 폭탄을 방지하기 위해, 존재하지 않는 OTI의 Webhook은 500 에러를 뱉지 않아야 합니다.");
        }
}
