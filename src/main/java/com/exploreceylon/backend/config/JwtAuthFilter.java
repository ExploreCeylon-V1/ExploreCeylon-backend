package com.exploreceylon.backend.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    private String getHeaderPreview(String header) {
        if (header == null) return "NULL";
        if (header.length() <= 30) return header + " (len=" + header.length() + ")";
        return header.substring(0, 15) + "..." + header.substring(header.length() - 15) + " (len=" + header.length() + ")";
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String uri = request.getRequestURI();
        final String method = request.getMethod();
        final String authHeader = request.getHeader("Authorization");
        final boolean isTrackedRoute = uri.contains("/api/v1/trips") || uri.contains("/api/v1/auth");

        if (isTrackedRoute) {
            log.info("[DIAGNOSTIC-JWT-HIT] {} {} | AuthHeader: {}", method, uri, getHeaderPreview(authHeader));
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            if (isTrackedRoute) {
                log.warn("[DIAGNOSTIC-JWT-RESULT] {} {} | Outcome: NO_VALID_BEARER_HEADER | Header: {}", method, uri, getHeaderPreview(authHeader));
            }
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        final String tokenPreview = getHeaderPreview(jwt);

        try {
            final String userEmail = jwtService.extractUsername(jwt);
            Date exp = jwtService.extractClaim(jwt, Claims::getExpiration);
            long expSec = exp != null ? exp.getTime() / 1000 : 0;
            long nowSec = System.currentTimeMillis() / 1000;
            long remainingSec = expSec - nowSec;

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

                boolean isValid = jwtService.isTokenValid(jwt, userDetails);
                boolean isEnabled = userDetails.isEnabled();

                if (isValid && isEnabled) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null,
                                    userDetails.getAuthorities());

                    authToken.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(request));

                    SecurityContextHolder.getContext()
                            .setAuthentication(authToken);

                    if (isTrackedRoute) {
                        log.info("[DIAGNOSTIC-JWT-RESULT] {} {} | Token: {} | User: {} | Outcome: AUTHENTICATED | ExpSec: {} | ServerNow: {} | Remaining: {}s",
                                method, uri, tokenPreview, userEmail, expSec, nowSec, remainingSec);
                    }
                } else {
                    if (isTrackedRoute) {
                        log.warn("[DIAGNOSTIC-JWT-RESULT] {} {} | Token: {} | User: {} | Outcome: REJECTED | isValid: {} | isEnabled: {} | Remaining: {}s",
                                method, uri, tokenPreview, userEmail, isValid, isEnabled, remainingSec);
                    }
                }
            }
        } catch (ExpiredJwtException e) {
            Claims claims = e.getClaims();
            Date exp = claims != null ? claims.getExpiration() : null;
            String sub = claims != null ? claims.getSubject() : "unknown";
            long expSec = exp != null ? exp.getTime() / 1000 : 0;
            long nowSec = System.currentTimeMillis() / 1000;
            long expiredAgoSec = nowSec - expSec;
            log.warn("[DIAGNOSTIC-JWT-RESULT] {} {} | Token: {} | Subject: {} | Outcome: EXPIRED_JWT | ExpAt: {} | ServerNow: {} | ExpiredAgo: {}s | Msg: {}",
                    method, uri, tokenPreview, sub, expSec, nowSec, expiredAgoSec, e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("[DIAGNOSTIC-JWT-RESULT] {} {} | Token: {} | Outcome: MALFORMED_JWT | Msg: {}",
                    method, uri, tokenPreview, e.getMessage());
        } catch (SignatureException e) {
            log.warn("[DIAGNOSTIC-JWT-RESULT] {} {} | Token: {} | Outcome: SIGNATURE_MISMATCH | Msg: {}",
                    method, uri, tokenPreview, e.getMessage());
        } catch (UsernameNotFoundException e) {
            log.warn("[DIAGNOSTIC-JWT-RESULT] {} {} | Token: {} | Outcome: USER_NOT_FOUND_IN_DB | Msg: {}",
                    method, uri, tokenPreview, e.getMessage());
        } catch (JwtException e) {
            log.warn("[DIAGNOSTIC-JWT-RESULT] {} {} | Token: {} | Outcome: GENERAL_JWT_EXCEPTION ({}) | Msg: {}",
                    method, uri, tokenPreview, e.getClass().getSimpleName(), e.getMessage());
        } catch (Exception e) {
            log.error("[DIAGNOSTIC-JWT-RESULT] {} {} | Token: {} | Outcome: UNEXPECTED_EXCEPTION ({}) | Msg: {}",
                    method, uri, tokenPreview, e.getClass().getSimpleName(), e.getMessage(), e);
        }

        filterChain.doFilter(request, response);
    }
}