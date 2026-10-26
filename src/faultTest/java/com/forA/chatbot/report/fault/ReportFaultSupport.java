package com.forA.chatbot.report.fault;

import java.net.URI;
import java.util.List;

/** 로컬 전용 입력 경계와 공통 통계 정의. */
final class ReportFaultSupport {
  private ReportFaultSupport() {}

  static String requireJdbc(String value) {
    if (value == null || !value.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]{1,5}/"
        + "report_fault_[a-z0-9_]+\\?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC")) {
      throw new IllegalArgumentException("REPORT_FAULT_JDBC_URL must name a disposable loopback report_fault_* database");
    }
    requirePort(URI.create(value.substring(5)));
    return value;
  }

  static String requireMongo(String value) {
    if (value == null || !value.matches("mongodb://127\\.0\\.0\\.1:[0-9]{1,5}/report_fault_[a-z0-9_]+")) {
      throw new IllegalArgumentException("REPORT_FAULT_MONGO_URI must name a disposable loopback report_fault_* database");
    }
    requirePort(URI.create(value));
    return value;
  }

  private static void requirePort(URI uri) {
    if (uri.getPort() < 1 || uri.getPort() > 65535) {
      throw new IllegalArgumentException("Invalid local database port");
    }
  }

  static double percentile(List<Double> samples, double quantile) {
    if (samples.isEmpty()) {
      return 0;
    }
    List<Double> sorted = samples.stream().sorted().toList();
    return sorted.get(Math.max(0, (int) Math.ceil(quantile * sorted.size()) - 1));
  }
}
