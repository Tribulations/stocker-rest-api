package com.joakimcolloz.stocker.api;

import io.github.cdimascio.dotenv.Dotenv;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

import static com.joakimcolloz.stocker.api.security.SecurityConstants.API_KEY_HEADER;
import static com.joakimcolloz.stocker.api.security.SecurityConstants.API_KEY_SCHEME;

/**
 * Entry point for the Stocker REST API Spring Boot application.
 * <p>
 * Configures OpenAPI documentation and loads environment variables from a local {@code .env} file
 * before initializing the Spring context. Secures the API via a header-based API key.
 *
 * <p><strong>OpenAPI:</strong> Adds metadata and API key security definition for Swagger UI.
 *
 * <p><strong>Environment Variables:</strong> Uses {@link Dotenv} to load configuration values
 * into system properties, allowing them to be used by Spring Boot.
 *
 * @author Joakim Colloz
 * @version 1.0
 */
// Configure API key authentication for Swagger UI
@SecurityScheme(
		name = API_KEY_SCHEME,
		type = SecuritySchemeType.APIKEY,
		in = io.swagger.v3.oas.annotations.enums.SecuritySchemeIn.HEADER,
		paramName = API_KEY_HEADER
)
// Apply API key requirement to all endpoints
@OpenAPIDefinition(
		info = @Info(title = "Stocker REST API", version = "v1"),
		security = @SecurityRequirement(name = API_KEY_SCHEME)
)
// Disable auto-generated passwords and login forms (using API keys instead)
@SpringBootApplication(exclude = {
		UserDetailsServiceAutoConfiguration.class
})
public class Application {
	public static void main(String[] args) {
		loadEnvironmentVariables();
		SpringApplication.run(Application.class, args);
	}

	/**
	 * Loads environment variables from a local {@code .env} file and registers them as system properties.
	 * <p>
	 * Skips loading if file is missing or malformed. Primarily used for local development.
	 */
	private static void loadEnvironmentVariables() {
		try {
			Dotenv.configure()
					.directory(".")
					.ignoreIfMissing()
					.ignoreIfMalformed()
					.load()
					.entries()
					.forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

			System.out.println("Loaded environment variables from .env file.");
		} catch (Exception e) {
			System.err.println("Failed to load .env file: " + e.getMessage());
		}
	}
}
