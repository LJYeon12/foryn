package com.forA.chatbot.user.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forA.chatbot.user.domain.enums.DisorderType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Converter
public class DisorderSetConverter implements AttributeConverter<Set<DisorderType>, String> {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public String convertToDatabaseColumn(Set<DisorderType> disorders) {
    if (disorders == null || disorders.isEmpty()) {
      return "[]";
    }

    try {
      List<Integer> disorderIds = DisorderType.toIds(disorders);
      return objectMapper.writeValueAsString(disorderIds);
    } catch (JsonProcessingException e) {
      log.error("Error converting disorders to JSON", e);
      return "[]";
    }
  }

  @Override
  public Set<DisorderType> convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.trim().isEmpty() || "[]".equals(dbData.trim())) {
      return Set.of();
    }

    try {
      List<Integer> disorderIds =
          objectMapper.readValue(dbData, new TypeReference<List<Integer>>() {});
      return DisorderType.fromIds(disorderIds);
    } catch (IOException e) {
      log.error("Error converting JSON to disorders", e);
      return Set.of();
    }
  }
}
