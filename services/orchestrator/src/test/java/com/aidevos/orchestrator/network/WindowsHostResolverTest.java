package com.aidevos.orchestrator.network;
import com.aidevos.orchestrator.executor.command.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
class WindowsHostResolverTest {
	@Test void resolvesCurrentDefaultRouteEveryTime(){CommandExecutor commands=mock(CommandExecutor.class);CommandResult one=result("default via 172.21.64.1 dev eth0\n",true),two=result("default via 10.0.0.1 dev eth0\n",true);when(commands.execute(any(CommandOptions.class))).thenReturn(one,two);WindowsHostResolver resolver=new WindowsHostResolver(commands);assertEquals("172.21.64.1",resolver.resolve());assertEquals("10.0.0.1",resolver.resolve());}
	@Test void returnsExplicitFailureWithoutCachedHost(){CommandExecutor commands=mock(CommandExecutor.class);when(commands.execute(any(CommandOptions.class))).thenReturn(result("",false));assertEquals(ProxyResolutionException.CODE,assertThrows(ProxyResolutionException.class,()->new WindowsHostResolver(commands).resolve()).getMessage());}
	@Test void neverFallsBackToSecondaryDefaultRoute(){CommandExecutor commands=mock(CommandExecutor.class);when(commands.execute(any(CommandOptions.class))).thenReturn(result("default dev eth0 metric 1\ndefault via 192.168.1.1 dev eth1 metric 291\n",true));assertEquals(ProxyResolutionException.CODE,assertThrows(ProxyResolutionException.class,()->new WindowsHostResolver(commands).resolve()).getMessage());}
	private CommandResult result(String output,boolean success){CommandResult r=new CommandResult();r.setOutput(output);r.setSuccess(success);return r;}
}
