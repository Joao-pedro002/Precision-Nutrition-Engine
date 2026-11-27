package com.precisionnutrition.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record AiChatRequest(
        @NotBlank(message = "A mensagem é obrigatória")
        @Size(max = 1000, message = "A mensagem deve ter no máximo 1000 caracteres")
        String message,

        LocalDate date,

        @Size(max = 12, message = "O histórico deve conter no máximo 12 mensagens (6 trocas)")
        List<@Valid AiChatMessageDto> history
) {}
