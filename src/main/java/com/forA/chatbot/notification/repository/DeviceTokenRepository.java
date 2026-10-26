package com.forA.chatbot.notification.repository;

import com.forA.chatbot.notification.domain.DeviceToken;
import com.forA.chatbot.user.domain.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

  // 토큰 문자열로 엔티티 조회
  Optional<DeviceToken> findByDeviceToken(String deviceToken);

  // 토큰만 단건 하드 삭제
  @Modifying
  @Query("DELETE FROM DeviceToken d WHERE d.deviceToken = :deviceToken")
  int deleteByDeviceToken(@Param("deviceToken") String deviceToken);

  // 사용자 ID로 엔티티 조회 (단건)
  Optional<DeviceToken> findByUserId(Long userId);

  // 사용자 ID로 모든 토큰 일괄 삭제
  void deleteAllByUserId(Long userId);

  // 특정 시간 이전에 업데이트된 모든 토큰 삭제 (스케줄러에서 사용)
  void deleteByUpdatedAtBefore(LocalDateTime expiryDate);

  List<DeviceToken> findByUser(User user);

  List<DeviceToken> findByUserAndIsPushEnabledTrue(User user);

  List<DeviceToken> findAllByUserIdAndIsPushEnabledTrue(Long userId);
}
