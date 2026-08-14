package com.aidevos.orchestrator.network;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class ProxyEnvironmentService {
	public static final Set<String> PROXY_KEYS=Set.of("HTTP_PROXY","HTTPS_PROXY","ALL_PROXY","http_proxy","https_proxy","all_proxy");
	private static final List<String> REQUIRED_BYPASS=List.of("localhost","127.0.0.1","::1");
	private final NetworkSettingsRepository repository; private final WindowsHostResolver hostResolver;
	public ProxyEnvironmentService(NetworkSettingsRepository repository,@Lazy WindowsHostResolver hostResolver){this.repository=repository;this.hostResolver=hostResolver;}
	public ProxySettings current(){ProxySettings s=repository.get();return s==null?new ProxySettings():s;}
	public synchronized ProxySettings save(ProxySettings input){validate(input);ProxySettings old=current();input.setSettingsId(ProxySettings.SETTINGS_ID);input.setVersion(old.getVersion()+1);input.setUpdatedAt(Instant.now());repository.save(input);return input;}
	public Map<String,String> environment(){
		ProxySettings s=current(); LinkedHashMap<String,String> env=new LinkedHashMap<>();
		String noProxy=noProxy(s.getNoProxy());
		if(s.getMode()==ProxyMode.CUSTOM){
			String host=s.getHostStrategy()==ProxyHostStrategy.AUTO_WINDOWS_HOST?hostResolver.resolve():null;
			putPair(env,"HTTP_PROXY","http_proxy",resolve(s.getHttpProxy(),host));
			putPair(env,"HTTPS_PROXY","https_proxy",resolve(s.getHttpsProxy(),host));
			putPair(env,"ALL_PROXY","all_proxy",resolve(s.getSocks5Proxy(),host));
		}
		env.put("NO_PROXY",noProxy);env.put("no_proxy",noProxy);return env;
	}
	public void applyTo(Map<String,String> target){
		ProxySettings s=current();
		if(s.getMode()==ProxyMode.DIRECT||s.getMode()==ProxyMode.CUSTOM) PROXY_KEYS.forEach(target::remove);
		Map<String,String> generated=environment();
		if(s.getMode()==ProxyMode.SYSTEM){
			String merged=noProxy(join(target.get("NO_PROXY"),target.get("no_proxy"),s.getNoProxy()));
			target.put("NO_PROXY",merged);target.put("no_proxy",merged);
		}else target.putAll(generated);
	}
	public boolean bypass(String host){if(host==null)return false;return REQUIRED_BYPASS.stream().anyMatch(v->v.equalsIgnoreCase(host));}
	private void putPair(Map<String,String> env,String upper,String lower,String value){if(value!=null&&!value.isBlank()){env.put(upper,value);env.put(lower,value);}}
	private String resolve(String raw,String host){if(raw==null||raw.isBlank())return null;if(host==null)return raw;try{URI u=URI.create(raw);return new URI(u.getScheme(),u.getUserInfo(),host,u.getPort(),u.getPath(),u.getQuery(),u.getFragment()).toString();}catch(IllegalArgumentException|URISyntaxException e){throw new IllegalArgumentException("INVALID_PROXY_URL");}}
	private void validate(ProxySettings s){if(s==null||s.getMode()==null||s.getHostStrategy()==null)throw new IllegalArgumentException("INVALID_PROXY_SETTINGS");for(String v:List.of(nvl(s.getHttpProxy()),nvl(s.getHttpsProxy()),nvl(s.getSocks5Proxy())))if(!v.isBlank()){URI u;try{u=URI.create(v);}catch(Exception e){throw new IllegalArgumentException("INVALID_PROXY_URL");}if(u.getScheme()==null||u.getHost()==null||u.getPort()<1)throw new IllegalArgumentException("INVALID_PROXY_URL");}}
	private String noProxy(String custom){LinkedHashSet<String> values=new LinkedHashSet<>(REQUIRED_BYPASS);if(custom!=null)Arrays.stream(custom.split(",")).map(String::trim).filter(v->!v.isBlank()).forEach(values::add);return String.join(",",values);}
	private String join(String... values){return String.join(",",Arrays.stream(values).filter(Objects::nonNull).filter(v->!v.isBlank()).toList());}
	private String nvl(String v){return v==null?"":v;}
}
