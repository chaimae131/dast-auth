package com.CAN.auth_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test") // Optionnel : pour utiliser un application-test.properties
class AuthServiceApplicationTests {
	@Test
	void contextLoads() {
	}
}
