package com.aidevos.orchestrator.modelregistry;

import com.aidevos.orchestrator.modelrouter.ModelConfigLoader;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Seeds the model/provider registry from models.yaml on startup. Insert-only:
 * entries that already exist (including user-modified or disabled ones) are
 * left untouched.
 */
@Component
public class ModelRegistryBootstrap {

	private final ModelRegistryService registryService;
	private final ModelConfigLoader configLoader;

	public ModelRegistryBootstrap(ModelRegistryService registryService,
			ModelConfigLoader configLoader) {
		this.registryService = registryService;
		this.configLoader = configLoader;
	}

	@PostConstruct
	public void seed() {
		registryService.seedFrom(configLoader.load());
	}
}
