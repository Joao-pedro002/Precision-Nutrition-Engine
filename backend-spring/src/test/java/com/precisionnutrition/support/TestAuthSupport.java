package com.precisionnutrition.support;

import com.precisionnutrition.model.enums.UserRole;
import com.precisionnutrition.security.AuthenticatedUser;
import com.precisionnutrition.util.AppConstants;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

public final class TestAuthSupport {

    private TestAuthSupport() {
    }

    public static RequestPostProcessor asDefaultUser() {
        return asUser(AppConstants.DEFAULT_USER_ID, UserRole.ADMIN);
    }

    public static RequestPostProcessor asUser(UUID userId, UserRole role) {
        AuthenticatedUser principal = new AuthenticatedUser(
                userId,
                "user-" + userId + "@test.local",
                "Test User",
                role,
                true
        );
        var auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }
}
