package com.joakimcolloz.stocker.api.config;

import com.joakimcolloz.stocker.api.entity.Candlestick;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.rest.core.config.RepositoryRestConfiguration;
import org.springframework.data.rest.webmvc.config.RepositoryRestConfigurer;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

/**
 * Spring Data REST configuration that exposes the {@link Candlestick} entity as read-only.
 * <p>
 * Disables HTTP methods (POST, PUT, PATCH, DELETE) to prevent modifications
 * via REST endpoints, allowing only read operations (GET).
 *
 * @author Joakim Colloz
 * @version 1.0
 */
@Configuration
public class RestRepositoryConfig implements RepositoryRestConfigurer {

    /**
     * Customizes repository REST configuration to disable write operations
     * (POST, PUT, PATCH, DELETE) for {@link Candlestick} endpoints.
     *
     * @param config the repository REST configuration
     * @param cors   the CORS registry
     */
    @Override
    public void configureRepositoryRestConfiguration(RepositoryRestConfiguration config, CorsRegistry cors) {
        config.getExposureConfiguration()
                .forDomainType(Candlestick.class)
                .withItemExposure((metadata, httpMethods) ->
                        httpMethods.disable(
                                HttpMethod.POST,
                                HttpMethod.PUT,
                                HttpMethod.PATCH,
                                HttpMethod.DELETE
                        ))
                .withCollectionExposure((metadata, httpMethods) ->
                        httpMethods.disable(
                                HttpMethod.POST,
                                HttpMethod.PUT,
                                HttpMethod.PATCH,
                                HttpMethod.DELETE
                        ));
    }
}
