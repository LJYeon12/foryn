package com.forA.chatbot.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import io.sentry.spring.boot.jakarta.SentryAutoConfiguration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SentryConfigTest {

  private final SentryConfig sentryConfig = new SentryConfig();
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(SentryAutoConfiguration.class))
          .withUserConfiguration(SentryConfig.class)
          .withPropertyValues("sentry.dsn=https://public@example.com/1");

  @Test
  @DisplayName("filters expected medication log duplicate-key Hibernate event")
  void filterMedicationLogDuplicateKeyEvent() {
    SentryOptions.BeforeSendCallback callback = configuredBeforeSend();
    SentryEvent event =
        sentryEvent(
            "org.hibernate.engine.jdbc.spi.SqlExceptionHelper",
            "Duplicate entry '2026-04-29-56' for key 'medication_logs.UK_DATE_BUNDLE'");

    SentryEvent result = callback.execute(event, new Hint());

    assertThat(result).isNull();
  }

  @Test
  @DisplayName("keeps unrelated Hibernate SQL errors")
  void keepUnrelatedHibernateSqlError() {
    SentryOptions.BeforeSendCallback callback = configuredBeforeSend();
    SentryEvent event =
        sentryEvent(
            "org.hibernate.engine.jdbc.spi.SqlExceptionHelper",
            "Cannot create PoolableConnectionFactory");

    SentryEvent result = callback.execute(event, new Hint());

    assertThat(result).isSameAs(event);
  }

  @Test
  @DisplayName("keeps duplicate-key events from other constraints")
  void keepOtherDuplicateKeyEvents() {
    SentryOptions.BeforeSendCallback callback = configuredBeforeSend();
    SentryEvent event =
        sentryEvent(
            "org.hibernate.engine.jdbc.spi.SqlExceptionHelper",
            "Duplicate entry 'token' for key 'device_tokens.uk_device_tokens_token'");

    SentryEvent result = callback.execute(event, new Hint());

    assertThat(result).isSameAs(event);
  }

  @Test
  @DisplayName("keeps matching text from non-Hibernate loggers")
  void keepNonHibernateLogger() {
    SentryOptions.BeforeSendCallback callback = configuredBeforeSend();
    SentryEvent event =
        sentryEvent(
            "com.forA.chatbot.medications.service.MedicationService",
            "Duplicate entry '2026-04-29-56' for key 'medication_logs.UK_DATE_BUNDLE'");

    SentryEvent result = callback.execute(event, new Hint());

    assertThat(result).isSameAs(event);
  }

  @Test
  @DisplayName("preserves an existing beforeSend callback for events that are not filtered")
  void preserveExistingBeforeSendCallback() {
    SentryOptions options = new SentryOptions();
    AtomicBoolean existingCallbackCalled = new AtomicBoolean(false);
    options.setBeforeSend(
        (event, hint) -> {
          existingCallbackCalled.set(true);
          return event;
        });

    sentryConfig.medicationLogDuplicateEventFilter().configure(options);
    SentryEvent event =
        sentryEvent(
            "org.hibernate.engine.jdbc.spi.SqlExceptionHelper",
            "Cannot create PoolableConnectionFactory");

    SentryEvent result = options.getBeforeSend().execute(event, new Hint());

    assertThat(result).isSameAs(event);
    assertThat(existingCallbackCalled).isTrue();
  }

  @Test
  @DisplayName("starts with Sentry auto-configuration without bean conflicts")
  void startsWithSentryAutoConfiguration() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasBean("medicationLogDuplicateEventFilter");
        });
  }

  private SentryOptions.BeforeSendCallback configuredBeforeSend() {
    SentryOptions options = new SentryOptions();
    sentryConfig.medicationLogDuplicateEventFilter().configure(options);
    return options.getBeforeSend();
  }

  private SentryEvent sentryEvent(String logger, String formattedMessage) {
    Message message = new Message();
    message.setFormatted(formattedMessage);

    SentryEvent event = new SentryEvent();
    event.setLogger(logger);
    event.setMessage(message);
    return event;
  }
}
