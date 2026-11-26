package com.precisionnutrition.model.dto;

import com.precisionnutrition.model.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCreateUserRequest(
        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        String email,

        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 8, max = 128, message = "A senha deve ter entre 8 e 128 caracteres")
        String password,

        @Size(max = 120, message = "Nome deve ter no máximo 120 caracteres")
        String name,

        UserRole role
) {}
