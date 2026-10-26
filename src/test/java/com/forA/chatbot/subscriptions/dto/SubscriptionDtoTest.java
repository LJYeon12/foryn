package com.forA.chatbot.subscriptions.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubscriptionDtoTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("NotificationDataPayload에 알 수 없는 필드가 있어도 파싱에 실패하지 않아야 한다")
  void notificationDataPayload_ignoreUnknown() throws JsonProcessingException {
    // 준비
    String json = """
        {
          "bundleId": "com.test.app",
          "environment": "Sandbox",
          "signedTransactionInfo": "eyJ...",
          "signedRenewalInfo": "eyJ...",
          "appAppleId": "123456",
          "newUnknownField": "someValue",
          "anotherUnknown": 123
        }
        """;

    // 실행
    NotificationDataPayload payload = objectMapper.readValue(json, NotificationDataPayload.class);

    // 검증
    assertAll(
        () -> assertThat(payload.getBundleId()).isEqualTo("com.test.app"),
        () -> assertThat(payload.getEnvironment()).isEqualTo("Sandbox"));
  }

  @Test
  @DisplayName("DecodedSignedTransactionInfo에 알 수 없는 필드가 있어도 파싱에 실패하지 않아야 한다")
  void transactionInfo_ignoreUnknown() throws JsonProcessingException {
    // 준비
    String json = """
        {
          "transactionId": "1000000",
          "originalTransactionId": "1000000",
          "productId": "com.test.product",
          "unknownField": "shouldBeIgnored"
        }
        """;

    // 실행
    DecodedSignedTransactionInfo info = objectMapper.readValue(json, DecodedSignedTransactionInfo.class);

    // 검증
    assertThat(info.getTransactionId()).isEqualTo("1000000");
  }

  @Test
  @DisplayName("SubscriptionStatusResponse에 알 수 없는 필드가 있어도 파싱에 실패하지 않아야 한다")
  void statusResponse_ignoreUnknown() throws JsonProcessingException {
    // 준비
    String json = """
        {
          "environment": "Sandbox",
          "appAppleId": 123456,
          "bundleId": "com.test.app",
          "data": [],
          "newRootField": "ignored"
        }
        """;

    // 실행
    SubscriptionStatusResponse response = objectMapper.readValue(json, SubscriptionStatusResponse.class);

    // 검증
    assertThat(response.getEnvironment()).isEqualTo("Sandbox");
  }
}
