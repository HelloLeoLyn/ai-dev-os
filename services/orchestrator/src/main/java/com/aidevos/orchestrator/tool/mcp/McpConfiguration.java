package com.aidevos.orchestrator.tool.mcp;

import com.aidevos.orchestrator.tool.ToolProvider;
import com.aidevos.orchestrator.audit.AuditService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class McpConfiguration {

	@Bean(destroyMethod = "close")
	@ConditionalOnProperty(prefix = "tools.mcp", name = "enabled", havingValue = "true")
	ToolProvider mcpToolProvider(McpProperties properties, ObjectMapper objectMapper,
			AuditService auditService) {
		McpSession session = new McpStdioSession(properties.getCommand(),
			properties.getWorkingDirectory(), objectMapper);
		McpClient client = new McpClient(session, objectMapper, properties.getRequestTimeout());
		return new McpToolProvider(properties.getProviderId(), client, objectMapper, auditService);
	}
}
