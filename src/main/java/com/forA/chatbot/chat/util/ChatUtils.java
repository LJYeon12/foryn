package com.forA.chatbot.chat.util;

import com.forA.chatbot.chat.domain.enums.EmotionType;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatUtils {

    public static Set<EmotionType> parseEmotionsFromString(String emotionString) {
        if (emotionString == null || emotionString.isEmpty()) {
            return Collections.emptySet();
        }
        return Arrays.stream(emotionString.split(","))
                .map(EmotionType::valueOf)
                .collect(Collectors.toSet());
    }
}