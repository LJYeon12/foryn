package com.forA.chatbot.chat.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forA.chatbot.chat.dto.BehavioralSkill;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BehavioralSkillProvider {

    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;
    private List<BehavioralSkill> behavioralSkills = Collections.emptyList();

    @PostConstruct
    public void loadBehavioralSkills() {
        try {
            Resource resource = applicationContext.getResource("classpath:behavioral-skills.json");
            InputStream inputStream = resource.getInputStream();
            this.behavioralSkills = objectMapper.readValue(inputStream, new TypeReference<List<BehavioralSkill>>() {});
            log.info("JSON에서 {}개의 행동 스킬을 로드했습니다.", behavioralSkills.size());
        } catch (Exception e) {
            log.error("JSON에서 행동 스킬을 로드하는데 실패했습니다.", e);
            this.behavioralSkills = Collections.emptyList();
        }
    }

    public List<BehavioralSkill> getAllSkills() {
        return behavioralSkills;
    }

    public Optional<BehavioralSkill> findByChunkId(String chunkId) {
        return behavioralSkills.stream()
                .filter(skill -> skill.getChunkId().equals(chunkId))
                .findFirst();
    }
}