package com.forA.chatbot.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehavioralSkill {

    @JsonProperty("chunk_id")
    private String chunkId;

    @JsonProperty("skill_type")
    private String skillType;

    @JsonProperty("situation_tags")
    private List<String> situationTags;

    @JsonProperty("skill_origin")
    private String skillOrigin;

    @JsonProperty("skill_name")
    private String skillName;

    private String description;

    @JsonProperty("step_by_step")
    private List<String> stepByStep;

    @JsonProperty("emotion_tags")
    private List<String> emotionTags;
}
