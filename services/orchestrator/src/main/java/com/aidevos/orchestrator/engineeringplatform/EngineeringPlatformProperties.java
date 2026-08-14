package com.aidevos.orchestrator.engineeringplatform;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "engineering-platform")
public class EngineeringPlatformProperties {

	private boolean enabled;
	private String executable = "";
	private String platformRoot = "";
	private String version = "v0.3.0";

	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
	public String getExecutable() { return executable; }
	public void setExecutable(String executable) { this.executable = executable; }
	public String getPlatformRoot() { return platformRoot; }
	public void setPlatformRoot(String platformRoot) { this.platformRoot = platformRoot; }
	public String getVersion() { return version; }
	public void setVersion(String version) { this.version = version; }
}
