package com.forA.chatbot.global.time;

import java.time.LocalDate;
import java.time.YearMonth;

public interface AppDateTimeProvider {

  LocalDate today(String timeZone);

  YearMonth currentMonth(String timeZone);
}
