package com.forA.chatbot.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.enums.ProviderType;
import com.forA.chatbot.notification.domain.DeviceToken;
import com.forA.chatbot.notification.repository.DeviceTokenRepository;
import com.forA.chatbot.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import(DeviceTokenCleanupService.class)
class DeviceTokenCleanupServiceTest {

  @Autowired private DeviceTokenCleanupService deviceTokenCleanupService;

  @Autowired private DeviceTokenRepository deviceTokenRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("외부 트랜잭션 없이 무효 토큰을 삭제한다")
  void deleteInvalidToken_deletesTokenWithoutAmbientTransaction() {
    String token = "invalid-token-without-ambient-transaction";
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.executeWithoutResult(status -> {
      User user = userRepository.saveAndFlush(User.builder()
          .email("cleanup@example.com")
          .providerUserId("cleanup-provider-user")
          .providerType(ProviderType.APPLE)
          .fullName("Cleanup User")
          .build());
      deviceTokenRepository.saveAndFlush(DeviceToken.builder()
          .user(user)
          .deviceToken(token)
          .isPushEnabled(true)
          .build());
    });

    int deletedCount = deviceTokenCleanupService.deleteInvalidToken(token);

    assertThat(deletedCount).isEqualTo(1);
    assertThat(deviceTokenRepository.findByDeviceToken(token)).isEmpty();
  }
}
