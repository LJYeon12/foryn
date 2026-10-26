package com.forA.chatbot.user.domain.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DisorderType {
  DEPRESSION(1, "우울증"),
  BIPOLAR_DISORDER(2, "조울증"),
  ANXIETY_DISORDER(3, "불안장애"),
  PANIC_DISORDER(4, "공황장애"),
  SLEEP_DISORDER(5, "수면장애"),
  ADHD(6, "ADHD"),
  OBSESSIVE_COMPULSIVE_DISORDER(7, "강박장애"),
  PTSD(8, "PTSD(트라우마)"),
  EXCRETORY_TENSION_RESPONSE(9, "배변배뇨와 관련된 긴장 반응"),
  EATING_DISORDER(10, "섭식장애"),
  SEXUAL_DYSFUNCTION(11, "성기능장애"),
  PREFER_NOT_TO_SAY(12, "말하고 싶지 않아요"),
  NONE(13, "없음");

  private final int id;
  private final String name;

  public static DisorderType fromId(int id) {
    return Arrays.stream(values())
        .filter(disorder -> disorder.id == id)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Invalid disorder id: " + id));
  }

  public static Set<DisorderType> fromIds(List<Integer> ids) {
    if (ids == null || ids.isEmpty()) {
      return Set.of();
    }
    return ids.stream().map(DisorderType::fromId).collect(Collectors.toSet());
  }

  public static List<Integer> toIds(Set<DisorderType> disorders) {
    if (disorders == null || disorders.isEmpty()) {
      return List.of();
    }
    return disorders.stream().map(DisorderType::getId).sorted().collect(Collectors.toList());
  }
}
