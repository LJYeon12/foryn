package com.forA.chatbot.notification.service;

import com.forA.chatbot.notification.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTokenCleanupService {

  private final DeviceTokenRepository deviceTokenRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int deleteInvalidToken(String deviceToken) {
    int deletedCount = deviceTokenRepository.deleteByDeviceToken(deviceToken);
    log.info("Invalid FCM token cleanup completed: deletedCount={}", deletedCount);
    return deletedCount;
  }
}
