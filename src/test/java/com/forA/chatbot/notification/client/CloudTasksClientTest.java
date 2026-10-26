package com.forA.chatbot.notification.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forA.chatbot.config.CloudTasksConfig;
import com.google.api.gax.rpc.AlreadyExistsException;
import com.google.api.gax.rpc.NotFoundException;
import com.google.api.gax.rpc.UnavailableException;
import com.google.cloud.tasks.v2.HttpMethod;
import com.google.cloud.tasks.v2.QueueName;
import com.google.cloud.tasks.v2.Task;
import com.google.cloud.tasks.v2.TaskName;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CloudTasksClientTest {

  private static final QueueName QUEUE = QueueName.of("local-project", "local-region", "local-queue");
  private static final TaskName TASK = TaskName.of("local-project", "local-region", "local-queue", "med-local");

  @Test
  @DisplayName("실제 어댑터가 작업 ID·예약 시각·JSON·OIDC 설정을 SDK 요청에 넣는다")
  void createsExpectedSdkRequestAndClosesClient() throws Exception {
    var sdk = mock(com.google.cloud.tasks.v2.CloudTasksClient.class);
    Instant scheduled = Instant.parse("2026-10-21T01:02:03.123456789Z");
    try (var factory = mockStatic(com.google.cloud.tasks.v2.CloudTasksClient.class)) {
      factory.when(com.google.cloud.tasks.v2.CloudTasksClient::create).thenReturn(sdk);
      adapter().createTask("med-local", "/internal/notifications/send/42", "{\"bundleId\":42}", scheduled);

      ArgumentCaptor<Task> request = ArgumentCaptor.forClass(Task.class);
      verify(sdk).createTask(eq(QUEUE), request.capture());
      Task task = request.getValue();
      assertThat(task.getName()).isEqualTo(TASK.toString());
      assertThat(task.getScheduleTime().getSeconds()).isEqualTo(scheduled.getEpochSecond());
      assertThat(task.getScheduleTime().getNanos()).isEqualTo(scheduled.getNano());
      assertThat(task.getHttpRequest().getHttpMethod()).isEqualTo(HttpMethod.POST);
      assertThat(task.getHttpRequest().getUrl()).isEqualTo("https://local.example.invalid/internal/notifications/send/42");
      assertThat(task.getHttpRequest().getHeadersMap()).containsEntry("Content-Type", "application/json");
      assertThat(task.getHttpRequest().getBody().toStringUtf8()).isEqualTo("{\"bundleId\":42}");
      assertThat(task.getHttpRequest().getOidcToken().getAudience()).isEqualTo("https://local.example.invalid");
      assertThat(task.getHttpRequest().getOidcToken().getServiceAccountEmail()).isEqualTo("runner@example.invalid");
      verify(sdk).close();
    }
  }

  @Test
  @DisplayName("예약 시각이 없으면 SDK 요청에 예약 시각을 강제로 넣지 않는다")
  void nullScheduleLeavesTimeUnset() throws Exception {
    var sdk = mock(com.google.cloud.tasks.v2.CloudTasksClient.class);
    try (var factory = mockStatic(com.google.cloud.tasks.v2.CloudTasksClient.class)) {
      factory.when(com.google.cloud.tasks.v2.CloudTasksClient::create).thenReturn(sdk);
      adapter().createTask("med-local", "/internal/test", "{}", null);
      ArgumentCaptor<Task> request = ArgumentCaptor.forClass(Task.class);
      verify(sdk).createTask(eq(QUEUE), request.capture());
      assertThat(request.getValue().hasScheduleTime()).isFalse();
      verify(sdk).close();
    }
  }

  @Test
  @DisplayName("동일 작업의 AlreadyExists 응답은 성공으로 취급하고 ID를 바꾸지 않는다")
  void alreadyExistsIsIdempotentSuccess() throws Exception {
    var first = mock(com.google.cloud.tasks.v2.CloudTasksClient.class);
    var retry = mock(com.google.cloud.tasks.v2.CloudTasksClient.class);
    when(retry.createTask(eq(QUEUE), any(Task.class))).thenThrow(mock(AlreadyExistsException.class));
    try (var factory = mockStatic(com.google.cloud.tasks.v2.CloudTasksClient.class)) {
      factory.when(com.google.cloud.tasks.v2.CloudTasksClient::create).thenReturn(first, retry);
      CloudTasksClient adapter = adapter();
      for (int attempt = 0; attempt < 2; attempt++) {
        assertThatCode(() -> adapter.createTask("med-local", "/internal/test", "{}", null)).doesNotThrowAnyException();
      }
      for (var sdk : List.of(first, retry)) {
        ArgumentCaptor<Task> request = ArgumentCaptor.forClass(Task.class);
        verify(sdk).createTask(eq(QUEUE), request.capture());
        assertThat(request.getValue().getName()).isEqualTo(TASK.toString());
        verify(sdk).close();
      }
    }
  }

  @Test
  @DisplayName("등록 서비스 오류는 숨기지 않고 Outbox가 재시도하도록 전파한다")
  void unavailableRegistrationPropagatesAndClosesClient() throws Exception {
    var sdk = mock(com.google.cloud.tasks.v2.CloudTasksClient.class);
    var failure = mock(UnavailableException.class);
    when(sdk.createTask(eq(QUEUE), any(Task.class))).thenThrow(failure);
    try (var factory = mockStatic(com.google.cloud.tasks.v2.CloudTasksClient.class)) {
      factory.when(com.google.cloud.tasks.v2.CloudTasksClient::create).thenReturn(sdk);
      assertThatThrownBy(() -> adapter().createTask("med-local", "/internal/test", "{}", null)).isSameAs(failure);
      verify(sdk).close();
    }
  }

  @Test
  @DisplayName("SDK 생성 IO 오류는 원인을 보존해 호출자에게 전달한다")
  void sdkInitializationFailurePropagates() throws Exception {
    IOException failure = new IOException("synthetic initialization failure");
    try (var factory = mockStatic(com.google.cloud.tasks.v2.CloudTasksClient.class)) {
      factory.when(com.google.cloud.tasks.v2.CloudTasksClient::create).thenThrow(failure);
      assertThatThrownBy(() -> adapter().createTask("med-local", "/internal/test", "{}", null))
          .isInstanceOf(RuntimeException.class).hasCause(failure);
    }
  }

  @Test
  @DisplayName("삭제할 작업이 이미 없으면 성공으로 취급하고 SDK를 닫는다")
  void missingTaskDeleteIsIdempotent() throws Exception {
    var sdk = mock(com.google.cloud.tasks.v2.CloudTasksClient.class);
    org.mockito.Mockito.doThrow(mock(NotFoundException.class)).when(sdk).deleteTask(TASK);
    try (var factory = mockStatic(com.google.cloud.tasks.v2.CloudTasksClient.class)) {
      factory.when(com.google.cloud.tasks.v2.CloudTasksClient::create).thenReturn(sdk);
      assertThatCode(() -> adapter().deleteTask("med-local")).doesNotThrowAnyException();
      verify(sdk).deleteTask(TASK);
      verify(sdk).close();
    }
  }

  @Test
  @DisplayName("삭제 서비스 장애는 성공으로 숨기지 않는다")
  void deleteFailurePropagates() throws Exception {
    var sdk = mock(com.google.cloud.tasks.v2.CloudTasksClient.class);
    var failure = mock(UnavailableException.class);
    org.mockito.Mockito.doThrow(failure).when(sdk).deleteTask(TASK);
    try (var factory = mockStatic(com.google.cloud.tasks.v2.CloudTasksClient.class)) {
      factory.when(com.google.cloud.tasks.v2.CloudTasksClient::create).thenReturn(sdk);
      assertThatThrownBy(() -> adapter().deleteTask("med-local")).isSameAs(failure);
      verify(sdk).close();
    }
  }

  private CloudTasksClient adapter() {
    CloudTasksConfig config = new CloudTasksConfig();
    config.setProjectId("local-project");
    config.setLocationId("local-region");
    config.setQueueId("local-queue");
    config.setTargetBaseUrl("https://local.example.invalid");
    config.setTaskExecutionSaEmail("runner@example.invalid");
    return new CloudTasksClient(config);
  }

  @Test
  @DisplayName("buildTaskId는 med-{bundleId}-{date} 형식을 생성한다")
  void buildTaskId_generatesCorrectFormat() {
    String taskId = CloudTasksClient.buildTaskId(42L, "2026-04-04");

    assertThat(taskId).isEqualTo("med-42-2026-04-04");
  }

  @Test
  @DisplayName("buildTaskId는 다른 bundleId와 날짜에 대해 다른 ID를 생성한다")
  void buildTaskId_differentInputs_differentIds() {
    String taskId1 = CloudTasksClient.buildTaskId(42L, "2026-04-04");
    String taskId2 = CloudTasksClient.buildTaskId(43L, "2026-04-04");
    String taskId3 = CloudTasksClient.buildTaskId(42L, "2026-04-05");

    assertThat(taskId1).isNotEqualTo(taskId2);
    assertThat(taskId1).isNotEqualTo(taskId3);
  }

  @Test
  @DisplayName("buildDiaryReminderTaskId는 diary-reminder-{reminderId}-{date} 형식을 생성한다")
  void buildDiaryReminderTaskId_generatesCorrectFormat() {
    String taskId = CloudTasksClient.buildDiaryReminderTaskId(10L, "2026-06-22");

    assertThat(taskId).isEqualTo("diary-reminder-10-2026-06-22");
  }
}
