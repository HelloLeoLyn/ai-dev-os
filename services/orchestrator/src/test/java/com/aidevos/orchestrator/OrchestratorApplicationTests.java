package com.aidevos.orchestrator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "aidevos.persistence.type=in-memory")
@ActiveProfiles("test")
class OrchestratorApplicationTests {

	@Test
	void contextLoads() {
	}

}
