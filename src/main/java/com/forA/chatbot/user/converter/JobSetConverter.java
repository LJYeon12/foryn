package com.forA.chatbot.user.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forA.chatbot.user.domain.enums.JobType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Converter
public class JobSetConverter implements AttributeConverter<Set<JobType>, String> {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public String convertToDatabaseColumn(Set<JobType> jobs) {
    if (jobs == null || jobs.isEmpty()) {
      return "[]";
    }

    try {
      List<Integer> jobIds = JobType.toIds(jobs);
      return objectMapper.writeValueAsString(jobIds);
    } catch (JsonProcessingException e) {
      log.error("Error converting jobs to JSON", e);
      return "[]";
    }
  }

  @Override
  public Set<JobType> convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.trim().isEmpty() || "[]".equals(dbData.trim())) {
      return Set.of();
    }

    try {
      List<Integer> jobIds = objectMapper.readValue(dbData, new TypeReference<List<Integer>>() {});
      return JobType.fromIds(jobIds);
    } catch (IOException e) {
      log.error("Error converting JSON to jobs", e);
      return Set.of();
    }
  }
}
