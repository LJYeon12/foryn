package com.forA.chatbot.subscriptions.service;

import com.forA.chatbot.subscriptions.domain.Subscription;
import com.forA.chatbot.subscriptions.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SubscriptionSaver {

    private final SubscriptionRepository subscriptionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Subscription saveNewSubscription(Subscription subscription) {
        return subscriptionRepository.saveAndFlush(subscription);
    }
}
