package com.forA.chatbot.global.time;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

@Component
public class SystemAppDateTimeProvider implements AppDateTimeProvider {

  @Override
  public LocalDate today(String timeZone) {
    return LocalDate.now(ZoneId.of(timeZone));
  }

  @Override
  public YearMonth currentMonth(String timeZone) {
    return YearMonth.now(ZoneId.of(timeZone));
  }
}
