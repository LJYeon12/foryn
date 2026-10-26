package com.forA.chatbot.subscriptions.client;

import com.forA.chatbot.subscriptions.dto.SubscriptionStatusResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "apple-appstore-sandbox", url = "https://api.storekit-sandbox.itunes.apple.com")
public interface AppleAppStoreSandboxClient {

  @GetMapping("/inApps/v1/subscriptions/{originalTransactionId}")
  SubscriptionStatusResponse getAllSubscriptionStatuses(
      @RequestHeader("Authorization") String bearerToken,
      @PathVariable("originalTransactionId") String originalTransactionId
  );
}
