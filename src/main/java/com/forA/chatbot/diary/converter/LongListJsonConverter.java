package com.forA.chatbot.diary.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Converter
public class LongListJsonConverter implements AttributeConverter<List<Long>, String> {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public String convertToDatabaseColumn(List<Long> values) {
    if (values == null || values.isEmpty()) {
      return "[]";
    }

    try {
      return objectMapper.writeValueAsString(values);
    } catch (JsonProcessingException e) {
      log.error("Error converting long list to JSON", e);
      return "[]";
    }
  }

  @Override
  public List<Long> convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.trim().isEmpty() || "[]".equals(dbData.trim())) {
      return List.of();
    }

    try {
      return objectMapper.readValue(dbData, new TypeReference<List<Long>>() {});
    } catch (IOException e) {
      log.error("Error converting JSON to long list", e);
      return List.of();
    }
  }
}
