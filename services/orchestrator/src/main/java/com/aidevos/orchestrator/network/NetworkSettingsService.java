package com.aidevos.orchestrator.network;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.aidevos.orchestrator.audit.*;
import org.springframework.stereotype.Service;

@Service
public class NetworkSettingsService {
	public static final String MASK="********";
	private final ProxyEnvironmentService environment;private final WindowsHostResolver resolver;private final AuditService audit;
	public NetworkSettingsService(ProxyEnvironmentService e,WindowsHostResolver r,AuditService a){environment=e;resolver=r;audit=a;}
	public ProxySettingsView get(){return view(environment.current(),true);}
	public ProxySettingsView save(ProxySettings input){mergeMasked(input,environment.current());ProxySettings saved=environment.save(input);audit.record(new EventRecord(UUID.randomUUID().toString(),EventType.NETWORK_SETTINGS_UPDATED,Instant.now(),0,"network-settings",ProxySettings.SETTINGS_ID,null,saved.getMode().name(),null,null,null,null,null,null,null,null,null,null,null,"USER","network-settings","Runtime network settings updated",Map.of("mode",saved.getMode().name(),"hostStrategy",saved.getHostStrategy().name(),"version",saved.getVersion()),"NETWORK_SETTINGS_UPDATED:"+saved.getVersion(),1));return view(saved,true);}
	private void mergeMasked(ProxySettings input,ProxySettings old){input.setHttpProxy(merge(input.getHttpProxy(),old.getHttpProxy()));input.setHttpsProxy(merge(input.getHttpsProxy(),old.getHttpsProxy()));input.setSocks5Proxy(merge(input.getSocks5Proxy(),old.getSocks5Proxy()));}
	private String merge(String value,String old){return value!=null&&value.contains(MASK)?old:value;}
	private ProxySettingsView view(ProxySettings s,boolean resolve){String host=null,error=null;if(resolve&&s.getMode()==ProxyMode.CUSTOM&&s.getHostStrategy()==ProxyHostStrategy.AUTO_WINDOWS_HOST)try{host=resolver.resolve();}catch(ProxyResolutionException e){error=ProxyResolutionException.CODE;}return new ProxySettingsView(s.getMode(),s.getHostStrategy(),redact(s.getHttpProxy()),redact(s.getHttpsProxy()),redact(s.getSocks5Proxy()),safeNoProxy(s),s.getVersion(),s.getUpdatedAt(),host,error);}
	private String safeNoProxy(ProxySettings s){try{return environment.environment().get("NO_PROXY");}catch(ProxyResolutionException e){return "localhost,127.0.0.1,::1";}}
	static String redact(String value){if(value==null||value.isBlank())return value;try{URI u=URI.create(value);if(u.getUserInfo()==null)return value;return new URI(u.getScheme(),MASK,u.getHost(),u.getPort(),u.getPath(),u.getQuery(),u.getFragment()).toString();}catch(Exception e){return "[INVALID_PROXY_URL]";}}
}
