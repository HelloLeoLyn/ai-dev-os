package com.aidevos.orchestrator.validationplan;

/**
 * AI Provider Chat 调用边界（OpenAI 兼容 chat/completions）。
 * 独立小接口，便于测试注入 fake；不建立新 LLM framework。
 */
public interface AiProviderClient {

	/**
	 * 调用 provider chat completions，返回 assistant content。
	 *
	 * @param baseUrl   provider base url（如 https://api.deepseek.com）
	 * @param apiKey    credential 值（环境变量解析后）
	 * @param model     resolved model id
	 * @param systemPrompt 系统提示（结构化输出约束）
	 * @param userPrompt    压缩后的验证输入
	 * @throws RuntimeException 任何 provider/timeout/rate limit/401 失败（调用方 fallback）
	 */
	String chatCompletion(String baseUrl, String apiKey, String model,
			String systemPrompt, String userPrompt);
}
