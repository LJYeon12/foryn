package com.forA.chatbot.notification.fault;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forA.chatbot.config.CloudTasksConfig;
import com.forA.chatbot.notification.client.CloudTasksClient;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** 테스트 전용 HTTP 어댑터이며 Cloud Tasks 에뮬레이터나 Google SDK는 사용하지 않는다. */
final class LocalTaskTransport extends CloudTasksClient {
  private final URI endpoint;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  private final ObjectMapper mapper = new ObjectMapper();

  LocalTaskTransport(URI endpoint) {
    super(new CloudTasksConfig());
    requireLoopback(endpoint);
    this.endpoint = endpoint;
  }

  static void requireLoopback(URI endpoint) {
    if (!"http".equals(endpoint.getScheme()) || !"127.0.0.1".equals(endpoint.getHost())
        || endpoint.getPort() < 1 || endpoint.getUserInfo() != null
        || endpoint.getRawQuery() != null || endpoint.getFragment() != null
        || !(endpoint.getPath().isEmpty() || "/".equals(endpoint.getPath()))) {
      throw new IllegalArgumentException("Only an explicit HTTP loopback test server is permitted");
    }
  }

  static String requireDisposableDatabase(String jdbcUrl) {
    if (jdbcUrl == null || !jdbcUrl.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]{1,5}/"
        + "outbox_fault_[a-z0-9_]+\\?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC")) {
      throw new IllegalArgumentException("Set OUTBOX_FAULT_JDBC_URL to a disposable loopback outbox_fault_* database");
    }
    return jdbcUrl;
  }

  @Override
  public void createTask(String taskId, String path, String jsonPayload, Instant scheduleTime) {
    try {
      post("/tasks", mapper.writeValueAsString(Map.of("taskId", taskId, "path", path,
          "payload", mapper.readTree(jsonPayload), "scheduledAt", scheduleTime.toString())), true);
    } catch (IOException e) {
      throw new IllegalStateException("Local registration transport failed", e);
    }
  }

  void push() {
    post("/push", "{}", false);
  }

  private void post(String path, String body, boolean acceptDuplicate) {
    try {
      HttpResponse<Void> result = http.send(HttpRequest.newBuilder(endpoint.resolve(path))
          .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.discarding());
      if (result.statusCode() != 200 && !(acceptDuplicate && result.statusCode() == 409)) {
        throw new IllegalStateException("Local receiver returned HTTP " + result.statusCode());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Local request interrupted", e);
    } catch (IOException e) {
      throw new IllegalStateException("Local request failed", e);
    }
  }
}
