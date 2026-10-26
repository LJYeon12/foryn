package com.forA.chatbot.notification.client;

import com.forA.chatbot.config.CloudTasksConfig;
import com.google.cloud.tasks.v2.HttpMethod;
import com.google.cloud.tasks.v2.HttpRequest;
import com.google.cloud.tasks.v2.OidcToken;
import com.google.cloud.tasks.v2.QueueName;
import com.google.cloud.tasks.v2.Task;
import com.google.cloud.tasks.v2.TaskName;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import java.io.IOException;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CloudTasksClient {

  private final CloudTasksConfig config;

  public CloudTasksClient(CloudTasksConfig config) {
    this.config = config;
  }

  public void createTask(String taskId, String path, String jsonPayload, Instant scheduleTime) {
    QueueName queueName = QueueName.of(
        config.getProjectId(), config.getLocationId(), config.getQueueId());

    String targetUrl = config.getTargetBaseUrl() + path;

    HttpRequest httpRequest = HttpRequest.newBuilder()
        .setUrl(targetUrl)
        .setHttpMethod(HttpMethod.POST)
        .putHeaders("Content-Type", "application/json")
        .setBody(ByteString.copyFromUtf8(jsonPayload))
        .setOidcToken(OidcToken.newBuilder()
            .setServiceAccountEmail(config.getTaskExecutionSaEmail())
            .setAudience(config.getTargetBaseUrl())
            .build())
        .build();

    Task.Builder taskBuilder = Task.newBuilder()
        .setName(TaskName.of(
            config.getProjectId(), config.getLocationId(), config.getQueueId(), taskId).toString())
        .setHttpRequest(httpRequest);

    if (scheduleTime != null) {
      taskBuilder.setScheduleTime(Timestamp.newBuilder()
          .setSeconds(scheduleTime.getEpochSecond())
          .setNanos(scheduleTime.getNano())
          .build());
    }

    try (com.google.cloud.tasks.v2.CloudTasksClient client =
        com.google.cloud.tasks.v2.CloudTasksClient.create()) {
      client.createTask(queueName, taskBuilder.build());
      log.info("Cloud Task 생성 완료: taskId={}, scheduleTime={}", taskId, scheduleTime);
    } catch (com.google.api.gax.rpc.AlreadyExistsException e) {
      log.info("Cloud Task 이미 존재 (멱등): taskId={}", taskId);
    } catch (IOException e) {
      log.error("Cloud Tasks 클라이언트 생성 실패", e);
      throw new RuntimeException("Cloud Tasks 클라이언트 생성 실패", e);
    }
  }

  public void deleteTask(String taskId) {
    TaskName taskName = TaskName.of(
        config.getProjectId(), config.getLocationId(), config.getQueueId(), taskId);

    try (com.google.cloud.tasks.v2.CloudTasksClient client =
        com.google.cloud.tasks.v2.CloudTasksClient.create()) {
      client.deleteTask(taskName);
      log.info("Cloud Task 삭제 완료: taskId={}", taskId);
    } catch (com.google.api.gax.rpc.NotFoundException e) {
      log.info("Cloud Task 이미 삭제됨 (no-op): taskId={}", taskId);
    } catch (IOException e) {
      log.error("Cloud Tasks 클라이언트 생성 실패", e);
      throw new RuntimeException("Cloud Tasks 클라이언트 생성 실패", e);
    }
  }

  public static String buildTaskId(Long bundleId, String scheduledDate) {
    return String.format("med-%d-%s", bundleId, scheduledDate);
  }

  public static String buildDiaryReminderTaskId(Long reminderId, String scheduledDate) {
    return String.format("diary-reminder-%d-%s", reminderId, scheduledDate);
  }

  public static String buildDiaryReminderRescheduleTaskId(Long jobId, int retryCount) {
    return String.format("diary-reminder-reschedule-%d-%d", jobId, retryCount);
  }
}
