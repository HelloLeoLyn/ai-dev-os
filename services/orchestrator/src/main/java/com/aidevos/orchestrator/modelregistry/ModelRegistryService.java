package com.aidevos.orchestrator.modelregistry;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.aidevos.orchestrator.modelrouter.ModelConfig;
import com.aidevos.orchestrator.modelrouter.ModelProvider;
import org.springframework.stereotype.Service;

/**
 * CRUD + enable/disable + idempotent seeding for the model/provider registry.
 * The registry is the runtime-configurable source of truth for model routing;
 * models.yaml is only used to seed initial data and never overwrites existing
 * registry entries. Secret values are never part of the registry: only a
 * credentialRef name is stored.
 */
@Service
public class ModelRegistryService {

	private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]*");
	private static final Pattern CREDENTIAL_REF_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]*");

	private final ProviderDefinitionRepository providerRepository;
	private final ModelDefinitionRepository modelRepository;

	public ModelRegistryService(ProviderDefinitionRepository providerRepository,
			ModelDefinitionRepository modelRepository) {
		this.providerRepository = providerRepository;
		this.modelRepository = modelRepository;
	}

	public List<ProviderDefinition> listProviders() {
		return providerRepository.getAll();
	}

	public ProviderDefinition getProvider(String providerId) {
		requireId(providerId, "providerId");
		return providerRepository.get(providerId);
	}

	public ProviderDefinition createProvider(ProviderDefinition definition) {
		validateProvider(definition);
		if (providerRepository.get(definition.getProviderId()) != null) {
			throw new IllegalStateException("Provider already exists: " + definition.getProviderId());
		}
		providerRepository.save(copy(definition));
		return providerRepository.get(definition.getProviderId());
	}

	public ProviderDefinition updateProvider(String providerId, ProviderDefinition definition) {
		requireId(providerId, "providerId");
		validateProvider(definition);
		if (!providerId.equals(definition.getProviderId())) {
			throw new IllegalArgumentException("providerId in body must match path");
		}
		ProviderDefinition existing = providerRepository.get(providerId);
		if (existing == null) {
			throw new IllegalArgumentException("Provider not found: " + providerId);
		}
		providerRepository.save(copy(definition));
		return providerRepository.get(providerId);
	}

	public ProviderDefinition setProviderEnabled(String providerId, boolean enabled) {
		requireId(providerId, "providerId");
		ProviderDefinition existing = providerRepository.get(providerId);
		if (existing == null) {
			throw new IllegalArgumentException("Provider not found: " + providerId);
		}
		existing.setEnabled(enabled);
		providerRepository.save(existing);
		return providerRepository.get(providerId);
	}

	public List<ModelDefinition> listModels() {
		return modelRepository.getAll();
	}

	public ModelDefinition getModel(String modelId) {
		requireId(modelId, "modelId");
		return modelRepository.get(modelId);
	}

	public ModelDefinition createModel(ModelDefinition definition) {
		validateModel(definition);
		if (modelRepository.get(definition.getModelId()) != null) {
			throw new IllegalStateException("Model already exists: " + definition.getModelId());
		}
		modelRepository.save(copy(definition));
		return modelRepository.get(definition.getModelId());
	}

	public ModelDefinition updateModel(String modelId, ModelDefinition definition) {
		requireId(modelId, "modelId");
		validateModel(definition);
		if (!modelId.equals(definition.getModelId())) {
			throw new IllegalArgumentException("modelId in body must match path");
		}
		ModelDefinition existing = modelRepository.get(modelId);
		if (existing == null) {
			throw new IllegalArgumentException("Model not found: " + modelId);
		}
		modelRepository.save(copy(definition));
		return modelRepository.get(modelId);
	}

	public ModelDefinition setModelEnabled(String modelId, boolean enabled) {
		requireId(modelId, "modelId");
		ModelDefinition existing = modelRepository.get(modelId);
		if (existing == null) {
			throw new IllegalArgumentException("Model not found: " + modelId);
		}
		existing.setEnabled(enabled);
		modelRepository.save(existing);
		return modelRepository.get(modelId);
	}

	/**
	 * Runtime credential status for a provider. Only the credential reference
	 * name and a configured/missing boolean are exposed; the secret value is
	 * never read from or written to the registry.
	 */
	public ProviderStatus providerStatus(String providerId) {
		requireId(providerId, "providerId");
		ProviderDefinition provider = providerRepository.get(providerId);
		if (provider == null) {
			return null;
		}
		String credentialRef = blankToNull(provider.getCredentialRef());
		boolean configured = credentialRef != null && environmentValue(credentialRef) != null;
		return new ProviderStatus(provider.getProviderId(), credentialRef, configured);
	}

	protected String environmentValue(String name) {
		return System.getenv(name);
	}

	public record ProviderStatus(String providerId, String credentialRef,
			boolean credentialConfigured) {
	}

	/**
	 * Seeds providers and models from models.yaml. Insert-only and idempotent:
	 * existing registry entries (including disabled ones) are never
	 * overwritten, so user edits survive restarts.
	 */
	public synchronized int seedFrom(ModelConfig config) {
		if (config == null) {
			return 0;
		}
		int seeded = 0;
		for (ModelProvider provider : config.providers()) {
			if (providerRepository.get(provider.getProviderId()) == null) {
				ProviderDefinition definition = new ProviderDefinition();
				definition.setProviderId(provider.getProviderId());
				definition.setDisplayName(provider.getName());
				definition.setBaseUrl(defaultBaseUrl(provider.getProviderId()));
				definition.setCredentialRef(defaultCredentialRef(provider.getProviderId()));
				definition.setEnabled(provider.isEnabled());
				providerRepository.save(definition);
				seeded++;
			}
			String model = provider.getModel();
			if (model != null && !model.isBlank() && modelRepository.get(model) == null) {
				ModelDefinition definition = new ModelDefinition();
				definition.setModelId(model);
				definition.setDisplayName(provider.getName() + " / " + model);
				definition.setProviderId(provider.getProviderId());
				definition.setExecutorType(executorType(provider));
				definition.setEnabled(provider.isEnabled());
				definition.setCapabilities(List.of());
				modelRepository.save(definition);
				seeded++;
			}
		}
		// The default coding model referenced by agents.yaml (coder.codex.model)
		// must exist in the registry even when models.yaml does not declare it.
		ProviderDefinition deepSeekProvider = providerRepository.get("deepseek");
		if (deepSeekProvider != null && modelRepository.get("deepseek-v4-flash") == null) {
			ModelDefinition definition = new ModelDefinition();
			definition.setModelId("deepseek-v4-flash");
			definition.setDisplayName("DeepSeek / deepseek-v4-flash");
			definition.setProviderId("deepseek");
			definition.setExecutorType("codex");
			definition.setEnabled(deepSeekProvider.isEnabled());
			definition.setCapabilities(List.of("coding"));
			modelRepository.save(definition);
			seeded++;
		}
		return seeded;
	}

	private String defaultBaseUrl(String providerId) {
		if ("deepseek".equals(providerId)) {
			return "https://api.deepseek.com";
		}
		return null;
	}

	private String defaultCredentialRef(String providerId) {
		if ("deepseek".equals(providerId)) {
			return "DEEPSEEK_API_KEY";
		}
		return null;
	}

	private String executorType(ModelProvider provider) {
		return "AGENT".equalsIgnoreCase(provider.getType())
			? provider.getProviderId() : "codex";
	}

	private void validateProvider(ProviderDefinition definition) {
		if (definition == null) {
			throw new IllegalArgumentException("Provider definition is required");
		}
		requireId(definition.getProviderId(), "providerId");
		requireText(definition.getDisplayName(), "displayName");
		String baseUrl = blankToNull(definition.getBaseUrl());
		definition.setBaseUrl(baseUrl);
		if (baseUrl != null && !isHttpUrl(baseUrl)) {
			throw new IllegalArgumentException("baseUrl must be a valid http(s) URL");
		}
		String credentialRef = blankToNull(definition.getCredentialRef());
		definition.setCredentialRef(credentialRef);
		if (credentialRef != null && !CREDENTIAL_REF_PATTERN.matcher(credentialRef).matches()) {
			throw new IllegalArgumentException(
				"credentialRef must be an environment variable / secret reference, e.g. DEEPSEEK_API_KEY");
		}
	}

	private void validateModel(ModelDefinition definition) {
		if (definition == null) {
			throw new IllegalArgumentException("Model definition is required");
		}
		requireId(definition.getModelId(), "modelId");
		requireText(definition.getDisplayName(), "displayName");
		requireId(definition.getProviderId(), "providerId");
		requireText(definition.getExecutorType(), "executorType");
		if (providerRepository.get(definition.getProviderId()) == null) {
			throw new IllegalArgumentException("Provider not found: " + definition.getProviderId());
		}
		if (definition.getCapabilities() == null) {
			definition.setCapabilities(List.of());
		}
	}

	private void requireId(String value, String field) {
		requireText(value, field);
		if (!ID_PATTERN.matcher(value.trim()).matches()) {
			throw new IllegalArgumentException(field + " contains unsupported characters: " + value.trim());
		}
	}

	private void requireText(String value, String field) {
		if (value == null || value.trim().isBlank()) {
			throw new IllegalArgumentException(field + " is required");
		}
	}

	private boolean isHttpUrl(String value) {
		String lower = value.trim().toLowerCase(Locale.ROOT);
		return lower.startsWith("http://") || lower.startsWith("https://");
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private ProviderDefinition copy(ProviderDefinition source) {
		ProviderDefinition target = new ProviderDefinition();
		target.setProviderId(source.getProviderId());
		target.setDisplayName(source.getDisplayName());
		target.setBaseUrl(source.getBaseUrl());
		target.setCredentialRef(source.getCredentialRef());
		target.setEnabled(source.isEnabled());
		return target;
	}

	private ModelDefinition copy(ModelDefinition source) {
		ModelDefinition target = new ModelDefinition();
		target.setModelId(source.getModelId());
		target.setDisplayName(source.getDisplayName());
		target.setProviderId(source.getProviderId());
		target.setExecutorType(source.getExecutorType());
		target.setEnabled(source.isEnabled());
		target.setCapabilities(source.getCapabilities());
		return target;
	}
}
