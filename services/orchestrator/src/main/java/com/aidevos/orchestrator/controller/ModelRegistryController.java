package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.config.AgentConfigLoader;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.modelregistry.ModelDefinition;
import com.aidevos.orchestrator.modelregistry.ModelRegistryService;
import com.aidevos.orchestrator.modelregistry.ProviderDefinition;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runtime model/provider registry API. Create/update/enable/disable only; V1
 * has no physical delete. Responses never contain secret values: providers
 * only expose a credentialRef name that references a server-side environment
 * variable or secret.
 */
@RestController
@RequestMapping("/api/model-registry")
public class ModelRegistryController {

	private final ModelRegistryService registry;
	private final AgentConfigLoader agentConfigLoader;

	public ModelRegistryController(ModelRegistryService registry,
			AgentConfigLoader agentConfigLoader) {
		this.registry = registry;
		this.agentConfigLoader = agentConfigLoader;
	}

	@GetMapping("/default-model")
	public DefaultModelResponse defaultModel() {
		String modelId = null;
		for (AgentDefinition agent : agentConfigLoader.loadAgents()) {
			if ("coder".equals(agent.getName())) {
				Object model = agent.getExecutorConfig().get("model");
				if (model instanceof String text && !text.isBlank()) {
					modelId = text;
				}
				break;
			}
		}
		return new DefaultModelResponse(modelId);
	}

	@GetMapping("/providers/{id}/status")
	public ResponseEntity<ModelRegistryService.ProviderStatus> providerStatus(
			@PathVariable String id) {
		ModelRegistryService.ProviderStatus status = registry.providerStatus(id);
		return status == null ? ResponseEntity.notFound().build()
			: ResponseEntity.ok(status);
	}

	@GetMapping("/providers")
	public List<ProviderDefinition> listProviders() {
		return registry.listProviders();
	}

	@GetMapping("/providers/{id}")
	public ResponseEntity<ProviderDefinition> getProvider(@PathVariable String id) {
		ProviderDefinition provider = registry.getProvider(id);
		return provider == null ? ResponseEntity.notFound().build()
			: ResponseEntity.ok(provider);
	}

	@PostMapping("/providers")
	public ResponseEntity<?> createProvider(@RequestBody ProviderDefinition definition) {
		try {
			return ResponseEntity.status(HttpStatus.CREATED).body(registry.createProvider(definition));
		}
		catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(exception.getMessage());
		}
		catch (IllegalStateException exception) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
		}
	}

	@PutMapping("/providers/{id}")
	public ResponseEntity<?> updateProvider(@PathVariable String id,
			@RequestBody ProviderDefinition definition) {
		try {
			return ResponseEntity.ok(registry.updateProvider(id, definition));
		}
		catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(exception.getMessage());
		}
	}

	@PostMapping("/providers/{id}/enabled")
	public ResponseEntity<?> setProviderEnabled(@PathVariable String id,
			@RequestBody EnableRequest request) {
		try {
			return ResponseEntity.ok(registry.setProviderEnabled(id, request.enabled()));
		}
		catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(exception.getMessage());
		}
	}

	@GetMapping("/models")
	public List<ModelDefinition> listModels() {
		return registry.listModels();
	}

	@GetMapping("/models/{id}")
	public ResponseEntity<ModelDefinition> getModel(@PathVariable String id) {
		ModelDefinition model = registry.getModel(id);
		return model == null ? ResponseEntity.notFound().build()
			: ResponseEntity.ok(model);
	}

	@PostMapping("/models")
	public ResponseEntity<?> createModel(@RequestBody ModelDefinition definition) {
		try {
			return ResponseEntity.status(HttpStatus.CREATED).body(registry.createModel(definition));
		}
		catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(exception.getMessage());
		}
		catch (IllegalStateException exception) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
		}
	}

	@PutMapping("/models/{id}")
	public ResponseEntity<?> updateModel(@PathVariable String id,
			@RequestBody ModelDefinition definition) {
		try {
			return ResponseEntity.ok(registry.updateModel(id, definition));
		}
		catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(exception.getMessage());
		}
	}

	@PostMapping("/models/{id}/enabled")
	public ResponseEntity<?> setModelEnabled(@PathVariable String id,
			@RequestBody EnableRequest request) {
		try {
			return ResponseEntity.ok(registry.setModelEnabled(id, request.enabled()));
		}
		catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(exception.getMessage());
		}
	}

	public record EnableRequest(boolean enabled) {
	}

	public record DefaultModelResponse(String modelId) {
	}
}
