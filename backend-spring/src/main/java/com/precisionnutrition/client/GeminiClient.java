package com.precisionnutrition.client;

import com.precisionnutrition.model.dto.AiChatMessageDto;

import java.util.List;

public interface GeminiClient {

    boolean isConfigured();

    String getModel();

    String generateContent(String systemInstruction, List<AiChatMessageDto> turns);
}
