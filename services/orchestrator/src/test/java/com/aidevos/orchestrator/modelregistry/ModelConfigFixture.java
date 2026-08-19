package com.aidevos.orchestrator.modelregistry;

import com.aidevos.orchestrator.modelrouter.ModelConfig;
import com.aidevos.orchestrator.modelrouter.ModelConfigLoader;

/**
 * Loads the real models.yaml through the production loader so registry tests
 * exercise the actual seed source.
 */
class ModelConfigFixture {

	private final ModelConfig config = new ModelConfigLoader().load();

	ModelConfig config() {
		return config;
	}
}
