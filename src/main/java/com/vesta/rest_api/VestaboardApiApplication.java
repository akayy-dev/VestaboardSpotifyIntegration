package com.vesta.rest_api;

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
			System.err.println("Starting API");
			System.err.println(
					"ENV: CLIENT_ID=" + System.getenv("CLIENT_ID") + " REDIRECT_URL=" + System.getenv("REDIRECT_URL"));
			// beans
			System.err.println("Has SpotifyIntegration bean: " + ctx.containsBean("getSpotifyIntegration"));
			try {
				Object si = ctx.getBean(SpotifyIntegration.class);
				System.err.println("SpotifyIntegration bean instance: " + si);
			} catch (Exception e) {
				System.err.println("Could not get SpotifyIntegration from context: " + e.getMessage());
			}
		};
	}


	@Bean
	public SpotifyIntegration getSpotifyIntegration(StateBroadcastService stateBroadcastService) {
		String vestaboardKey = System.getenv("VESTABOARD_KEY");
		String clientID = System.getenv("CLIENT_ID");
		String clientSecret = System.getenv("CLIENT_SECRET");
		String redirectURL = System.getenv("REDIRECT_URL");
		return new SpotifyIntegration(clientID, clientSecret, redirectURL, vestaboardKey, stateBroadcastService);
	}

	// Enabling CORS
	@Bean
	public WebMvcConfigurer corsConfiguration() {
		return new WebMvcConfigurer() {
			@Override
			public void addCorsMappings(CorsRegistry registry) {
				registry.addMapping("/request_song").allowedOrigins("http://localhost:3000");
				registry.addMapping("/current").allowedOrigins("http://localhost:3000");
				registry.addMapping("/get_auth_url").allowedOrigins("http://localhost:3000");
				registry.addMapping("/send_auth_token").allowedOrigins("http://localhost:3000");
				registry.addMapping("/auth_status").allowedOrigins("http://localhost:3000");
				registry.addMapping("/*").allowedOrigins("http://localhost:3000");
			}
		};
	}
}
