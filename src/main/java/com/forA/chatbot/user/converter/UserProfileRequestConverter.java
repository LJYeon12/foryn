package com.forA.chatbot.user.converter;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.UserHandler;
import com.forA.chatbot.user.domain.enums.DisorderType;
import com.forA.chatbot.user.domain.enums.JobType;
import com.forA.chatbot.user.domain.enums.SymptomType;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class UserProfileRequestConverter {

  public Set<JobType> convertJobs(List<String> jobNames) {
    if (jobNames == null) {
      return new HashSet<>();
    }

    return jobNames.stream()
        .map(
            name ->
                Arrays.stream(JobType.values())
                    .filter(job -> job.getName().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Invalid job: " + name)))
        .collect(Collectors.toSet());
  }

  public Set<DisorderType> convertDisorders(List<String> disorderNames) {
    if (disorderNames == null) {
      return new HashSet<>();
    }

    return disorderNames.stream()
        .map(
            name ->
                Arrays.stream(DisorderType.values())
                    .filter(disorder -> disorder.getName().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Invalid disorder: " + name)))
        .collect(Collectors.toSet());
  }

  public Set<SymptomType> convertSymptoms(Map<String, List<String>> symptomsMap) {
    if (symptomsMap == null) {
      return new HashSet<>();
    }

    Set<SymptomType> symptoms = new HashSet<>();

    for (Map.Entry<String, List<String>> entry : symptomsMap.entrySet()) {
      String disorderName = entry.getKey();
      List<String> symptomNames = entry.getValue();

      DisorderType disorderType =
          Arrays.stream(DisorderType.values())
              .filter(disorder -> disorder.getName().equals(disorderName))
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("Invalid disorder: " + disorderName));

      if (symptomNames.size() > 3) {
        throw new UserHandler(ErrorStatus.USER_SYMPTOM_DISORDER_LIMIT_EXCEEDED);
      }

      for (String symptomName : symptomNames) {
        SymptomType symptom =
            Arrays.stream(SymptomType.values())
                .filter(
                    value ->
                        value.getDescription().equals(symptomName)
                            && value.getDisorderType() == disorderType)
                .findFirst()
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "Invalid symptom: " + symptomName + " (disorder: " + disorderName + ")"));
        symptoms.add(symptom);
      }
    }

    return symptoms;
  }
}
