package com.aidevos.orchestrator.network;
public class ProxyResolutionException extends RuntimeException {
	public static final String CODE = "AUTO_WINDOWS_HOST_RESOLUTION_FAILED";
	public ProxyResolutionException() { super(CODE); }
}
