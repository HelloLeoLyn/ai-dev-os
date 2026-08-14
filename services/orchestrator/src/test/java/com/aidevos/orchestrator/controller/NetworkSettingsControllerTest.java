package com.aidevos.orchestrator.controller;
import java.util.List;
import com.aidevos.orchestrator.network.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class NetworkSettingsControllerTest {
	@Test void returnsSanitizedSettingsAndProbeResults(){NetworkSettingsService settings=mock(NetworkSettingsService.class);NetworkProbeService probes=mock(NetworkProbeService.class);ProxySettingsView view=new ProxySettingsView(ProxyMode.CUSTOM,ProxyHostStrategy.MANUAL,"http://********@proxy:1",null,null,"localhost,127.0.0.1,::1",2,null,null,null);when(settings.get()).thenReturn(view);when(probes.probe()).thenReturn(List.of(new NetworkProbeResult("GitHub","https://github.com",NetworkRoute.PROXY,true,12,null)));NetworkSettingsController controller=new NetworkSettingsController(settings,probes);assertFalse(controller.get().httpProxy().contains("secret"));assertEquals(NetworkRoute.PROXY,controller.probes().getFirst().route());}
}
