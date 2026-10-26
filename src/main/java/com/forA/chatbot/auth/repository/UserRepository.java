package com.forA.chatbot.auth.repository;

import com.forA.chatbot.enums.ProviderType;
import com.forA.chatbot.user.domain.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByProviderTypeAndProviderUserId(ProviderType providerType,
      String providerUserId);

  Optional<User> findByEmail(String email);

  @Query("SELECT u FROM User u WHERE u.isDeleted = true AND u.deletedAt < :beforeTime")
  List<User> findExpiredSoftDeletedUsers(@Param("beforeTime") LocalDateTime beforeTime);
}
