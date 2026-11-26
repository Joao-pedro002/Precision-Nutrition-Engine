package com.precisionnutrition.security;

import com.precisionnutrition.model.enums.UserRole;

import java.util.UUID;

public record AuthenticatedUser(
        UUID id,
        String email,
        String name,
        UserRole role,
        boolean active
) {}
