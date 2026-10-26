package com.forA.chatbot.notification.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.forA.chatbot.notification.service.MedicationSchedulerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class MedicationPollingSchedulerTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfig.class, MedicationPollingScheduler.class);

  @Test
  @DisplayName("mode=polling when the scheduler bean is registered")
  void pollingMode_registersBean() {
    contextRunner
        .withPropertyValues("notification.scheduling.mode=polling")
        .run(context -> assertThat(context).hasSingleBean(MedicationPollingScheduler.class));
  }

  @Test
  @DisplayName("mode=cloud-tasks when the scheduler bean is not registered")
  void cloudTasksMode_doesNotRegisterBean() {
    contextRunner
        .withPropertyValues("notification.scheduling.mode=cloud-tasks")
        .run(context -> assertThat(context).doesNotHaveBean(MedicationPollingScheduler.class));
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    MedicationSchedulerService medicationSchedulerService() {
      return org.mockito.Mockito.mock(MedicationSchedulerService.class);
    }
  }
}
