package com.aidevos.orchestrator.modelregistry;

import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * Single trusted resolver for task model selection. Explicit selections and
 * agent defaults both resolve to a concrete ResolvedModel backed by the
 * registry; every failure mode is fail-closed with a stable code. There is no
 * silent fallback to any other provider.
 */
@Service
public class ModelResolver {

	private static final Set<String> SUPPORTED_EXECUTORS = Set.of("codex");

	private final ProviderDefinitionRepository providerRepository;
	private final ModelDefinitionRepository modelRepository;

	public ModelResolver(ProviderDefinitionRepository providerRepository,
			ModelDefinitionRepository modelRepository) {
		this.providerRepository = providerRepository;
		this.modelRepository = modelRepository;
	}

	/**
	 * Resolves the effective model. When requestedModelId is blank the agent
	 * default ModelDefinition id is used (Auto semantics); a missing default
	 * fails closed with MODEL_NOT_FOUND.
	 */
	public ResolvedModel resolve(String requestedModelId, String agentDefaultModelId) {
		String selected = requestedModelId == null || requestedModelId.isBlank()
			? agentDefaultModelId : requestedModelId.trim();
		if (selected == null || selected.isBlank()) {
			throw new ModelResolutionException(ModelResolutionException.Code.MODEL_NOT_FOUND,
				"No model requested and the agent has no default model");
		}
		ModelDefinition model = modelRepository.get(selected);
		if (model == null) {
			throw new ModelResolutionException(ModelResolutionException.Code.MODEL_NOT_FOUND,
				"Model definition not found: " + selected);
		}
		if (!model.isEnabled()) {
			throw new ModelResolutionException(ModelResolutionException.Code.MODEL_DISABLED,
				"Model is disabled: " + selected);
		}
		if (!SUPPORTED_EXECUTORS.contains(model.getExecutorType())) {
			throw new ModelResolutionException(ModelResolutionException.Code.UNSUPPORTED_EXECUTOR,
				"Unsupported executor type: " + model.getExecutorType() + " for model " + selected);
		}
		ProviderDefinition provider = providerRepository.get(model.getProviderId());
		if (provider == null) {
			throw new ModelResolutionException(ModelResolutionException.Code.PROVIDER_NOT_FOUND,
				"Provider not found: " + model.getProviderId() + " for model " + selected);
		}
		if (!provider.isEnabled()) {
			throw new ModelResolutionException(ModelResolutionException.Code.PROVIDER_DISABLED,
				"Provider is disabled: " + model.getProviderId() + " for model " + selected);
		}
		String credentialRef = provider.getCredentialRef();
		if (credentialRef != null && !credentialRef.isBlank()
				&& lookupEnv(credentialRef) == null) {
			throw new ModelResolutionException(ModelResolutionException.Code.CREDENTIAL_MISSING,
				"Credential reference " + credentialRef + " is not set in the environment");
		}
		return new ResolvedModel(requestedModelId, model.getModelId(), provider.getProviderId(),
			model.getExecutorType(), provider.getBaseUrl(), credentialRef);
	}

	/**
	 * Verifies the resolved executor matches the executor that will run it.
	 */
	public void requireExecutor(ResolvedModel resolved, String executorType) {
		if (resolved == null || !resolved.executorType().equals(executorType)) {
			throw new ModelResolutionException(
				ModelResolutionException.Code.MODEL_EXECUTOR_MISMATCH,
				"Resolved executor " + (resolved == null ? "null" : resolved.executorType())
					+ " does not match executor " + executorType);
		}
	}

	protected String lookupEnv(String name) {
		return System.getenv(name);
	}
}
