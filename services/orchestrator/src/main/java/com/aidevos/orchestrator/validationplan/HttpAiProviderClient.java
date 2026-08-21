package com.aidevos.orchestrator.validationplan;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import com.aidevos.orchestrator.network.NetworkAwareHttpClientFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * HTTP 版 AiProviderClient：OpenAI 兼容 /chat/completions。
 * 复用 NetworkAwareHttpClientFactory（代理/网络感知），不建立新 framework。
 */
@Component
public class HttpAiProviderClient implements AiProviderClient {

	private static final Duration TIMEOUT = Duration.ofSeconds(60);

	private final NetworkAwareHttpClientFactory httpClientFactory;
	private final ObjectMapper mapper;

	public HttpAiProviderClient(NetworkAwareHttpClientFactory httpClientFactory,
			ObjectMapper mapper) {
		this.httpClientFactory = httpClientFactory;
		this.mapper = mapper;
	}

	@Override
	public String chatCompletion(String baseUrl, String apiKey, String model,
			String systemPrompt, String userPrompt) {
		try {
			String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions"
				: baseUrl + "/chat/completions";
			String body = mapper.writeValueAsString(Map.of(
				"model", model,
				"messages", java.util.List.of(
					Map.of("role", "system", "content", systemPrompt),
					Map.of("role", "user", "content", userPrompt)),
				"temperature", 0,
				"response_format", Map.of("type", "json_object")));
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(TIMEOUT)
				.header("Content-Type", "application/json")
				.header("Authorization", "Bearer " + apiKey)
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
			HttpResponse<String> response = httpClientFactory.client(TIMEOUT)
				.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				throw new IllegalStateException("AI provider returned HTTP "
					+ response.statusCode());
			}
			Map<?, ?> json = mapper.readValue(response.body(), Map.class);
			java.util.List<?> choices = (java.util.List<?>) json.get("choices");
			if (choices == null || choices.isEmpty()) {
				throw new IllegalStateException("AI provider returned no choices");
			}
			Map<?, ?> message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
			Object content = message == null ? null : message.get("content");
			if (content == null) {
				throw new IllegalStateException("AI provider returned empty content");
			}
			return String.valueOf(content);
		}
		catch (Exception exception) {
			if (exception instanceof RuntimeException runtime) {
				throw runtime;
			}
			throw new IllegalStateException("AI provider call failed: " + exception.getMessage(),
				exception);
		}
	}
}
