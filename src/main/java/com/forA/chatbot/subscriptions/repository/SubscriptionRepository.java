package com.forA.chatbot.subscriptions.repository;

import com.forA.chatbot.subscriptions.domain.Subscription;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

  Optional<Subscription> findByOriginalTransactionId(String originalTransactionId);
  Optional<Subscription> findByUser_Id(Long userId);
}
