package com.precisionnutrition.service;

import com.precisionnutrition.model.dto.AiChatRequest;
import com.precisionnutrition.model.dto.AiChatResponse;

import java.util.UUID;

public interface NutritionAiService {

    AiChatResponse chat(UUID userId, AiChatRequest request);
}
