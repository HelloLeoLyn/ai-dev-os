package com.aidevos.orchestrator.network;

import java.util.HashMap;
import java.util.Map;
import com.aidevos.orchestrator.executor.command.*;
import com.aidevos.orchestrator.executor.command.approval.ApprovalGate;
import com.aidevos.orchestrator.executor.command.policy.PolicyDecision;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProxyEnvironmentServiceTest {
	private final InMemoryNetworkSettingsRepository repository=new InMemoryNetworkSettingsRepository();
	private final WindowsHostResolver resolver=mock(WindowsHostResolver.class);
	private final ProxyEnvironmentService service=new ProxyEnvironmentService(repository,resolver);

	@Test void directClearsEveryInheritedProxyAndKeepsSafeNoProxy(){ProxySettings s=settings(ProxyMode.DIRECT,ProxyHostStrategy.MANUAL);service.save(s);Map<String,String> env=new HashMap<>();for(String key:ProxyEnvironmentService.PROXY_KEYS)env.put(key,"http://parent:99");service.applyTo(env);for(String key:ProxyEnvironmentService.PROXY_KEYS)assertFalse(env.containsKey(key));assertEquals("localhost,127.0.0.1,::1",env.get("NO_PROXY"));assertEquals(env.get("NO_PROXY"),env.get("no_proxy"));}
	@Test void autoReplacesOnlyHostAndGeneratesUpperAndLowerCase(){when(resolver.resolve()).thenReturn("10.20.30.1");ProxySettings s=settings(ProxyMode.CUSTOM,ProxyHostStrategy.AUTO_WINDOWS_HOST);s.setHttpProxy("http://user:pass@old-host:10808/path");s.setHttpsProxy("http://old-host:10809");s.setSocks5Proxy("socks5://old-host:10810");service.save(s);Map<String,String> env=service.environment();assertEquals("http://user:pass@10.20.30.1:10808/path",env.get("HTTP_PROXY"));assertEquals(env.get("HTTP_PROXY"),env.get("http_proxy"));assertEquals("http://10.20.30.1:10809",env.get("HTTPS_PROXY"));assertEquals("socks5://10.20.30.1:10810",env.get("ALL_PROXY"));}
	@Test void autoFailureNeverFallsBackToOldHost(){when(resolver.resolve()).thenThrow(new ProxyResolutionException());ProxySettings s=settings(ProxyMode.CUSTOM,ProxyHostStrategy.AUTO_WINDOWS_HOST);s.setHttpProxy("http://old-host:10808");service.save(s);ProxyResolutionException error=assertThrows(ProxyResolutionException.class,service::environment);assertEquals(ProxyResolutionException.CODE,error.getMessage());}
	@Test void rejectsInvalidProxy(){ProxySettings s=settings(ProxyMode.CUSTOM,ProxyHostStrategy.MANUAL);s.setHttpProxy("not-a-proxy");assertEquals("INVALID_PROXY_URL",assertThrows(IllegalArgumentException.class,()->service.save(s)).getMessage());}
	@Test void commandExecutorReceivesNewSettingsAndExplicitEnvironmentWins(){ProxySettings custom=settings(ProxyMode.CUSTOM,ProxyHostStrategy.MANUAL);custom.setHttpProxy("http://proxy.test:18080");service.save(custom);CommandExecutor executor=new CommandExecutor(o->PolicyDecision.allow("test"),new ApprovalGate(),service);CommandOptions options=new CommandOptions();options.setCommand(java.util.List.of("sh","-c","printf '%s|%s' \"$HTTP_PROXY\" \"$http_proxy\""));CommandResult first=executor.execute(options);assertEquals("http://proxy.test:18080|http://proxy.test:18080",first.getOutput());ProxySettings direct=settings(ProxyMode.DIRECT,ProxyHostStrategy.MANUAL);service.save(direct);CommandResult second=executor.execute(options);assertEquals("|",second.getOutput());options.setEnvironment(Map.of("HTTP_PROXY","http://explicit:1"));assertEquals("http://explicit:1|",executor.execute(options).getOutput());}
	private ProxySettings settings(ProxyMode mode,ProxyHostStrategy strategy){ProxySettings s=new ProxySettings();s.setMode(mode);s.setHostStrategy(strategy);return s;}
}
