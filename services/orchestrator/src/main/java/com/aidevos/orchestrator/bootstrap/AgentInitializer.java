package com.aidevos.orchestrator.bootstrap;

import com.aidevos.orchestrator.config.AgentConfigLoader;
import com.aidevos.orchestrator.manager.AgentManager;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class AgentInitializer implements ApplicationRunner {

	private final AgentConfigLoader agentConfigLoader;
	private final AgentManager agentManager;

	public AgentInitializer(AgentConfigLoader agentConfigLoader, AgentManager agentManager) {
		this.agentConfigLoader = agentConfigLoader;
		this.agentManager = agentManager;
	}

	@Override
	public void run(ApplicationArguments args) {
		agentConfigLoader.loadAgents().forEach(agentManager::register);
	}
}
