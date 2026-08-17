package com.pm.patientservice.infrastructure.bean;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class RoleHeaderFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        String path = request.getRequestURI();

        // Không xử lý authentication cho Actuator
        if (path.equals("/actuator")
                || path.startsWith("/actuator/")) {
            return true;
        }

        // Không xử lý authentication cho internal APIs
        if (path.startsWith("/internal/")) {
            return true;
        }

        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String role = request.getHeader("X-User-Role");

        if (role == null || role.isBlank()) {
            role = request.getHeader("X-Role");
        }

        String principal = request.getHeader("X-User-Email");

        if (principal == null || principal.isBlank()) {
            principal = request.getHeader("X-User-Id");
        }

        if (principal == null || principal.isBlank()) {
            principal = "gateway-user";
        }

        if (role != null && !role.isBlank()) {

            List<GrantedAuthority> authorities =
                    List.of(
                            new SimpleGrantedAuthority(
                                    "ROLE_" + role
                            )
                    );

            Authentication authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            authorities
                    );

            SecurityContextHolder
                    .getContext()
                    .setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}