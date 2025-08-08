package com.joakimcolloz.stocker.api.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;

/**
 * Authentication token representing a successfully authenticated API key.
 * <p>
 * Used by {@link ApiKeyAuthenticationFilter} to authenticate clients via API keys.
 * Grants the {@code ROLE_API_USER} authority to all authenticated API key clients.
 *
 * @author Joakim Colloz
 * @version 1.0
 */
public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final String apiKey; // The authenticated API key

    /**
     * Constructs a new authenticated API key token with {@code ROLE_API_USER} authority.
     *
     * @param apiKey the validated API key
     */
    public ApiKeyAuthenticationToken(String apiKey) {
        super(Collections.singletonList(new SimpleGrantedAuthority("ROLE_API_USER")));
        this.apiKey = apiKey;
        setAuthenticated(true);
    }

    /**
     * Returns the credentials, which is the raw API key.
     *
     * @return the API key
     */
    @Override
    public Object getCredentials() {
        return apiKey;
    }

    /**
     * Returns the principal name for the authenticated API client.
     *
     * @return static string {@code "api-client"}
     */
    @Override
    public Object getPrincipal() {
        return "api-client";
    }


    /**
     * Returns a unique name for the authenticated API client, based on a shortened version of the API key.
     *
     * @return a string in the format {@code api-client-<first8chars>}
     */
    @Override
    public String getName() {
        return "api-client-" + apiKey.substring(0, Math.min(8, apiKey.length()));
    }
}
