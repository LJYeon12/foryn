package com.forA.chatbot.report.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReportMetricsRecorder {

  private static final String REPORT_SEGMENT_DURATION = "chatbot.report.segment.duration";
  private static final String REPORT_SEGMENT_FAILURE = "chatbot.report.segment.failure";

  private final MeterRegistry meterRegistry;

  public <T> T recordSegment(String reportName, String segmentName, Supplier<T> supplier) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      return supplier.get();
    } finally {
      sample.stop(
          Timer.builder(REPORT_SEGMENT_DURATION)
              .tag("report", reportName)
              .tag("segment", segmentName)
              .register(meterRegistry));
    }
  }

  public void recordSegment(String reportName, String segmentName, Runnable runnable) {
    recordSegment(
        reportName,
        segmentName,
        () -> {
          runnable.run();
          return null;
        });
  }

  public void recordSegmentFailure(String reportName, String segmentName, String source) {
    Counter.builder(REPORT_SEGMENT_FAILURE)
        .tag("report", reportName)
        .tag("segment", segmentName)
        .tag("source", source)
        .register(meterRegistry)
        .increment();
  }
}
