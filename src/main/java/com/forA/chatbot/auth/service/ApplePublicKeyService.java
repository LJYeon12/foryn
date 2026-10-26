package com.forA.chatbot.auth.service;

import com.forA.chatbot.auth.client.AppleAuthClient;
import com.forA.chatbot.auth.dto.ApplePublicKey;
import com.forA.chatbot.auth.dto.ApplePublicKeyResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApplePublicKeyService {

  private final AppleAuthClient appleAuthClient;

  @Cacheable("applePublicKeys")
  public List<ApplePublicKey> getApplePublicKeys() {
    ApplePublicKeyResponse response = appleAuthClient.getPublicKeys();
    return response.getKeys();
  }
}
