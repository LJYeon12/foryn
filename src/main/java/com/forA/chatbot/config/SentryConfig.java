package com.forA.chatbot.config;

import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SentryConfig {

  private static final String HIBERNATE_SQL_EXCEPTION_LOGGER =
      "org.hibernate.engine.jdbc.spi.SqlExceptionHelper";
  private static final String DUPLICATE_ENTRY_TEXT = "Duplicate entry";
  private static final String MEDICATION_LOG_UNIQUE_KEY = "medication_logs.UK_DATE_BUNDLE";

  @Bean
  public Sentry.OptionsConfiguration<SentryOptions> medicationLogDuplicateEventFilter() {
    return options -> {
      SentryOptions.BeforeSendCallback existingBeforeSend = options.getBeforeSend();

      options.setBeforeSend(
          (event, hint) -> {
            if (shouldDropMedicationLogDuplicateEvent(event)) {
              return null;
            }

            return existingBeforeSend != null ? existingBeforeSend.execute(event, hint) : event;
          });
    };
  }

  private boolean shouldDropMedicationLogDuplicateEvent(SentryEvent event) {
    if (event == null || !HIBERNATE_SQL_EXCEPTION_LOGGER.equals(event.getLogger())) {
      return false;
    }

    String message = extractMessage(event.getMessage());
    return message.contains(DUPLICATE_ENTRY_TEXT)
        && message.contains(MEDICATION_LOG_UNIQUE_KEY);
  }

  private String extractMessage(Message message) {
    if (message == null) {
      return "";
    }

    if (message.getFormatted() != null) {
      return message.getFormatted();
    }

    return message.getMessage() != null ? message.getMessage() : "";
  }
}
