package com.joakimcolloz.stocker.api;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Stocker REST API Spring Boot application.
 *
 * @author Joakim Colloz
 * @version 1.0
 */
@SpringBootApplication
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
					.load()
					.entries()
					.forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

			System.out.println("Loaded environment variables from .env file.");
		} catch (Exception e) {
			System.err.println("Failed to load .env file: " + e.getMessage());
		}
	}
}
