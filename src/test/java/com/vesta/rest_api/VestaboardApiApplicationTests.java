package com.vesta.rest_api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration tests for VestaboardApiApplication.
 * 
 * Note: These tests require environment variables to be set:
 * - VESTABOARD_KEY
 * - CLIENT_ID
 * - CLIENT_SECRET
 * - REDIRECT_URL
 * 
 * The test is disabled by default unless the VESTABOARD_KEY environment variable is set.
 * To run these tests, ensure all required environment variables are configured.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "VESTABOARD_KEY", matches = ".+")
class VestaboardApiApplicationTests {

	@Test
	void contextLoads() {
		// This test verifies that the Spring context loads successfully
		// when all required environment variables are properly configured.
	}

}
