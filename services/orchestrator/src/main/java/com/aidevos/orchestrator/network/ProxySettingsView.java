package com.aidevos.orchestrator.network;
import java.time.Instant;
public record ProxySettingsView(ProxyMode mode,ProxyHostStrategy hostStrategy,String httpProxy,String httpsProxy,String socks5Proxy,String noProxy,long version,Instant updatedAt,String resolvedWindowsHost,String errorCode) {}
