package com.joakimcolloz.stocker.api.config;

import com.joakimcolloz.stocker.api.security.ApiKeyAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for stateless API key authentication.
 * <p>
 * Registers a custom {@link ApiKeyAuthenticationFilter}, disables session-based authentication mechanisms,
 * and permits public access to Swagger endpoints. All other API requests under <code>/api/**</code>
 * require a valid API key.
 *
 * <ul>
 *   <li>Disables CSRF, HTTP Basic, and form login</li>
 *   <li>Enforces stateless session policy</li>
 *   <li>Returns HTTP 401 for unauthorized access</li>
 * </ul>
 *
 * @author Joakim Colloz
 * @version 1.0
 */
@Configuration
@EnableWebSecurity
public class ApiKeySecurityConfig {

    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    /**
     * Constructs the configuration with the provided API key filter.
     *
     * @param apiKeyAuthenticationFilter the custom API key authentication filter
     */
    public ApiKeySecurityConfig(ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) {
        this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
    }

    /**
     * Defines the Spring Security filter chain for API key-based authentication.
     * <p>
     * Registers the {@link ApiKeyAuthenticationFilter} before the standard
     * {@link UsernamePasswordAuthenticationFilter}.
     *
     * @param http the HTTP security configuration
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if an error occurs during configuration
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Add API key authentication filter
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(authz -> authz
                        // Permit actuator and Swagger endpoints
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/api-docs/**")
                        .permitAll()
                        // Require authentication for API endpoints
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated()
                )
                // Disable CSRF protection (stateless API)
                .csrf(AbstractHttpConfigurer::disable)
                // Stateless session management
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Disable HTTP Basic and form login
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                // Return 401 Unauthorized for authentication failures
                .exceptionHandling(ex ->
                        ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                );

        return http.build();
    }
}
