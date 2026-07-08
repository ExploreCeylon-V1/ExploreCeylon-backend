package com.exploreceylon.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService; 

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors().and()
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/hotels/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/vehicles/local/*/reviews").authenticated()
                .requestMatchers("/api/v1/vehicles/**").permitAll()
                .requestMatchers("/api/v1/vehicle-bookings/**").authenticated()
                .requestMatchers("/api/v1/events/**").permitAll()
                .requestMatchers("/api/v1/gems/submit").authenticated()
                .requestMatchers("/api/v1/gems/pending").hasRole("ADMIN")
                .requestMatchers("/api/v1/gems/**").permitAll()
                .requestMatchers("/api/v1/upload/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/guides/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/guides/*/reviews").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/guides/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/guides/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/guides/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/guide-bookings/**").authenticated()
                .requestMatchers("/api/v1/trips/**").authenticated()
                .requestMatchers("/api/v1/budget/**").authenticated()
                .requestMatchers("/api/v1/payments/*/notify").permitAll() // PayHere webhooks
                .requestMatchers("/api/v1/payments/**").authenticated()
                .requestMatchers("/api/v1/admin/stats/**").permitAll()
                .requestMatchers("/api/v1/guide-payments/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/destinations/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/count").permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter,
                    UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("http://localhost:*", "http://127.0.0.1:*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService); 
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}