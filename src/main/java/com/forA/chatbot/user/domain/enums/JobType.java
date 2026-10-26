package com.forA.chatbot.user.domain.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum JobType {
  HIGH_SCHOOL_STUDENT(1, "고등학생"),
  UNIVERSITY_STUDENT(2, "대학생"),
  OFFICE_WORKER(3, "직장인"),
  PART_TIMER(4, "파트타이머"),
  FREELANCER(5, "프리랜서"),
  JOB_SEEKER(6, "취업준비생"),
  HOUSEWIFE(7, "주부"),
  OUT_OF_SCHOOL_YOUTH(8, "학교 밖 청소년"),
  UNEMPLOYED(9, "무직");

  private final int id;
  private final String name;

  public static JobType fromId(int id) {
    return Arrays.stream(values())
        .filter(job -> job.id == id)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Invalid job id: " + id));
  }

  public static Set<JobType> fromIds(List<Integer> ids) {
    if (ids == null || ids.isEmpty()) {
      return Set.of();
    }
    return ids.stream().map(JobType::fromId).collect(Collectors.toSet());
  }

  public static List<Integer> toIds(Set<JobType> jobs) {
    if (jobs == null || jobs.isEmpty()) {
      return List.of();
    }
    return jobs.stream().map(JobType::getId).sorted().collect(Collectors.toList());
  }
}
