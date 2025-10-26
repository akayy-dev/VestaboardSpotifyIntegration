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

import com.vesta.rest_api.patterns.SpotifySession;

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
            System.err.println("ENV: CLIENT_ID=" + System.getenv("CLIENT_ID") + " REDIRECT_URL=" + System.getenv("REDIRECT_URL"));
            // beans
            System.err.println("Has SpotifySession bean: " + ctx.containsBean("getSpotifySession"));
            System.err.println("Has SpotifyIntegration bean: " + ctx.containsBean("getSpotifyIntegration"));
            try {
                SpotifySession ss = ctx.getBean(SpotifySession.class);
                System.err.println("SpotifySession bean instance: " + ss);
                try {
                    System.err.println("SpotifySession.getAuthURL() => " + ss.getAuthURL());
                } catch (Throwable t) {
                    System.err.println("SpotifySession.getAuthURL() failed: " + t.getClass().getSimpleName() + " - " + t.getMessage());
                }
            } catch (Exception e) {
                System.err.println("Could not get SpotifySession from context: " + e.getMessage());
            }
            try {
                Object si = ctx.getBean(SpotifyIntegration.class);
                System.err.println("SpotifyIntegration bean instance: " + si);
            } catch (Exception e) {
                System.err.println("Could not get SpotifyIntegration from context: " + e.getMessage());
            }
        };
    }

	@Bean
	public SpotifySession getSpotifySession() {
		String clientID = System.getenv("CLIENT_ID");
		String clientSecret = System.getenv("CLIENT_SECRET");
		String redirectURL = System.getenv("REDIRECT_URL");
		return new SpotifySession(clientID, clientSecret, redirectURL);
	}

	@Bean
	public SpotifyIntegration getSpotifyIntegration(SpotifySession spotifySession) {
		String vestaboardKey = System.getenv("VESTABOARD_KEY");
		String clientID = System.getenv("CLIENT_ID");
		String clientSecret = System.getenv("CLIENT_SECRET");
		String redirectURL = System.getenv("REDIRECT_URL");
		return new SpotifyIntegration(clientID, clientSecret, redirectURL, vestaboardKey);
		// return new SpotifyIntegration(spotifySession, vestaboardKey);
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
