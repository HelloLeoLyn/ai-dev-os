package com.aidevos.orchestrator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.aidevos.orchestrator.remote.RemoteGitService;
import com.aidevos.orchestrator.remote.RemotePushApprovalService;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "aidevos.persistence.type=in-memory")
@ActiveProfiles("test")
class OrchestratorApplicationTests {

	@Autowired
	private RemoteGitService remoteGitService;
	@Autowired
	private RemotePushApprovalService remotePushApprovalService;

	@Test
	void contextLoads() {
	}

	@Test
	void remoteGitServiceWiresRealRemotePushApprovalService() {
		assertNotNull(remotePushApprovalService);
		assertTrue(remoteGitService.requiresRemotePushApproval());
	}

}
