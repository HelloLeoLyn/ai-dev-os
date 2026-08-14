package com.aidevos.orchestrator.network;

import java.time.Instant;

public class ProxySettings {
	public static final String SETTINGS_ID = "runtime-network";
	private String settingsId = SETTINGS_ID;
	private ProxyMode mode = ProxyMode.SYSTEM;
	private ProxyHostStrategy hostStrategy = ProxyHostStrategy.MANUAL;
	private String httpProxy;
	private String httpsProxy;
	private String socks5Proxy;
	private String noProxy;
	private long version;
	private Instant updatedAt = Instant.now();
	public String getSettingsId(){return settingsId;} public void setSettingsId(String v){settingsId=v;}
	public ProxyMode getMode(){return mode;} public void setMode(ProxyMode v){mode=v;}
	public ProxyHostStrategy getHostStrategy(){return hostStrategy;} public void setHostStrategy(ProxyHostStrategy v){hostStrategy=v;}
	public String getHttpProxy(){return httpProxy;} public void setHttpProxy(String v){httpProxy=v;}
	public String getHttpsProxy(){return httpsProxy;} public void setHttpsProxy(String v){httpsProxy=v;}
	public String getSocks5Proxy(){return socks5Proxy;} public void setSocks5Proxy(String v){socks5Proxy=v;}
	public String getNoProxy(){return noProxy;} public void setNoProxy(String v){noProxy=v;}
	public long getVersion(){return version;} public void setVersion(long v){version=v;}
	public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
}
