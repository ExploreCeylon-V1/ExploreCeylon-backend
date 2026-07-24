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

                // Live chat: WS handshake auth is done separately (ChatHandshakeInterceptor);
                // REST is traveler-authenticated / admin-gated like everything else.
                .requestMatchers("/ws-chat/**").permitAll()
                .requestMatchers("/api/v1/chat/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/chat/**").authenticated()

                // Local vehicles (public browse; mutations admin-only via AdminVehicleController)
                .requestMatchers(HttpMethod.POST, "/api/v1/vehicles/local/*/reviews").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/v1/vehicles/local/*/availability").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/vehicles/local/search").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/vehicles/**").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/vehicle-bookings/*/status").hasRole("ADMIN")
                .requestMatchers("/api/v1/vehicle-bookings/**").authenticated()

                // Events: public read, admin-only write
                .requestMatchers(HttpMethod.GET, "/api/v1/events/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/events").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/events/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/events/**").hasRole("ADMIN")

                // Hidden gems: public read, submit requires auth, admin-only moderation/write
                .requestMatchers("/api/v1/gems/submit").authenticated()
                .requestMatchers("/api/v1/gems/pending").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/gems/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/gems/*/reviews").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/gems/*/reviews/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/gems").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/gems/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/gems/**").hasRole("ADMIN")

                .requestMatchers("/api/v1/upload/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/guides/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/guides/*/reviews").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/guides/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/guides/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/guides/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/guide-bookings/*/status").hasRole("ADMIN")
                .requestMatchers("/api/v1/guide-bookings/**").authenticated()

                // Trips: share link is genuinely public, everything else requires auth (ownership enforced in service layer)
                .requestMatchers(HttpMethod.GET, "/api/v1/trips/share/**").permitAll()
                .requestMatchers("/api/v1/trips/**").authenticated()

                .requestMatchers("/api/v1/budget/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/contact").permitAll()  // public submit
                .requestMatchers("/api/v1/contact/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/payments/*/notify").permitAll() // PayHere webhooks
                .requestMatchers("/api/v1/payments/**").authenticated()
                .requestMatchers("/api/v1/guide-payments/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                // Destinations: public read, admin-only write; nested reviews public read/auth post/admin delete
                .requestMatchers(HttpMethod.POST, "/api/v1/destinations/*/reviews").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/destinations/*/reviews/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/destinations/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/destinations").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/destinations/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/destinations/**").hasRole("ADMIN")

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
        source.registerCorsConfiguration("/ws-chat/**", configuration); // SockJS XHR-fallback handshake
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