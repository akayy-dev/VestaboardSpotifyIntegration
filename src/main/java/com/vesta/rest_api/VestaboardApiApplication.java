package com.vesta.rest_api;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.vesta.rest_api.routes.StateBroadcastService;
import com.vesta.rest_api.spotify.SpotifyIntegration;

@SpringBootApplication
@EnableScheduling
public class VestaboardApiApplication extends SpringBootServletInitializer {

	private static final Logger LOG = LogManager.getLogger(VestaboardApiApplication.class);

	@Value("${cors.allowed-origins:http://localhost:3000}")
	private String corsAllowedOrigins;

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(VestaboardApiApplication.class);
	}

	public static void main(String[] args) {
		SpringApplication.run(VestaboardApiApplication.class, args);
	}

	@Bean
	public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
		return args -> {
			LOG.info("Starting VestaboardSpotify API");
			LOG.debug("ENV: CLIENT_ID is set: {}, REDIRECT_URL is set: {}", 
					System.getenv("CLIENT_ID") != null,
					System.getenv("REDIRECT_URL") != null);
			LOG.debug("Has SpotifyIntegration bean: {}", ctx.containsBean("getSpotifyIntegration"));
			try {
				Object si = ctx.getBean(SpotifyIntegration.class);
				LOG.debug("SpotifyIntegration bean instance: {}", si);
			} catch (Exception e) {
				LOG.error("Could not get SpotifyIntegration from context: {}", e.getMessage());
			}
		};
	}

	@Bean
	public SpotifyIntegration getSpotifyIntegration(StateBroadcastService stateBroadcastService) {
		String vestaboardKey = System.getenv("VESTABOARD_KEY");
		String clientID = System.getenv("CLIENT_ID");
		String clientSecret = System.getenv("CLIENT_SECRET");
		String redirectURL = System.getenv("REDIRECT_URL");
		
		// Validate required environment variables
		validateEnvVar("VESTABOARD_KEY", vestaboardKey);
		validateEnvVar("CLIENT_ID", clientID);
		validateEnvVar("CLIENT_SECRET", clientSecret);
		validateEnvVar("REDIRECT_URL", redirectURL);
		
		return new SpotifyIntegration(clientID, clientSecret, redirectURL, vestaboardKey, stateBroadcastService);
	}

	private void validateEnvVar(String name, String value) {
		if (value == null || value.isBlank()) {
			LOG.error("Required environment variable {} is not set!", name);
			throw new IllegalStateException("Required environment variable " + name + " is not set. " +
					"Please set it in your .env file or environment.");
		}
	}

	// Enabling CORS - configured via cors.allowed-origins property
	@Bean
	public WebMvcConfigurer corsConfiguration() {
		return new WebMvcConfigurer() {
			@Override
			public void addCorsMappings(CorsRegistry registry) {
				String[] origins = corsAllowedOrigins.split(",");
				LOG.info("Configuring CORS with allowed origins: {}", corsAllowedOrigins);
				registry.addMapping("/**")
						.allowedOrigins(origins)
						.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
						.allowedHeaders("*")
						.allowCredentials(true);
			}
		};
	}
}
