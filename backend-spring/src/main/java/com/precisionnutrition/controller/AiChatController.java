package com.precisionnutrition.controller;

import com.precisionnutrition.model.dto.AiChatRequest;
import com.precisionnutrition.model.dto.AiChatResponse;
import com.precisionnutrition.security.SecurityUtils;
import com.precisionnutrition.service.NutritionAiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final NutritionAiService nutritionAiService;

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        AiChatResponse response = nutritionAiService.chat(userId, request);
        return ResponseEntity.ok(response);
    }
}
