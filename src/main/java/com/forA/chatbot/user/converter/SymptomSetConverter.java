package com.forA.chatbot.user.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forA.chatbot.user.domain.enums.SymptomType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Converter
public class SymptomSetConverter implements AttributeConverter<Set<SymptomType>, String> {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public String convertToDatabaseColumn(Set<SymptomType> symptoms) {
    if (symptoms == null || symptoms.isEmpty()) {
      return "[]";
    }

    try {
      List<Integer> symptomIds = SymptomType.toIds(symptoms);
      return objectMapper.writeValueAsString(symptomIds);
    } catch (JsonProcessingException e) {
      log.error("Error converting symptoms to JSON", e);
      return "[]";
    }
  }

  @Override
  public Set<SymptomType> convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.trim().isEmpty() || "[]".equals(dbData.trim())) {
      return Set.of();
    }

    try {
      List<Integer> symptomIds =
          objectMapper.readValue(dbData, new TypeReference<List<Integer>>() {});
      return SymptomType.fromIds(symptomIds);
    } catch (IOException e) {
      log.error("Error converting JSON to symptoms", e);
      return Set.of();
    }
  }
}
