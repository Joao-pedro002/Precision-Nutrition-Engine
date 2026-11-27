package com.precisionnutrition.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AiChatMessageDto(
        @NotBlank(message = "Papel da mensagem é obrigatório")
        @Pattern(regexp = "user|model", message = "Papel deve ser 'user' ou 'model'")
        String role,

        @NotBlank(message = "Texto da mensagem é obrigatório")
        @Size(max = 2000, message = "Mensagem do histórico excede o limite permitido")
        String text
) {}
