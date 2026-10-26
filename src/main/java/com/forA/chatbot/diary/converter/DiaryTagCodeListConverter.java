package com.forA.chatbot.diary.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forA.chatbot.diary.domain.enums.DiaryTagCode;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Converter
public class DiaryTagCodeListConverter implements AttributeConverter<List<DiaryTagCode>, String> {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public String convertToDatabaseColumn(List<DiaryTagCode> tagCodes) {
    if (tagCodes == null || tagCodes.isEmpty()) {
      return "[]";
    }

    try {
      List<String> names = tagCodes.stream()
          .map(DiaryTagCode::name)
          .toList();
      return objectMapper.writeValueAsString(names);
    } catch (JsonProcessingException e) {
      log.error("Error converting diary tag codes to JSON", e);
      return "[]";
    }
  }

  @Override
  public List<DiaryTagCode> convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.trim().isEmpty() || "[]".equals(dbData.trim())) {
      return List.of();
    }

    try {
      List<String> names = objectMapper.readValue(dbData, new TypeReference<List<String>>() {});
      return names.stream()
          .map(DiaryTagCode::valueOf)
          .toList();
    } catch (IllegalArgumentException | IOException e) {
      log.error("Error converting JSON to diary tag codes", e);
      return List.of();
    }
  }
}
