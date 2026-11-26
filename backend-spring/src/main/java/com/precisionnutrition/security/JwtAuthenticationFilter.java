package com.precisionnutrition.security;

import com.precisionnutrition.model.entity.User;
import com.precisionnutrition.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Filtro JWT stateless com verificação ativa de kill switch (user.isActive()) no PostgreSQL.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            Optional<UUID> userIdOpt = jwtService.validateAndExtractUserId(token);

            if (userIdOpt.isPresent()) {
                Optional<User> userOpt = userRepository.findById(userIdOpt.get());

                if (userOpt.isPresent() && userOpt.get().isActive()) {
                    User user = userOpt.get();
                    AuthenticatedUser principal = new AuthenticatedUser(
                            user.getId(),
                            user.getEmail(),
                            user.getName(),
                            user.getRole(),
                            user.isActive()
                    );

                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    // Kill switch: usuário inexistente ou desativado (active = false)
                    SecurityContextHolder.clearContext();
                    log.debug("Token rejeitado: usuário {} inexistente ou revogado (active=false).", userIdOpt.get());
                }
            } else {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
