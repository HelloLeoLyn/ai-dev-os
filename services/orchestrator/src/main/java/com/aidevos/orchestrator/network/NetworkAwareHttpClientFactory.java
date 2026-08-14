package com.aidevos.orchestrator.network;

import java.net.*;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class NetworkAwareHttpClientFactory {
	private final ProxyEnvironmentService proxyEnvironment; private final Map<String,HttpClient> clients=new ConcurrentHashMap<>();
	public NetworkAwareHttpClientFactory(ProxyEnvironmentService proxyEnvironment){this.proxyEnvironment=proxyEnvironment;}
	public HttpClient client(Duration timeout){ProxySettings s=proxyEnvironment.current();String key=s.getVersion()+":"+timeout.toMillis();return clients.computeIfAbsent(key,k->build(s,timeout));}
	public NetworkRoute route(URI uri){ProxySettings s=proxyEnvironment.current();if(proxyEnvironment.bypass(uri.getHost())||s.getMode()==ProxyMode.DIRECT)return NetworkRoute.DIRECT;if(s.getMode()==ProxyMode.CUSTOM)return NetworkRoute.PROXY;return NetworkRoute.SYSTEM;}
	private HttpClient build(ProxySettings s,Duration timeout){
		HttpClient.Builder builder=HttpClient.newBuilder().connectTimeout(timeout);
		if(s.getMode()==ProxyMode.DIRECT) builder.proxy(new RuntimeProxySelector(null,null,null,proxyEnvironment));
		else if(s.getMode()==ProxyMode.SYSTEM) {ProxySelector system=ProxySelector.getDefault();if(system!=null)builder.proxy(system);}
		else {Map<String,String> env=proxyEnvironment.environment();URI http=uri(env.get("HTTP_PROXY"));URI https=uri(env.get("HTTPS_PROXY"));URI socks=uri(env.get("ALL_PROXY"));builder.proxy(new RuntimeProxySelector(http,https,socks,proxyEnvironment));builder.authenticator(new ProxyAuthenticator(Arrays.asList(http,https,socks)));}
		return builder.build();
	}
	private URI uri(String value){return value==null||value.isBlank()?null:URI.create(value);}
	private static final class RuntimeProxySelector extends ProxySelector {private final URI http,https,socks;private final ProxyEnvironmentService bypass;RuntimeProxySelector(URI h,URI s,URI a,ProxyEnvironmentService b){http=h;https=s;socks=a;bypass=b;}public List<Proxy> select(URI target){if(bypass.bypass(target.getHost()))return List.of(Proxy.NO_PROXY);URI selected="https".equalsIgnoreCase(target.getScheme())?(https!=null?https:http):http;if(selected==null)selected=socks;if(selected==null)return List.of(Proxy.NO_PROXY);Proxy.Type type=selected.getScheme()!=null&&selected.getScheme().toLowerCase().startsWith("socks")?Proxy.Type.SOCKS:Proxy.Type.HTTP;return List.of(new Proxy(type,new InetSocketAddress(selected.getHost(),selected.getPort())));}public void connectFailed(URI u,SocketAddress a,java.io.IOException e){}}
	private static final class ProxyAuthenticator extends Authenticator {private final List<URI> proxies;ProxyAuthenticator(List<URI> p){proxies=p;}protected PasswordAuthentication getPasswordAuthentication(){if(getRequestorType()!=RequestorType.PROXY)return null;for(URI u:proxies)if(u!=null&&u.getHost().equalsIgnoreCase(getRequestingHost())&&u.getPort()==getRequestingPort()&&u.getUserInfo()!=null){String[] parts=u.getUserInfo().split(":",2);return new PasswordAuthentication(parts[0],(parts.length>1?parts[1]:"").toCharArray());}return null;}}
}
