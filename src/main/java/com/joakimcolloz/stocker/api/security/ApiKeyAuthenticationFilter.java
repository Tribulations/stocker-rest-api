package com.joakimcolloz.stocker.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

import static com.joakimcolloz.stocker.api.security.SecurityConstants.API_KEY_HEADER;

/**
 * Spring Security filter that authenticates incoming requests using an API key.
 * <p>
 * Validates the API key found in the X-API-Key header against a configured set of valid keys.
 * Skips Swagger endpoints to allow unauthenticated access.
 * Requires a valid API key for /api/** endpoints.
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);

    @Value("${app.api.valid-keys}")
    private Set<String> validApiKeys;

    /**
     * Filter method invoked once per request.
     * Checks for the API key header and validates it.
     * If valid, sets the authentication in the security context.
     *
     * @param request     HTTP servlet request
     * @param response    HTTP servlet response
     * @param filterChain filter chain to pass the request/response to the next filter
     * @throws ServletException if an error occurs during the filtering process
     * @throws IOException if an I/O error occurs during the filtering process
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Get API key from request header
        String apiKey = request.getHeader(API_KEY_HEADER);
        String requestUri = request.getRequestURI();

        // Check if API key is valid
        if (apiKey != null && validApiKeys.contains(apiKey)) {
            // Create authentication token and set it in security context
            ApiKeyAuthenticationToken auth = new ApiKeyAuthenticationToken(apiKey);
            SecurityContextHolder.getContext().setAuthentication(auth);
            logger.info("API key authenticated for request to: {}", requestUri);
        } else if (apiKey != null) {
            // Log invalid API key attempt
            logger.warn("Invalid API key attempted for request to: {}", requestUri);
        }

        // Continue with filter chain
        filterChain.doFilter(request, response);
    }

    /**
     * Specifies endpoints that should bypass this filter.
     * Excludes health/info and Swagger UI and docs endpoints.
     *
     * @param request HTTP servlet request
     * @return true if the filter should not be applied to the request
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip actuator and Swagger endpoints
        return path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/api-docs");
    }
}
