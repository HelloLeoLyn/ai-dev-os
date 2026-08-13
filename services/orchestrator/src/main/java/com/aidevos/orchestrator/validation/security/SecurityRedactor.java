package com.aidevos.orchestrator.validation.security;
import java.util.regex.Pattern; import org.springframework.stereotype.Component;
@Component public class SecurityRedactor {
	private static final Pattern JSON_SECRET=Pattern.compile("(?i)(\\\"(?:secret|match|password|api[_-]?key|token)\\\"\\s*:\\s*\\\")([^\\\"]*)(\\\")");
	private static final Pattern TEXT_SECRET=Pattern.compile("(?i)((?:secret|password|api[_-]?key|token)\\s*[:=]\\s*)([^\\s,}]+)");
	public String redact(String value){if(value==null)return null;String json=JSON_SECRET.matcher(value).replaceAll("$1[REDACTED]$3");return TEXT_SECRET.matcher(json).replaceAll("$1[REDACTED]");}
	public String mask(String value){if(value==null||value.isBlank())return "[REDACTED]"; return value.substring(0,Math.min(4,value.length()))+"****";}
}
