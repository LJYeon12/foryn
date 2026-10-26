package com.forA.chatbot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "cloud-tasks")
public class CloudTasksConfig {

  private String projectId;
  private String locationId;
  private String queueId;
  private String targetBaseUrl;
  private String taskExecutionSaEmail;
}
